(ns lanina.views.adjustments
  (:use noir.core
        lanina.views.common
        hiccup.form
        [hiccup.element :only [link-to]]
        [lanina.views.home :only [user-select]])
  (:require [lanina.models.article :as article]
            [lanina.models.adjustments :as model]
            [noir.response :as resp]
            [noir.session :as session]))

;;; changing IVA
(defpartial change-iva-form []
  (form-to {:class "form-horizontal"} [:post "/ajustes/iva/"]
    [:legend "Ajuste de IVA"]
    [:fieldset
     [:div.control-group
      [:p.control-label {:style "clear:left;"} "Viejo valor de iva:"]
      [:div.controls
       [:p {:style "position:relative;top:5px;"} (str (model/get-current-iva))]]]
     [:div.control-group
      (label {:class "control-label"} "iva" "Nuevo valor del IVA")
      [:div.controls
       (text-field {:autocomplete "off"} "iva")]]]
    [:div.form-actions
     (submit-button {:class "btn btn-primary"} "Cambiar IVA")]))
;;; changing passwords
(defpartial change-password-form []
  (form-to {:class "form-horizontal"} [:post "/ajustes/password/"]
    [:legend "Ajuste de Contraseña"]
    [:fieldset
     [:div.control-group
      (label {:class "control-label"} "user" "Usuario")
      [:div.controls
       (user-select)]]
     [:div.control-group
      (label {:class "control-label"} "old" "Contraseña Previa")
      [:div.controls
       (text-field "pass")]]
     [:div.control-group
      (label {:class "control-label"} "new" "Contraseña Nueva")
      [:div.controls
       (text-field "pass")]]]
    [:div.form-actions
     (submit-button {:class "btn btn-primary"} "Cambiar contraseña")]))
;;; Database errors
(defpartial error-notice [error-count]
  (let [error-msg (when error-count (str "Hay " error-count " errores a corregir en la base de datos"))]
    [:div.container-fluid
     [:legend "Errores en la Base de Datos"]
     (if error-msg
       [:div
        [:p "Errores a corregir: " [:span.label.label-important error-msg]]
        [:div.form-actions (link-to {:class "btn btn-primary"} "/ajustes/bd/" "Ver errores")]]
       [:span.label.label-info "No hay errores para corregir en la base de datos"])]))

(defpage "/ajustes/" []
  (let [error-count (model/count-all-errors)
        content {:title "Ajustes"
                 :content [:div
                           [:div.container-fluid
                            {:style "background-image: url(\"../img/bedge_grunge.png\")"} (change-iva-form)]
                           [:div.container-fluid (change-password-form)]
                           [:div.container-fluid
                            {:style "background-image: url(\"../img/bedge_grunge.png\")"} (error-notice error-count)]]
                 :nav-bar true
                 :active "Ajustes"}]
    (home-layout content)))

;;; Error counts

(defpartial iva-errors-count-li []
  (let [errors (model/find-iva-errors)
        number (count errors)]
    [:li (link-to "/errores/iva/" (str "Hay " number " artículos con IVA incorrecto"))]))

(defpartial duplicate-value-errors-count-li [prop]
  (let [errs (if (= prop :codigo) (model/find-duplicate-barcode-errors) (model/find-duplicate-value-errors prop))]
    (when (seq errs)
      [:li (link-to (str "/errores/" (name prop) "/duplicado/")
                    (str "Hay " (count (map second errs))                     
                         " artículos con " (article/verbose-names prop) " duplicado"))])))

(defpartial empty-value-errors-count [prop]
  (let [errs (model/find-empty-value-errors prop)]
    (when (seq errs)
      [:li (link-to (str "/errores/" (name prop) "/faltante/")
                    (str "Hay " (count (map second errs))                     
                         " artículos que no tienen " (article/verbose-names prop)))])))

(defpartial show-error-counts []
  [:ul.nav.nav-tabs.nav-stacked
   ;; All the partials for error counts
   (iva-errors-count-li)
   (duplicate-value-errors-count-li :nom_art)
   (duplicate-value-errors-count-li :codigo)
   (empty-value-errors-count :nom_art)
   (empty-value-errors-count :codigo)])

(defpage "/ajustes/bd/" []
  (let [content {:title "Errores en la base de datos"
                 :content [:div.container-fluid (show-error-counts)]
                 :nav-bar true
                 :active "Ajustes"}]
    (home-layout content)))

