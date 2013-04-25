(ns lanina.views.ticket
  (:use noir.core
        hiccup.form
        lanina.views.common
        [hiccup.element :only [link-to]]
        [lanina.utils :only [coerce-to]] )
  (:require [lanina.models.ticket  :as ticket]
            [lanina.models.article :as article]
            [lanina.models.adjustments :as settings]
            [lanina.views.utils    :as utils]
            [clj-time.core         :as time]
            [noir.response         :as resp]
            [noir.session          :as session]))

(defpartial ticket-row [prod]
  [:tr
   [:td (:nom_art prod)]
   [:td (:quantity prod)]
   [:td (:precio_unitario prod)]
   [:td (:total prod)]])

(defpartial ticket-table [prods]
  (form-to [:post "/tickets/nuevo/"]
    [:table.table.table-condensed
     [:tr
      [:th "Nombre de artículo"]
      [:th "Cantidad"]
      [:th "Precio unitario"]
      [:th "Total"]]
     (map ticket-row prods)]
    [:div.form-actions
     (submit-button {:class "btn btn-primary"} "Imprimir ticket")]))

(defpartial pay-notice [pay total change]
  [:div.container-fluid
   [:div.alert.alert-error
    [:h1 "Cambio: "
     (format "%.2f" (double change))]]
   [:div.alert.alert-info
    [:h2 "Total: "
     (format "%.2f" (double total))]]
   [:div.alert.alert-info
    [:h2 "Pagó: "
     (format "%.2f" (double pay))]]])

