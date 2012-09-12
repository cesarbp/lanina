(ns lanina.views.ticket
  (:use noir.core
        hiccup.form
        lanina.views.common
        [hiccup.element :only [link-to]])
  (:require [lanina.models.ticket :as ticket]
            [lanina.views.utils   :as utils]
            [clj-time.core        :as time]))

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
   [:div.alert.alert-info
    [:h2 "Total: "
     (format "%.2f" (double total))]]
   [:div.alert.alert-info
    [:h2 "Pagó: "
     (format "%.2f" (double pay))]]])

(defn get-article [denom]
  (letfn [(is-bc [d] (every? (set (map str (range 10)))
                             (rest (clojure.string/split (name d) #""))))
          (is-gvdo [d] (= "gvdo" (clojure.string/lower-case (apply str (take 4 (name d))))))
          (is-exto [d] (= "exto" (clojure.string/lower-case (apply str (take 4 (name d))))))]
    (cond (is-bc denom) (article/get-by-barcode denom)
          (is-gvdo denom) {:codigo "0" :nom_art "ARTÍCULO GRAVADO"
                           :prev_con (Double/parseDouble (clojure.string/replace (clojure.string/replace denom #"gvdo\d+_" "")
                                                                                 #"_" "."))}
          (is-exto denom) {:codigo "0" :nom_art "ARTÍCULO EXENTO"
                           :prev_sin (Double/parseDouble (clojure.string/replace (clojure.string/replace denom #"exto\d+_" "")
                                                                                 #"_" "."))}
          :else (article/get-by-name (clojure.string/replace denom #"_" " ")))))

(defpartial printed-ticket [prods pay total change]
  (let [now (time/now)
        date (str (time/day now) "/" (time/month now) "/" (time/year now))
        t (str (format "%02d" (time/hour now)) ":"
               (format "%02d" (time/minute now)) ":" (format "%02d" (time/sec now)))]
    [:pre.prettyprint.linenums {:style "max-width:235px;"}
     [:ol.linenums {:style "list-style-type:none;"}
      [:p 
       [:li {:style "text-align:center;"} "\"L A N I Ñ A\""]
       [:li {:style "text-align:center;"} "R.F.C: ROHE510827-8T7"]
       [:li {:style "text-align:center;"} "GUERRERO No. 45 METEPEC MEX."]
       [:li {:style "text-align:center;"} (str date " " t " TICKET:##")]]
      (map (fn [art] [:p
                      [:li (:nom_art art)]
                      [:li {:style "text-align:right;"}
                       (str (:quantity art) " x "
                            (format "%.2f" (double (:precio_unitario art))) " = "
                            (format "%.2f" (double (:total art))))]])
           prods)
      [:br]
      [:p
       [:li {:style "text-align:right;"}
        (format "SUMA ==> $ %8.2f" (double total))]
       [:li {:style "text-align:right;"}
        (format "CAMBIO ==> $ %8.2f" (double change))]
       [:li {:style "text-align:right;"}
        (format "EFECTIVO ==> $ %8.2f" (double pay))]]]]))

;;; Fixme - this should be POST
(defpage "/tickets/nuevo/" {:as items}
  (let [pay (Double/parseDouble (:pay items))
        items (dissoc items :pay)
        pairs (zipmap (keys items) (map #(Integer/parseInt %) (vals items)))
        prods (reduce (fn [acc [bc times]]
                        (let [article (get-article (name bc))
                              name (:nom_art article)
                              type (if (and (:prev_con article) (> (:prev_con article) 0.0))
                                      :gvdo :exto)
                              price (if (and (:prev_con article) (> (:prev_con article) 0.0))
                                      (:prev_con article) (:prev_sin article))
                              total (* price times)
                              art {:type type :quantity times :nom_art name :precio_unitario price :total total :codigo bc :cantidad times}]
                          (into acc [art])))
                      [] pairs)
        total (reduce + (map :total prods))
        change (- pay total)
        content {:title [:div.alert.alert-error
                         [:h1 "Cambio: "
                          (format "%.2f" (double change))]]
                 :content [:div.container-fluid
                           (pay-notice pay total change)
                           [:hr]
                           (printed-ticket prods pay total change)]
                 :active "Ventas"}]
    (ticket/insert-ticket pay prods)
    (home-layout content)))

(defpartial search-ticket-form []
  (let [now (time/now)
        date (str (format "%02d" (time/day now)) "/" (format "%02d" (time/month now)) "/" (format "%02d" (time/year now)))]
    (form-to {:class "form-horizontal"} [:get "/tickets/buscar"]
      [:legend "Buscar un ticket"]
      [:fieldset
       [:div.control-group
        (label {:class "control-label"} "date" "Buscar por fecha")
        [:div.controls
         [:input {:type "date" :name "date" :format "dd/mm/yyyy" :value (clojure.string/join "/" (reverse (clojure.string/split date #"/")))}]]]
       [:div.control-group
        (label {:class "control-label"} "folio" "Buscar por número de folio")
        [:div.controls
         (text-field "folio")]]]
      [:div.form-actions
       (submit-button {:class "btn btn-primary"} "Buscar")])))

(defpartial cut-form []
  (let [now (time/now)
        date (str (format "%02d" (time/day now)) "/" (format "%02d" (time/month now)) "/" (format "%02d" (time/year now)))]
    (form-to {:class "form-horizontal"} [:get "/tickets/corte"]
      [:legend "Corte de caja"]
      [:fieldset
       [:div.control-group
        (label {:class "control-label"} "date" "Indicar fecha de corte")
        [:div.controls
         [:input {:type "date" :name "date" :format "dd/mm/yyyy" :value (clojure.string/join "/" (reverse (clojure.string/split date #"/")))}]]]]
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
        folio (:folio ticket)]
    [:tr
     [:td (link-to (str "/tickets/folio/" folio "/") folio)]
     [:td date]
     [:td (format "%.2f" total)]
     [:td (format "%.2f" pay)]
     [:td (format "%.2f" (- pay total))]]))

(defpartial ticket-results-table [tickets]
  [:table.table.table-condensed
   [:tr
    [:th "Folio"]
    [:th "Fecha"]
    [:th "Total"]
    [:th "Pagado"]
    [:th "Cambio"]]
   (map ticket-results-row tickets)])

(defpage "/tickets/buscar" {:keys [date folio]}
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
                      (map #(reduce + 0.0 (map :total %)) ((juxt filter remove) #(= :gvdo (:gvdo %)) prods)))
                    all-arts)
        gvdo (reduce + (map first gvdos-extos))
        exto (reduce + (map second gvdos-extos))
        iva (if (> gvdo 0.0)
              (- gvdo (/ gvdo 1.16))
              0.0)
        total (+ gvdo exto)]
    [:div.container-fluid
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

(defpage "/tickets/corte" {:keys [date]}
  (let [tickets (ticket/search-by-date date)
        cut (when (seq tickets) (cashier-cut tickets))
        content {:title (str "Corte de caja de la fecha " (ticket/fix-date date))
                 :content (if (seq tickets)
                            cut
                            [:div.container-fluid
                             [:p {:class "alert alert-error"} "Este día no tiene ventas."]])
                 :nav-bar true
                 :active "Tickets"}]
    (home-layout content)))