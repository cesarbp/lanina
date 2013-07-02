(ns lanina.views.credit
  "Views for sales by credit"
  (:use noir.core
        hiccup.form)
  (:require [lanina.models.credit :as credit]
            [lanina.utils :refer [coerce-to]]
            [hiccup.element :refer [link-to]]
            [lanina.views.common :refer [home-layout main-layout-incl]]
            [lanina.views.utils :refer [format-decimal flash-message]]
            [noir.response :refer [redirect]]
            [hiccup.element :refer [javascript-tag]]))

(def focus-on-first-input-js
  (javascript-tag
   "$('form:first *:input[type!=hidden]:first').focus();"))

(defpartial install-form
  []
  (form-to {:class "form form-horizontal"} [:post "/credito/setup/"]
           [:div.control-group
            (label {:class "control-label"} :r "Número de filas")
            [:div.controls
             (text-field {:autocomplete "off"} :r)]]
           [:div.control-group
            (label {:class "control-label"} :c "Número de columnas")
            [:div.controls
             (text-field {:autocomplete "off"} :c)]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Crear Estante")]))

(defn credit-td
  [{:keys [r c name articles date]}]
  [:ul.unstyled {:style "text-align:center;"}
   [:li (if name
          (link-to (str "/credito/" r "/" c "/") name)
          (link-to (str "/credito/nuevo/" r "/" c "/") "Crear nuevo"))]
   (for [art articles]
     [:li (:name art)])
   [:li (if date (str "Fecha: " date) "")]])

(defpartial credits-table
  []
  (let [[rn cn] (credit/get-rc)
        table (credit/get-table)]
    [:table.table.table-condensed
     [:tr
      [:td]
      (for [j (range cn)]
        [:td {:style "text-align:center"} j])]
     (for [[r i] (map vector table (range))]
       [:tr
        [:td {:style "text-align:center"} i]
        (for [credit r]
          [:td (credit-td credit)])])]))

(defpartial time-unit-select
  []
  [:select {:name "time-unit"}
   [:option {:value "days"} "Días"]
   [:option {:value "months"} "Meses"]])

(defpartial new-credit-form
  [r c n]
  (form-to {:class "form form-horizontal"} [:post (str "/credito/nuevo/" r "/" c "/")]
           [:legend "Nuevo crédito"]
           [:div.control-group
            (label {:class "control-label"} :pname "Nombre de la persona")
            [:div.controls
             (text-field {:autocomplete "off"} :pname)]]
           (for [i (range n) :let [iname (keyword (str "article" i))
                                   pname (keyword (str "price" i))]]
             (list
              [:div.control-group
               (label {:class "control-label"} iname "Nombre del artículo")
               [:div.controls
                (text-field {:autocomplete "off"} iname)]]
              [:div.control-group
               (label {:class "control-label"} pname "Precio del artículo")
               [:div.controls
                (text-field {:autocomplete "off"} pname)]]))
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Crear")
            (link-to {:class "btn btn-success"} "/credito/" "Regresar")]))

(defpartial payments-table
  [payments]
  [:table.table.table-condensed
   [:tr.info
    (for [[d _] payments]
      [:td d])]
   [:tr
    (for [[_ p] payments]
      [:td (format-decimal p)])]])

(defpartial add-payment-form
  [r c articles]
  (form-to {:class "form-inline"} [:post (str "/credito/" r "/" c "/pago/")]
           (text-field {:autocomplete "off" :class "input-small"} :pay)
           [:label "Liberar artículos:"]
           (for [[{:keys [name purchased]} i] (map vector articles (range)) :when (not purchased)]
             [:label.checkbox
              (check-box (keyword (str "to-purchase" i)) false "true")
              name])
           (submit-button {:class "btn btn-primary"} "Agregar Pago")))

(defpartial show-credit
  [{:keys [r c name payments articles date free]}]
  [:ul.unstyled
   [:li "Cliente: " name]
   (for [{:keys [name price purchased]} articles]
     (list
      [:li "Artículo: " name]
      [:li "Precio: " price]
      [:li "Pagado: " (if purchased [:i.icon-ok] [:i.icon-remove])]
      [:hr]))
   [:li "Fecha de Inicio: " date]
   [:li "Pagos"]
   [:li (if (seq payments)
          (payments-table payments)
          [:p.alert.alert-info "No ha realizado ningún pago"])]
   [:li "Restante: " [:strong (format-decimal (credit/calc-remaining payments articles))]]
   [:li (if free
          [:p.notice "Los pagos ya se han completado"]
          (add-payment-form r c articles))]])

(defpartial search-client-form
  []
  (form-to {:class "form form-horizontal"} [:get "/credito/clientes/buscar/"]
           [:legend "Buscar el historial de un cliente"]
           [:div.control-group
            (label {:class "control-label"} :q "Nombre del cliente")
            [:div.controls
             (text-field :q)]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Buscar un cliente")]))

(defpartial show-clients
  [clients]
  [:ul.unstyled
   (for [[n credits] clients]
     (list
      [:li [:h3 "De: " n]]
      (for [[c i] (map vector credits (range))]
        [:li [:div.form-actions
              [:h4 (inc i) "."]
              (show-credit c)]])
      [:hr]))])

(defpage "/credito/clientes/buscar/" {:keys [q]}
  (let [clients (group-by :name (credit/find-client q))
        content {:content
                 [:div.container-fluid
                  (if (seq clients)
                    (show-clients clients)
                    [:p.error-notice "No se han encontrado clientes"])
                  [:div.form-actions
                   (link-to {:class "btn btn-success"} "/credito/" "Regresar")]]
                 :title "Créditos"
                 :active "Créditos"
                 :nav-bar true}]
    (home-layout content)))

(defn parse-to-purchase
  [pst]
  (->> pst
       (keys)
       (map name)
       (filter (partial re-seq #"to-purchase"))
       (map (partial re-seq #"\d+"))
       (map first)
       (map (coerce-to Long))))

(defpage [:post "/credito/:r/:c/pago/"] {:keys [pay r c] :as pst}
  (let [r ((coerce-to Long) r)
        c ((coerce-to Long) c)
        pay ((coerce-to Double) pay)
        to-purchase (parse-to-purchase pst)
        ans (when (and r c pay (< 0 pay)) (credit/add-payment! r c pay to-purchase))]
    (if (= :success (:resp ans))
      (flash-message "Pago agregado" "success")
      (flash-message "El pago no pudo ser agregado" "error"))
    (if (:freed ans)
      (do
        (flash-message (str "La ubicación " r "," c " ha sido liberada") "info")
        (redirect "/credito/"))
      (redirect (str "/credito/" r "/" c "/")))))

(defn parse-articles
  [pst]
  (let [ks (->> pst
                (keys)
                (map name)
                (filter (partial re-seq #"article"))
                (map keyword))
        ns (->> ks
                (map name)
                (map (partial re-seq #"\d+"))
                (map first)
                (map (coerce-to Long)))
        ps (->> ns
                (map (partial str "price"))
                (map keyword))]
    (into {}
          (for [[k p i] (map vector ks ps (range)) :let [n ((coerce-to Double -1.0) (p pst))] :when (<= 0 n)]
               {(k pst) n}))))

(defpage [:post "/credito/nuevo/:r/:c/"] {:keys [r c pname] :as pst}
  (let [r ((coerce-to Long) r)
        c ((coerce-to Long) c)
        articles (parse-articles pst)]
    (if (and r c (seq pname) (seq articles))
      (when (credit/create-credit! r c pname articles)
        (flash-message "El crédito ha sido creado" "success"))
      (flash-message "El crédito no pudo ser creado" "error"))
    (redirect "/credito/")))

(defpage [:post "/credito/setup/"] {:keys [r c]}
  (let [r ((coerce-to Long) r)
        c ((coerce-to Long) c)]
    (if (and r c)
      (do
        (credit/setup! r c)
        (flash-message "Estante creado" "success"))
      (flash-message "Estante no creado, verifique sus entradas" "error"))
    (redirect "/credito/")))

(defpartial ask-number-articles-form
  [r c]
  (form-to {:class "form form-horizontal"} [:get (str "/credito/nuevo/" r "/" c "/")]
           [:div.control-group
            (label {:class "control-label"} :n "Número de artículos")
            [:div.controls
             (text-field :n)]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Continuar")]))

(defpage "/credito/nuevo/:r/:c/" {:keys [r c n]}
  (let [r ((coerce-to Long) r)
        c ((coerce-to Long) c)
        n ((coerce-to Long) n)
        available? (when (and r c) (credit/available? r c))
        content {:content [:div.container-fluid
                           (cond
                            (and r c available? (not n)) (ask-number-articles-form r c)
                            (and r c n available?) (new-credit-form r c n)
                            :else [:div.alert.alert-error
                                   [:p "Esta ubicación no está disponible"]
                                   (link-to {:class "btn btn-success"} "/credito/" "Regresar")])
                           focus-on-first-input-js]
                 :title "Nuevo Crédito"
                 :active "Créditos"
                 :nav-bar true}]
    (main-layout-incl content [:base-css :jquery])))

(defpage "/credito/" []
  (let [installed? (credit/installed?)
        c (if installed?
            (list
             [:h2 "Estante"]
             [:hr]
             (credits-table))
            (list
             [:h2 "Instalación"]
             (install-form)))
        content {:content [:div.container-fluid
                           (if installed?
                             (list (search-client-form)
                                   [:hr]))
                           c]
                 :title "Créditos"
                 :nav-bar true
                 :active "Créditos"}]
    (home-layout content)))

(defpage "/credito/:r/:c/" {:keys [r c]}
  (let [r ((coerce-to Long) r)
        c ((coerce-to Long) c)
        credit (when (and r c) (credit/get-credit r c))
        content {:content [:div.container-fluid
                           (if credit
                             (if (credit/available? r c)
                               [:div.form-actions
                                [:div.alert.alert-notice
                                 [:p "Esta ubicación está disponible"]
                                 (link-to {:class "btn btn-primary"}
                                          (str "/credito/nuevo/" r "/" c "/") "Agregar artículo")]]
                               (list
                                (show-credit credit)
                                [:div.form-actions
                                 (link-to {:class "btn btn-primary"} "/credito/" "Regresar")]))
                             [:div.alert.alert-error
                              [:p "Esta ubicación no existe"]
                              (link-to {:class "btn btn-primary"} "/credito/" "Regresar")])]
                 :nav-bar true
                 :title "Créditos"
                 :active "Créditos"}]
    (home-layout content)))

(defpage "/credito/restart/" []
  (credit/restart!)
  (redirect "/credito/"))
