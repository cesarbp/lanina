(ns lanina.views.ticket
  (:use noir.core
        hiccup.form
        lanina.views.common
        [hiccup.element :only [link-to javascript-tag]]
        [lanina.utils :only [coerce-to]]
        [lanina.views.utils :only [now now-hour fix-date]])
  (:require [lanina.models.ticket  :as ticket]
            [lanina.models.article :as article]
            [lanina.models.adjustments :as settings]
            [lanina.views.utils    :as utils]
            [clj-time.core         :as time]
            [noir.response         :as resp]
            [noir.session          :as session]
            [lanina.models.user    :as users]
            [lanina.models.printing :as printing]
            [lanina.models.cashier :refer [cashier-is-open? add-money!]]))

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
    [:h1#change "Cambio: "
     (utils/format-decimal change)]]
   [:div.alert.alert-info
    [:h2 "Total: "
     (utils/format-decimal total)]]
   [:div.alert.alert-info
    [:h2 "Pagó: "
     (utils/format-decimal pay)]]])

(defn parse-unregistered [s]
  (let [s (name s)
        type (->> s (take 4) (apply str) clojure.string/lower-case)
        s (clojure.string/replace s (re-pattern (str type #"\d+")) "")
        nom_art (if (= \- (first s))
                  (clojure.string/replace (->> s (drop 1) (take-while #(not= \_ %)) (apply str))
                                          #"\-" " ")
                  (if (= type "exto") "ARTÍCULO EXENTO" "ARTÍCULO GRAVADO"))
        s (->> s (drop-while #(not= \_ %)) rest (apply str))
        price ((coerce-to Double 0.0) (clojure.string/replace s #"_" "."))]
    {:_id s :codigo "0" :nom_art nom_art :iva (if (= "exto" type) 0.0 16.0) :precio_venta price}))

(defn get-article [denom]
  (letfn [(is-unregistered [d] (let [type (->> d name (take 4) (apply str) clojure.string/lower-case)]
                                 (or (= "gvdo" type)
                                     (= "exto" type))))]
    (if (is-unregistered denom)
      (parse-unregistered denom)
      (article/get-by-id denom))))

(defpartial printed-ticket [prods pay total change ticket-number folio date time]
  [:pre.prettyprint.linenums {:style "max-width:250px;"}
   [:ol.linenums {:style "list-style-type:none;"}
    [:p
     [:li {:style "text-align:center;"} "\"L A N I Ñ A\""]
     [:li {:style "text-align:center;"} "R.F.C: ROHE510827-8T7"]
     [:li {:style "text-align:center;"} "GUERRERO No. 45 METEPEC MEX."]
     [:li {:style "text-align:center;"} (str date " " time " TICKET:" ticket-number)]]
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
     [:li (str "Folio: " folio)]]]])

(defn sanitize-ticket [items]
  {:pay (try (Double/valueOf (:pay items))
            (catch Exception e
              nil))
   :pairs (reduce (fn [acc [id quant]]
                    (try (Integer/valueOf quant)
                         (conj acc [id (Integer/valueOf quant)])
                         (catch Exception e
                           acc)))
                  []
                  (dissoc items :pay :ticketn))})

(defn fetch-prods
  [pairs]
  (reduce (fn [acc [bc times]]
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
          [] pairs))

(def new-ticket-js
  (javascript-tag
   "function blink(id) {
    var n = 0;
    var colors = [\"white\", \"black\"];
    var changeColor = function() {
        $(id).css('color', colors[n]);
        n = n + 1;
        n = n % 2;
    };
    return setInterval(changeColor, 250);
}

$(document).ready(function() {
    blink(\"#change\");
    $('body').keyup(function(e) {
    var code = (e.keyCode ? e.keyCode : e.which);
    if ( code == 13 )
      window.location = \"/ventas/\"
    });
  });"))

(defpartial enter-notice
  []
  [:div.alert.alert-info
   [:h2#enter-notice "Presione enter para continuar"]])

(defpage "/tickets/nuevo/" {:as items}
  (let [ticketn (ticket/get-next-ticket-number)
        prov-ticketn ((coerce-to Long) (:ticketn items))]
    (when (= ticketn prov-ticketn)
      (let [{pay :pay pairs :pairs} (sanitize-ticket items)

            prods (fetch-prods pairs)
            total (reduce + (map :total prods))
            change (if pay (- pay total) 0)
            next-ticket-number (ticket/get-next-ticket-number)
            folio (ticket/get-next-folio)
            date (utils/now)
            insertion (delay
                       (ticket/insert-ticket ticketn pay prods date))]
        (when (= :success @insertion)
          (printing/print-ticket prods pay total change ticketn folio date)
          (when (cashier-is-open?)
            (add-money! total)))
        (let [content {:content [:div.container-fluid
                                 (pay-notice pay total change)
                                 (enter-notice)
                                 new-ticket-js]
                       :nav-bar true
                       :title "Ticket pagado"}]
          (main-layout-incl content [:base-css :jquery]))))))

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
                           (when (users/admin?) (cut-form))]
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

(defn get-cut
  [tickets]
  (let [all-arts (map :articles tickets)
        gvdos-extos (map (fn [prods]
                           (map #(reduce + 0.0 (map :total %))
                                ((juxt filter remove) #(= "gvdo" (:type %)) prods)))
                         all-arts)
        date (now)
        time (now-hour)
        gvdo (reduce + (map first gvdos-extos))
        exto (reduce + (map second gvdos-extos))
        iva (if (> gvdo 0.0)
               (- gvdo (/ gvdo 1.16))
               0.0)
        total (+ gvdo exto)
        number (count tickets)]
    {:date date
     :time time
     :gvdo gvdo
     :exto exto
     :iva iva
     :total total
     :number number}))

(defpartial cashier-cut [{:keys [number total iva exto gvdo]}]
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
     (format "%.2f" (double total))]]])

(defpartial cut-as-printed [{:keys [number total iva exto gvdo date time]}]
  [:pre.prettyprint.linenums {:style "max-width:235px;"}
   [:ol.linenums {:style "list-style-type:none;"}
    [:p
     [:li {:style "text-align:center;"} "\"L A N I Ñ A\""]
     [:li {:style "text-align:center;"} "CORTE DE CAJA"]
     [:li {:style "text-align:center;"} (str "FECHA: " date)]
     [:li {:style "text-align:center;"} "-----------------"]
     [:li (format "TICKETS ==> %10d" number)]
     [:li (format "EXENTOS ==> %10.2f" (double exto))]
     [:li (format "GRAVADOS ==> %9.2f" (double gvdo))]
     [:li (format "IVA ==> %14.2f" (double iva))]
     [:li (format "TOTAL ==> %12.2f" (double total))]]]])

(defpage "/tickets/corte/" {:keys [fecha desde hasta]}
  (let [fecha (when (seq fecha) (fix-date fecha))
        tickets (cond (and (seq desde) (seq hasta))
                      (ticket/search-by-date-with-limits fecha desde hasta)
                      (seq hasta)
                      (ticket/search-by-date-with-limits fecha 0 hasta)
                      (seq desde)
                      (ticket/search-by-date-with-limits fecha desde)
                      :else (ticket/search-by-date fecha))
        cut-map (get-cut tickets)
        {:keys [tickets-n exto gvdo iva total date time]} cut-map
        cut (when (seq tickets) (cashier-cut cut-map))
        content {:title (str "Corte de caja de la fecha " fecha)
                 :content (if (seq tickets)
                            [:div.container-fluid
                             cut
                             [:hr]
                             (cut-as-printed cut-map)]
                            [:div.container-fluid
                             [:p {:class "alert alert-error"} "Este día no tiene ventas o no hay tickets para mostrar."]])
                 :nav-bar true
                 :active "Tickets"}]
    (if (or (seq tickets) (seq fecha))
      (do
        (printing/print-cashier-cut tickets-n exto gvdo iva total date time)
        (home-layout content))
      (do (session/flash-put! :messages '({:type "alert-error" :text "Necesita indicar una fecha de corte"}))
          (resp/redirect "/tickets/")))))

(defpartial show-ticket [ticket]
  (let [folio (:folio ticket)
        pay   (:pay ticket)
        prods (:articles ticket)
        total (reduce + (map :total prods))
        change (- pay total)
        number (:ticket-number ticket)
        date (:date ticket)
        time (:time ticket)]
    [:div.container-fluid
     [:div.form-actions
      (link-to {:class "btn btn-primary"} (str "/tickets/folio/" folio "/imprimir/") "Imprimir ticket")]
     [:hr]
     (printed-ticket prods pay total change number folio date time)]))

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

(defpage "/tickets/folio/:folio/imprimir" {folio :folio}
  (let [ticket (ticket/get-by-folio ((coerce-to Long 0) folio))
        prods (:articles ticket)
        total (reduce + (map :total prods))
        pay (:pay ticket)
        change (- pay total)
        {ticket-number :ticket-number folio :folio date :date} ticket]
    (if (seq ticket)
      (do
        (printing/print-ticket prods pay total change ticket-number folio date)
        (utils/flash-message "El ticket ha sido impreso" "info"))
      (utils/flash-message "Folio inválido"))
    (resp/redirect (str "/tickets/folio/" folio "/"))))
