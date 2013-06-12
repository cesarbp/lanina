(ns lanina.views.credit
  "Views for sales by credit"
  (:use noir.core
        hiccup.form)
  (:require [lanina.models.credit :as credit]
            [lanina.utils :refer [coerce-to]]
            [hiccup.element :refer [link-to]]
            [lanina.views.common :refer [home-layout]]
            [lanina.views.utils :refer [format-decimal flash-message]]
            [noir.response :refer [redirect]]))

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
  [{:keys [r c name article date]}]
  [:ul.unstyled {:style "text-align:center;"}
   [:li (if name
          (link-to (str "/credito/" r "/" c "/") name)
          (link-to (str "/credito/nuevo/" r "/" c "/") "Crear nuevo"))]
   [:li (if article article "")]
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
  [r c]
  (form-to {:class "form form-horizontal"} [:post (str "/credito/nuevo/" r "/" c "/")]
           [:legend "Nuevo crédito"]
           [:div.control-group
            (label {:class "control-label"} :n "Nombre de la persona")
            [:div.controls
             (text-field {:autocomplete "off"} :n)]]
           [:div.control-group
            (label {:class "control-label"} :article "Nombre del artículo")
            [:div.controls
             (text-field {:autocomplete "off"} :article)]]
           [:div.control-group
            (label {:class "control-label"} :price "Precio del artículo")
            [:div.controls
             (text-field {:autocomplete "off"} :price)]]
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
  [r c]
  (form-to {:class "form-inline"} [:post (str "/credito/" r "/" c "/pago/")]
           (text-field {:autocomplete "off" :class "input-small"} :pay)
           (submit-button {:class "btn btn-primary"} "Agregar Pago")))

(defpartial show-credit
  [{:keys [r c name payments article price date free]}]
  [:ul.unstyled
   [:li "Cliente: " name]
   [:li "Artículo: " article]
   [:li "Precio: " price]
   [:li "Fecha de Inicio: " date]
   [:li "Pagos"]
   [:li (if (seq payments)
          (payments-table payments)
          [:p.alert.alert-info "No ha realizado ningún pago"])]
   [:li "Restante: " [:strong (format-decimal (credit/calc-remaining payments price))]]
   [:li (if free
          [:p.notice "Los pagos ya se han completado"]
          (add-payment-form r c))]])

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

(defpage [:post "/credito/:r/:c/pago/"] {:keys [pay r c]}
  (let [r ((coerce-to Long) r)
        c ((coerce-to Long) c)
        pay ((coerce-to Double) pay)
        ans (when (and r c pay (< 0 pay)) (credit/add-payment! r c pay))]
    (if ans
      (flash-message "Pago agregado" "success")
      (flash-message "El pago no pudo ser agregado" "error"))
    (if (:freed ans)
      (do
        (flash-message (str "La ubicación " r "," c " ha sido liberada") "info")
        (redirect "/credito/"))
      (redirect (str "/credito/" r "/" c "/")))))

(defpage [:post "/credito/nuevo/:r/:c/"] {:keys [r c n article price]}
  (let [r ((coerce-to Long) r)
        c ((coerce-to Long) c)
        price ((coerce-to Double) price)]
    (if (and r c price (seq n) (seq article)
               (< 0 price))
      (when (credit/create-credit! r c n article price)
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

(defpage "/credito/nuevo/:r/:c/" {:keys [r c]}
  (let [r ((coerce-to Long) r)
        c ((coerce-to Long) c)
        available? (when (and r c) (credit/available? r c))
        content {:content [:div.container-fluid
                           (if available?
                             (new-credit-form r c)
                             [:div.alert.alert-error
                              [:p "Esta ubicación no está disponible"]
                              (link-to {:class "btn btn-success"} "/credito/" "Regresar")])]
                 :title "Nuevo Crédito"
                 :active "Créditos"
                 :nav-bar true}]
    (home-layout content)))

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
                           c
                           [:hr]
                           (search-client-form)]
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
