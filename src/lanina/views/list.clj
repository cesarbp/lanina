(ns lanina.views.list
  (:use noir.core
        hiccup.form
        lanina.views.common
        [hiccup.element :only [link-to]])
  (:require [lanina.models.logs :as logs]
            [lanina.models.article :as article]
            [lanina.models.employee :as employee]
            [clj-time.core :as time]))

(defpartial art-row [art]
  [:tr
   [:td (:codigo art)]
   [:td (:nom_art art)]
   [:td (text-field {:autocomplete "off" :class "input-small" :placeholder "##"} (:_id art))]])

(defpartial art-table [arts]
  (form-to [:post "/listas/nueva/"]
      [:table.table.table-condensed
       [:tr
        [:th "Código"]
        [:th "Nombre"]
        [:th "Número"]]
       (map art-row arts)
       [:tr
        [:div.form-actions
         (submit-button {:class "btn btn-primary"} "Continuar")]]]))

(defpage "/listas/" []
  (let [logs (filter (fn [l] (and (not (:cleared l))
                                  (or (= "updated" (:type l))
                                      (= "added"  (:type l)))))
                     (logs/retrieve-all))
        arts (remove nil? (map (fn [l] (article/get-by-id-only (:art-id l) [:nom_art :codigo]))
                               logs))
        content {:title "Listas"
                 :content [:div.container-fluid
                           (if (seq arts)
                             (art-table arts)
                             [:p {:class "alert alert-error"} "No hay listas para procesar"])
                           [:script "$('input:eq(1)').focus();"]]
                 :nav-bar true
                 :active "Listas"}]
    (println (seq arts))
    (main-layout-incl content [:base-css :jquery])))

(defpartial assign-employees-row [[n ids]]
  (let [employees employee/employee-list
        arts (map (fn [id] (article/get-by-id-only id [:nom_art :codigo]))
                  ids)]
    (form-to [:post "/listas/imprimir/"]
        [:table.table.table-condensed
         [:tr
          [:th "Código"]
          [:th "Nombre"]]
         (map (fn [art] [:tr
                         [:td (:codigo art)]
                         [:td (:nom_art art)]]) arts)
         [:div.form-actions
          [:p (str "Grupo " n)]
          (label {:class "control-label"} "employee" "Nombre de empleado")
          (text-field {:autocomplete "off"} "employee")
          (hidden-field :ids (seq (map :_id arts)))
          [:br]
          (submit-button {:class "btn btn-primary"} "Imprimir")]])))

(defpage [:post "/listas/nueva/"] {:as pst}
  (let [grouped (reduce
                 (fn [acc [id n]]
                   (update-in acc [n]
                              (fn [old new] (if (seq old) (conj old new) [new]))
                              id))
                 {}
                 pst)
        content {:title "Crear una lista"
                 :content [:div.container-fluid
                           (map assign-employees-row grouped)
                           [:script "$($('input[type=\"text\"]')[0]).focus();"]
                           [:div.form-actions
                            (link-to {:class "btn btn-danger"} "/listas/" "Cancelar")]]
                 :nav-bar true
                 :active "Listas"}]
    (main-layout-incl content [:base-css :jquery])))

(defpage "/logs/clear/" []
  (logs/remove-logs)
  "done!")

(defpartial list-as-printed [employee date barcodes names]
  [:pre.prettyprint.linenums {:style "max-width:235px;"}
   [:ol.linenums {:style "list-style-type:none;"}
    [:p
     [:li {:style "text-align:center;"} "\"L A N I Ñ A\""]
     [:li {:style "text-align:center;"} (str "LISTA PARA EMPLEADO: " (clojure.string/upper-case employee))]
     [:li {:style "text-align:center;"} (str "FECHA: " date)]
     (map (fn [bc name]
            [:p [:li  bc]
             [:li name]])
          barcodes names)]]])

(defpage [:post "/listas/imprimir/"] {:as post}
  (let [now (time/now)
        date (str (format "%02d" (time/day now)) "/" (format "%02d" (time/month now)) "/" (format "%02d" (time/year now)))
        employee (:employee post)
        ids (read-string (:ids post))
        arts (map article/get-by-id ids)
        barcodes (map :codigo arts)
        names    (map :nom_art arts)
        content {:title "Lista Impresa"
                 :nav-bar true
                 :active "Listas"
                 :content [:div.container-fluid (list-as-printed employee date barcodes names)]}]
    (home-layout content)))