;;; Error pages
(defpartial iva-error-row [id]
  (let [{:keys [nom_art codigo iva _id]} (article/get-by-id-only id [:nom_art :codigo :iva])]
    [:li (link-to (str "/errores/corregir/iva/" _id "/")
                  (str "Nombre: " nom_art ", Código: " codigo " iva: \"" iva "\""))]))

(defpartial iva-error-list [ids]
  [:ul.nav.nav-tabs.nav-stacked
   (map iva-error-row ids)])

(defpartial duplicate-value-errors-row [prop [repeated-prop ids]]
  (let [identifiers (map (comp (if (= :nom_art prop) :codigo :nom_art) #(article/get-by-id %)) ids)
        repeat-msg (str "\" se repite en los artículos con los " (if (= :nom_art prop) "códigos" "nombres") " siguientes: ")]
    [:li (link-to (str "/errores/corregir/duplicado/" (name prop) "/?ids=" (clojure.string/join "," ids))
                  (str "El " (article/verbose-names prop) " \"" repeated-prop repeat-msg (clojure.string/join "," identifiers)))]))

(defpartial duplicate-value-errors-list [prop id-groups]
  [:ul.nav.nav-tabs.nav-stacked
   (map (partial duplicate-value-errors-row prop) id-groups)])

(defpartial empty-value-errors-list [prop ids]
  [:ul.nav.nav-tabs.nav-stacked
   (map (fn [id]
          (let [id (:_id id)
                bc (:codigo (article/get-by-id-only id [:codigo]))]
            [:li (link-to (str "/errores/corregir/faltante/" (name prop) "/")
                          (str "El artículo con código \"" bc "\" no tiene " (article/verbose-names prop)))]))
        ids)])

(defpage "/errores/:prop/faltante/" {prop :prop}
  (let [errors (model/find-empty-value-errors :nom_art)
        content {:title "Artículos sin nombre"
                 :nav-bar true
                 :content [:div.container-fluid (empty-value-errors-list :nom_art errors)]}]
    (home-layout content)))

(defpage "/errores/:prop/duplicado/" {prop :prop}
  (let [prop (keyword prop)
        errors (if (= :codigo prop) (model/find-duplicate-barcode-errors) (model/find-duplicate-value-errors prop))
        content {:title (str "Artículos con " (article/verbose-names prop) " duplicado")
                 :nav-bar true
                 :content [:div.container-fluid (duplicate-value-errors-list prop errors)]}]
    (home-layout content)))

(defpage "/errores/iva/" []
  (let [errors (model/find-iva-errors)
        content {:title "Artículos con errores en el iva"
                 :nav-bar true
                 :content [:div.container-fluid (iva-error-list errors)]}]
    (home-layout content)))

;;; Fix the error pages
(defpartial fix-error-form [art]
  (let [art-map (apply hash-map (reduce into [] art))]
    (form-to [:post (str "/errores/corregir/iva/" (:_id art-map) "/")]
        [:table.table.table-condensed
         (map (fn [[k v]]
                (when (not= k :_id)
                  [:tr [:td (article/verbose-names k)]
                   [:td (str v)]]))
              art)
         [:tr.info [:td "Poner iva:"] [:td "Sí" (radio-button "iva" false "yes") "No" (radio-button "iva" false "no")]]]
      [:div.form-actions
       (submit-button {:class "btn btn-primary"} "Corregir IVA")])))

(defpage "/errores/corregir/iva/:_id/" {id :_id}
  (let [art (article/sort-by-vec (article/get-by-id-only id [:codigo :nom_art :prev_con :prev_sin :ccj_con :ccj_sin])
                         [:codigo :nom_art :ccj_con :prev_con :ccj_sin :prev_sin])
        content {:title "Corrigiendo errores en el iva"
                 :nav-bar true
                 :content (fix-error-form art)}]
    (home-layout content)))

(defpage [:post "/errores/corregir/iva/:_id/"] {:as pst}
  (let [id (:_id pst)
        iva (:iva pst)
        new-iva (cond (= "yes" iva)
                  (model/get-current-iva)
                  (= "no" iva) 0
                  :else nil)]    
    (if iva
      (do (model/adjust-individual-article-prop id :iva new-iva)
          (session/flash-put! :messages '({:type "alert-success" :text "El IVA ha sido corregido"}))
          (resp/redirect "/errores/iva/"))
      (do (session/flash-put! :messages '({:type "alert-error" :text "No eligió una opción para IVA"}))
          (resp/redirect (str "/errores/corregir/iva/" id "/"))))))

(defpage "/ajustes/super-secret" []
  (model/setup!)
  "done!")