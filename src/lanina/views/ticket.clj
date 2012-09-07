(ns lanina.views.ticket
  (:use noir.core
        hiccup.form
        lanina.views.common)
  (:require [lanina.models.article :as article]
            [lanina.views.utils    :as utils]
            [clj-time.core         :as time]))

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

(defpartial pay-notice [pay total change]
  [:div.container-fluid
   [:div.alert.alert-info
    [:h2 "Total: "
     (format "%.2f" (double total))]]
   [:div.alert.alert-info
    [:h2 "Pagó: "
     (format "%.2f" (double pay))]]])

;;; Fixme - this should be POST
(defpage "/tickets/nuevo/" {:as items}
  (let [pay (Double/parseDouble (:pay items))
        items (dissoc items :pay)
        pairs (zipmap (keys items) (map #(Integer/parseInt %) (vals items)))
        prods (reduce (fn [acc [bc times]]
                        (let [article (get-article (name bc))
                              name (:nom_art article)
                              price (if (and (:prev_con article) (> (:prev_con article) 0.0))
                                      (:prev_con article) (:prev_sin article))
                              total (* price times)
                              art {:quantity times :nom_art name :precio_unitario price :total total :codigo bc :cantidad times}]
                          (into acc [art])))
                      [] pairs)
        total (reduce + (map :total prods))
        change (- pay total)
        foo (println (str prods))
        content {:title [:div.alert.alert-error
                         [:h1 "Cambio: "
                          (format "%.2f" (double change))]]
                 :content [:div.container-fluid
                           (pay-notice pay total change)
                           [:hr]
                           (printed-ticket prods pay total change)]
                 :active "Ventas"}]
    (home-layout content)))