(defn get-article [denom]
  (letfn [(is-gvdo [d] (= "gvdo" (clojure.string/lower-case (apply str (take 4 (name d))))))
          (is-exto [d] (= "exto" (clojure.string/lower-case (apply str (take 4 (name d))))))]
    (cond (is-gvdo denom) {:_id denom :codigo "0" :nom_art "ARTÍCULO GRAVADO"
                           :iva 16.0 :precio_venta ((coerce-to Double 0.0) (clojure.string/replace (clojure.string/replace denom #"gvdo\d+_" "")
                                                                                 #"_" "."))}
          (is-exto denom) {:_id denom :codigo "0" :nom_art "ARTÍCULO EXENTO"
                           :iva 0 :precio_venta ((coerce-to Double 0.0) (clojure.string/replace (clojure.string/replace denom #"exto\d+_" "")
                                                                                 #"_" "."))}
          :else (article/get-by-id denom))))

(defpartial printed-ticket [prods pay total change ticket-number folio]
  (let [now (time/now)
        date (str (time/day now) "/" (time/month now) "/" (time/year now))
        t (str (format "%02d" (time/hour now)) ":"
               (format "%02d" (time/minute now)) ":" (format "%02d" (time/sec now)))]
    [:pre.prettyprint.linenums {:style "max-width:250px;"}
     [:ol.linenums {:style "list-style-type:none;"}
      [:p
       [:li {:style "text-align:center;"} "\"L A N I Ñ A\""]
       [:li {:style "text-align:center;"} "R.F.C: ROHE510827-8T7"]
       [:li {:style "text-align:center;"} "GUERRERO No. 45 METEPEC MEX."]
       [:li {:style "text-align:center;"} (str date " " t " TICKET:" ticket-number)]]
      (map (fn [art] [:p
                      [:li (:nom_art art)]
                      [:li {:style "text-align:right;"}
                       (str (:quantity art) " x "
                            (format "%.2f" (double (:precio_venta art))) " = "
                            (format "%.2f" (double (:total art))))]])
           prods)
      [:br]
      [:p
       [:li {:style "text-align:right;"}
        (format "SUMA ==> $ %8.2f" (double total))]
       [:li {:style "text-align:right;"}
        (format "CAMBIO ==> $ %8.2f" (double change))]
       [:li {:style "text-align:right;"}
        (format "EFECTIVO ==> $ %8.2f" (double pay))]
       [:li (str "Folio: " folio)]]]]))

(defn sanitize-ticket [items]
  {:pay (try (Double/valueOf (:pay items))
            (catch Exception e
              nil))
   :ticketn (try (Integer/valueOf (:ticketn items))
                (catch Exception e
                  nil))
   :pairs (reduce (fn [acc [id quant]]
                    (try (Integer/valueOf quant)
                         (conj acc [id (Integer/valueOf quant)])
                         (catch Exception e
                           acc)))
                  []
                  (dissoc items :pay :ticketn))})

(defpage "/tickets/nuevo/" {:as items}
  (let [{pay :pay ticketn :ticketn pairs :pairs} (sanitize-ticket items)
        prods (reduce (fn [acc [bc times]]
                        (let [article (get-article (name bc))
                              name (:nom_art article)
                              type (if (settings/valid-iva? (:iva article))
                                     (if (< 0 (:iva article))
                                       "gvdo"
                                       "exto")
                                     "exto")
                              price (:precio_venta article)
                              total (if (number? price) (* price times) 0.0)
                              art {:type type :iva (:iva article) :quantity times :_id bc :codigo (:codigo article) :nom_art name :precio_venta price :total total}]
                          (conj acc art)))
                      [] pairs)
        total (reduce + (map :total prods))
        change (if pay (- pay total) 0)
        next-ticket-number (ticket/get-next-ticket-number)
        folio (ticket/get-next-folio)
        content {:title "Ticket impreso"
                 :content [:div.container-fluid
                           (pay-notice pay total change)
                           [:hr]
                           (when (and ticketn (< ticketn (ticket/get-next-ticket-number)))
                             (utils/message "Este número de ticket ya ha sido impreso y no se volverá a imprimir. Para visitar un ticket previo e imprimirlo acuda a la sección de tickets." "error"))
                           (printed-ticket prods pay total change ticketn folio)]
                 :active "Ventas"}]
    (when (and ticketn (>= ticketn (ticket/get-next-ticket-number)))
      (ticket/insert-ticket pay prods))
    (home-layout content)))

(defpartial search-ticket-form []
  (let [date (utils/now)]
    (form-to {:class "form-horizontal"} [:get "/tickets/buscar/"]
      [:legend "Buscar un ticket"]
      [:fieldset
       [:div.control-group
        (label {:class "control-label"} "date" "Buscar por fecha")
        [:div.controls
         [:input {:type "date" :name "date" :value date}]]]
       [:div.control-group
        (label {:class "control-label"} "folio" "Buscar por número de folio")
        [:div.controls
         (text-field "folio")]]]
      [:div.form-actions
       (submit-button {:class "btn btn-primary"} "Buscar")])))

(defpartial cut-form []
  (let [date (utils/now)]
    (form-to {:class "form-horizontal"} [:get "/tickets/corte/"]
      [:legend "Cortes de caja"]
      [:fieldset
       [:div.control-group
        (label {:class "control-label"} "fecha" "Indicar fecha de corte")
        [:div.controls
         [:input {:type "date" :name "fecha" :value date}]]]
       [:div.control-group
        (label {:class "control-label"} "desde" "Opcional: Indique el número del primer ticket")
        [:div.controls
         (text-field "desde")]]
       [:div.control-group
        (label {:class "control-label"} "hasta" "Opcional: Indique el número del último ticket")
        [:div.controls
         (text-field "hasta")]]]
      [:div.form-actions
       (submit-button {:class "btn btn-primary"} "Cortar")])))

(defpage "/tickets/" []
  (let [content {:title "Tickets y cortes de caja"
                 :content [:div.container-fluid
                           (search-ticket-form)
                           (cut-form)]
                 :nav-bar true
                 :active "Tickets"}]
    (home-layout content)))

(defpartial ticket-results-row [ticket]
  (let [prods (:articles ticket)
        total (reduce + (map :total prods))
        pay   (:pay ticket)
        date  (:date ticket)
        folio (:folio ticket)
        ticket-number (:ticket-number ticket)]
    [:tr
     [:td (link-to (str "/tickets/folio/" folio "/") folio)]
     [:td ticket-number]
     [:td date]
     [:td (format "%.2f" total)]
     [:td (format "%.2f" pay)]
     [:td (format "%.2f" (- pay total))]
     [:td (link-to {:class "btn btn-success"} (str "/tickets/folio/" folio "/") "Consultar")]
     [:td (link-to {:class "btn btn-primary"} (str "/tickets/folio/" folio "/imprimir/") "Imprimir")]]))

(defpartial ticket-results-table [tickets]
  [:table.table.table-condensed
   [:tr
    [:th "Folio"]
    [:th "Ticket"]
    [:th "Fecha"]
    [:th "Total"]
    [:th "Pagado"]
    [:th "Cambio"]
    [:th {:colspan 2} "Controles"]]
   (map ticket-results-row tickets)])

(defpage "/tickets/buscar/" {:keys [date folio]}
  (let [results (if (seq date)
                  (ticket/search-by-date date)
                  (ticket/search-by-folio (try (Long. folio)
                                               (catch Exception e 0))))
        disp (if (seq results)
               (ticket-results-table results)
               [:p {:class "alert alert-error"} "No se encontraron resultados"])
        content {:title "Resultados de la búsqueda"
                 :content [:div.container-fluid disp]
                 :nav-bar true
                 :active "Tickets"}]
    (home-layout content)))

(defpage "/tickets/init" []
  (ticket/setup!)
  (str "Done!"))

(defpartial cashier-cut [tickets]
  (let [all-arts (map :articles tickets)
        gvdos-extos (map (fn [prods]
                      (map #(reduce + 0.0 (map :total %)) ((juxt filter remove) #(= "gvdo" (:type %)) prods)))
                    all-arts)
        gvdo (reduce + (map first gvdos-extos))
        exto (reduce + (map second gvdos-extos))
        iva (if (> gvdo 0.0)
              (- gvdo (/ gvdo 1.16))
              0.0)
        total (+ gvdo exto)
        number (count tickets)]
    [:div.container-fluid
     [:div.alert
      [:h2 "Tickets: "
       (str number)]]
     [:div.alert.alert-info
      [:h2 "Exentos: "
       (format "%.2f" (double exto))]]
     [:div.alert
      [:h2 "Gravados: "
       (format "%.2f" (double gvdo))]]
     [:div.alert.alert-info
      [:h2 "Iva: "
       (format "%.2f" (double iva))]]
     [:div.alert.alert-error
      [:h2 "Total: "
       (format "%.2f" (double total))]]]))

(defpartial cut-as-printed [tickets]
  (let [all-arts (map :articles tickets)
        gvdos-extos (map (fn [prods]
                      (map #(reduce + 0.0 (map :total %)) ((juxt filter remove) #(= "gvdo" (:type %)) prods)))
                    all-arts)
        gvdo (reduce + (map first gvdos-extos))
        exto (reduce + (map second gvdos-extos))
        iva (if (> gvdo 0.0)
              (- gvdo (/ gvdo 1.16))
              0.0)
        total (+ gvdo exto)
        number (count tickets)]
    [:pre.prettyprint.linenums {:style "max-width:235px;"}
     [:ol.linenums {:style "list-style-type:none;"}
      [:p
       [:li {:style "text-align:center;"} "\"L A N I Ñ A\""]
       [:li {:style "text-align:center;"} "CORTE DE CAJA"]
       [:li {:style "text-align:center;"} (str "FECHA: " (:date (first tickets)))]
       [:li {:style "text-align:center;"} "-----------------"]
       [:li (format "TICKETS ==> %10d" (Integer. number))]
       [:li (format "EXENTOS ==> %10.2f" (double exto))]
       [:li (format "GRAVADOS ==> %9.2f" (double gvdo))]
       [:li (format "IVA ==> %14.2f" (double iva))]
       [:li (format "TOTAL ==> %12.2f" (double total))]]]]))

(defpage "/tickets/corte/" {:keys [fecha desde hasta]}
  (let [date fecha
        tickets (cond (and (seq desde) (seq hasta))
                      (ticket/search-by-date-with-limits date desde hasta)
                      (seq hasta)
                      (ticket/search-by-date-with-limits date 0 hasta)
                      (seq desde)
                      (ticket/search-by-date-with-limits date desde)
                      :else (ticket/search-by-date date))
        cut (when (seq tickets) (cashier-cut tickets))
        content {:title (str "Corte de caja de la fecha " (utils/fix-date date))
                 :content (if (seq tickets)
                            [:div.container-fluid
                             cut
                             [:hr]
                             (cut-as-printed tickets)
                             [:div.form-actions
                              (link-to {:class "btn btn-primary"}
                                       (str "/tickets/corte/"
                                            (clojure.string/replace date #"/" "-") "/imprimir/")
                                       "Imprimir corte")]]
                            [:div.container-fluid
                             [:p {:class "alert alert-error"} "Este día no tiene ventas o no hay tickets para mostrar."]])
                 :nav-bar true
                 :active "Tickets"}]
    (if (or (seq tickets) (seq fecha))
      (home-layout content)
      (do (session/flash-put! :messages '({:type "alert-error" :text "Necesita indicar una fecha de corte"}))
          (resp/redirect "/tickets/")))))

(defpartial show-ticket [ticket]
  (let [folio (:folio ticket)
        pay   (:pay ticket)
        prods (:articles ticket)
        total (reduce + (map :total prods))
        change (- pay total)
        number (:ticket-number ticket)]
    [:div.container-fluid
     [:div.form-actions
      (link-to {:class "btn btn-primary"} (str "/tickets/folio/" folio "/imprimir/") "Imprimir ticket")]
     [:hr]
     (printed-ticket prods pay total change number folio)]))

(defpage "/tickets/folio/:folio/" {folio :folio}
  (let [ticket (ticket/get-by-folio (try (Long/valueOf folio)
                                         (catch Exception e 0)))
        content {:title (str "Mostrando ticket con folio " folio)
                 :content [:div.container-fluid
                           (if (seq ticket)
                             (show-ticket ticket)
                             [:p {:class "alert alert-error"} "No existe tal ticket."])]
                 :nav-bar true
                 :active "Tickets"}]
    (home-layout content)))
