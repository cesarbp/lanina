(ns lanina.views.list
  (:use noir.core
        hiccup.form
        lanina.views.common
        [hiccup.element :only [link-to]])
  (:require [lanina.models.logs :as logs]
            [lanina.models.article :as article]
            [lanina.models.employee :as employee]))

(defpartial art-row [art]
  [:tr
   [:td (:codigo art)]
   [:td (:nom_art art)]
   [:td (text-field {:class "input-small" :placeholder "##"} (:_id art))]])

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
        arts (map (fn [l] (article/get-by-id-only (:art-id l) [:nom_art :codigo]))
                  logs)
        content {:title "Listas"
                 :content [:div.container-fluid
                           (if (seq arts)
                             (art-table arts)
                             [:p {:class "alert alert-error"} "No hay listas para procesar"])
                           [:script "$('input:eq(1)').focus();"]]
                 :nav-bar true
                 :active "Listas"}]
    (main-layout-incl content [:base-css :jquery])))

(defpartial assign-employees-row [[n ids]]
  (let [employees employee/employee-list
        arts (map (fn [id] (article/get-by-id-only id [:nom_art :codigo]))
                  ids)]
    [:table.table.table-condensed
     [:tr
      [:th "Código"]
      [:th "Nombre"]]
     (map (fn [art] [:tr [:td (:codigo art)] [:td (:nom_art art)]]) arts)
     [:div.form-actions
      [:p (str "Grupo " n)]
      [:select
       [:option {:value ""} "Escoja un empleado"]
       (map (fn [e] [:option {:value e} e]) employees)]]]))

(defpartial assign-employees-form [grouped]
  (form-to {:class "form form-horizontal"} [:post "/empleados/asignar/"]
    (map assign-employees-row grouped)
    [:div.form-actions
     (submit-button {:class "btn btn-primary"} "Crear lista")]))

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
                           (assign-employees-form grouped)]
                 :nav-bar true
                 :active "Listas"}]
    (home-layout content)))

(defpage "/logs/clear/" []
  (logs/remove-logs)
  "done!")