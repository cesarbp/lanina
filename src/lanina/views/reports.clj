(ns lanina.views.reports
  (:use noir.core
        hiccup.form
        lanina.views.common)
  (:require [lanina.views.utils :as t]
            [lanina.models.article :as article]
            [lanina.models.ticket :as ticket]
            [lanina.models.reports :as model]))

(defpartial type-select []
  [:select {:name :tipo :class "span2"}
   (map (fn [val verbose]
          [:option {:value val} verbose])
        ["" "dia" "mes" "ano"] ["--" "Días" "Meses" "Años"])])

(defpartial period-select []
  [:select {:name :periodo :class "span2"}
   (map (fn [val verbose]
          [:option {:value val} verbose])
        ["" "dia" "mes" "ano"] ["--" "Día" "Mes" "Año"])])

(defpartial report-form []
  [:div.navbar
   [:div.navbar-inner
    (form-to {:class "navbar-form"} [:get "/reportes/"]
             [:ul.nav
              [:li [:a "Desde"]]
              [:li [:input {:class "span2" :type "date" :name :desde}]]
              [:li [:a "Hasta"]]
              [:li [:input {:class "span2" :type "date" :name :hasta}]]
              [:li.divider-vertical]
              [:li [:a "Hace"]]
              [:li (text-field {:class "span1" :placeholder "##"} :n)]
              [:li (type-select)]
              [:li.divider-vertical]
              [:li [:a "Reporte del"]]
              [:li (period-select)]
              [:li.divider-vertical]
              [:li (submit-button {:class "btn btn-primary"} "Generar Reporte")]])]])

(defn report-title
  [type from to]
  (str "Reporte "
       (cond (= :dia type) "del día de hoy"
             (= :mes type) (str "del mes (de " from " a " to ")")
             (= :ano type) (str "del año (de " from " a " to ")")
             :else (str "del periodo de " from " a " to))))

(defpartial report-rows
  [rows]
  (let [rs (reduce (fn [acc r]
                     (let [prev-class (if (empty? acc)
                                        "info"
                                        (:class (second (peek acc))))
                           opposite {"info" ""
                                     "" "info"}
                           prev-date (second (first (nth (peek acc) 2)))
                           this-date (first r)
                           next-class (if (= this-date prev-date)
                                        prev-class
                                        (opposite prev-class))]
                       (conj acc
                             [:tr {:class next-class}
                              (for [v r]
                                [:td (if (number? v) (t/format-decimal v) v)])])))
                   []
                   rows)]
    (seq rs)))

(defpartial show-report
  "Receives the report data as a map"
  [type {:keys [total-exto total-gvdo count count-individual total rows verbose from to]}]
  [:div
   [:h2 (report-title type from to)]
   [:ul.inline.label.label-info
    [:li [:p.lead (str "Total: $" (t/format-decimal total))]]
    [:li [:p.lead (str "Total Exentos: $" (t/format-decimal total-exto))]]
    [:li [:p.lead (str "Total Gravados: $" (t/format-decimal total-gvdo))]]]
   [:ul.inline.label.label-info
    [:li [:p.lead (str "Artículos Totales Vendidos: " count)]]
    [:li [:p.lead (str "Artículos Diferentes Vendidos: " count-individual)]]]
   [:hr]
   [:table.table.table-condensed.table-hover {:id "report"}
    [:thead
     [:tr
      (for [n verbose]
        [:td {:style "cursor: hand; cursor: pointer;"} n [:i.icon-resize-vertical.pull-right]])]]
    [:tbody
     (report-rows (take 500 rows))]]])

(defpartial table-sorter-js
  []
  [:script "$(document).ready(function(){ $(\"#report\").tablesorter(); });"])

(defn report-vars [n tipo desde hasta periodo]
  (cond (and (seq desde) (seq hasta))
        [:range (model/gen-report-data desde hasta)]
        (seq desde)
        [:range (model/gen-report-data desde)]
        (and (seq n) (seq tipo))
        (when-let [n (article/to-int n)]
          (when-let [range (cond (= "dia" tipo) (t/day-range n)
                               (= "mes" tipo) (t/month-range n)
                               (= "ano" tipo) (t/year-range n))]
            [:range (model/gen-report-data (first range) (second range))]))
        (= "dia" periodo) [:dia (model/gen-report-data (t/start-of-day))]
        (= "mes" periodo) [:mes (model/gen-report-data (t/start-of-month))]
        (= "ano" periodo) [:ano (model/gen-report-data (t/start-of-year))]))

(defpage "/reportes/" {:keys [n tipo desde hasta periodo]}
  (let [[type data] (report-vars n tipo desde hasta periodo)
        content [:div.container-fluid
                 (report-form)
                 (when type
                   (show-report type data))
                 (when type
                   (table-sorter-js))]]
    (main-layout-incl
     {:title "Reportes de ventas"
      :active "Reportes"
      :content content
      :nav-bar true}
     [:base-css :jquery :base-js :tablesorter-js])))
