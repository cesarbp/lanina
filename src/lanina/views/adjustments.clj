(ns lanina.views.adjustments
  (:use noir.core
        lanina.views.common
        hiccup.form
        [hiccup.element :only [link-to]]
        [lanina.views.home :only [user-select]]
        lanina.utils)
  (:require [lanina.models.article :as article]
            [lanina.models.adjustments :as model]
            [noir.response :as resp]
            [noir.session :as session]
            [lanina.models.user :as user]))

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
       (password-field "old")]]
     [:div.control-group
      (label {:class "control-label"} "new" "Contraseña Nueva")
      [:div.controls
       (password-field "new")]]]
    [:div.form-actions
     (submit-button {:class "btn btn-primary"} "Cambiar contraseña")]))
;;; Changing modify article threshold
(defpartial modify-threshold-unit-select [orig]
  (let [remaining (clojure.set/difference (set model/valid-modify-threshold-units) #{orig})]
    [:select {:name :unit}
     [:option {:value orig} (model/valid-modify-threshold-units-trans orig)]
     (map (fn [opt]
            [:option {:value opt} (model/valid-modify-threshold-units-trans opt)])
          (vec remaining))]))

(defpartial change-modify-threshold-form []
  (let [actual (model/get-modify-threshold-doc)]
    (form-to {:class "form-horizontal"} [:post "/ajustes/modify-threshold/"]
      [:legend "Ajuste del tiempo aceptable para que un artículo no reciba modificaciones"]
      [:fieldset
       [:div.control-group
        (label {:class "control-label"} :threshold "Nueva tolerancia")
        [:div.controls
         (text-field :threshold (:modify-threshold actual))]]
       [:div.control-group
        (label {:class "control-label"} :unit "Unidad de tiempo")
        [:div.controls
         (modify-threshold-unit-select (:unit actual))]]]
      [:div.form-actions
       (submit-button {:class "btn btn-primary"} "Cambiar")])))

(defpartial change-image-dir-form []
  (let [actual (model/get-image-path)]
    (form-to {:class "form-horizontal"} [:post "/ajustes/imagenes/directorio/"]
      [:legend "Ajuste del directorio de imágenes"]
      [:fieldset
       [:div.control-group
        (label {:class "control-label"} :old "Viejo directorio:")
        [:div.controls
         [:p {:style "position:relative;top:5px;"} actual]]]
       [:div.control-group
        (label {:class "control-label"} :new "Nuevo directorio")
        [:div.controls
         (text-field :new)]]]
      [:div.form-actions
       (submit-button {:class "btn btn-primary"} "Cambiar")])))

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
;;; Adjust IVA/password/threshold pages
(defpage [:post "/ajustes/iva/"] {:as pst}
  (let [new-iva ((coerce-to Double) (:iva pst))]
    (if new-iva
      (do (model/adjust-iva new-iva)
          (session/flash-put! :messages (list {:type "alert-success" :text (str "El IVA ha sido cambiado a " new-iva)}))
          (resp/redirect "/ajustes/"))
      (do (session/flash-put! :messages (list {:type "alert-error" :text "El valor de IVA \"" new-iva "\" no es válido"}))))))

(defpage [:post "/ajustes/modify-threshold/"] {:as pst}
  (let [thresh ((coerce-to Integer) (:threshold pst))
        unit (:unit pst)]
    (if thresh
      (do (model/adjust-modify-threshold thresh unit)
          (session/flash-put! :messages (list {:type "alert-success" :text (str "El nuevo tiempo de tolerancia ha sido ajustado a " (model/get-modify-threshold) " días.")}))
          (resp/redirect "/ajustes/"))
      (do (session/flash-put! :messages (list {:type "alert-error" :text "No introdujo una cantidad válida para la nueva tolerancia"}))
          (resp/redirect "/ajustes/")))))

(defpage [:post "/ajustes/password/"] {:keys [user old new]}
  (cond (not (and (seq user) (seq old) (seq new)))
        (do (session/flash-put! :messages (list {:type "alert-error" :text "Faltaron campos para cambiar la contrasena"}))
            (resp/redirect "/ajustes/"))
        (user/verify-pass user old)
        (let [notice (user/reset-pass! user new)]
          (if (= :success notice)
            (do (session/flash-put! :messages (list {:type "alert-success" :text "La contraseña ha sido modificada"}))
                (resp/redirect "/ajustes/"))
            (do (session/flash-put! :messages (list {:type "alert-error" :text "La contraseña es demasiado corta"}))
                (resp/redirect "/ajustes/"))))
        :else
        (do (session/flash-put! :messages (list {:type "alert-error" :text "La contraseña no es correcta"}))
                (resp/redirect "/ajustes/"))))

(defpage [:post "/ajustes/imagenes/directorio/"] {:keys [new]}
  (let [status (model/adjust-image-path new)]
    (if (= status :success)
      (do (session/flash-put! :messages (list {:type "alert-success" :text "El directorio de imágenes ha sido modificado"}))
                (resp/redirect "/ajustes/"))
      (do (session/flash-put! :messages (list {:type "alert-error" :text "El directorio especificado es inválido o no existe"}))
                (resp/redirect "/ajustes/")))))

(defpage "/ajustes/" []
  (let [error-count (model/count-all-errors)
        content {:title "Ajustes"
                 :content [:div
                           [:div.container-fluid
                            {:style "background-image: url(\"../img/bedge_grunge.png\")"} (change-iva-form)]
                           [:div.container-fluid (change-password-form)]
                           [:div.container-fluid
                            {:style "background-image: url(\"../img/bedge_grunge.png\")"} (error-notice error-count)]
                           [:div.container-fluid
                            (change-modify-threshold-form)]
                           [:div.container-fluid
                            {:style "background-image: url(\"../img/bedge_grunge.png\")"} (change-image-dir-form)]]
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

(defpartial empty-value-errors-count-li [prop]
  (let [errs (model/find-empty-value-errors prop)]
    (when (seq errs)
      [:li (link-to (str "/errores/" (name prop) "/faltante/")
                    (str "Hay " (count (map second errs))                     
                         " artículos que no tienen " (article/verbose-names prop)))])))

(defpartial beyond-threshold-errors-count-li []
  (let [thresh (model/get-modify-threshold-doc)
        thresh-count (:modify-threshold thresh)
        thresh-unit (model/valid-modify-threshold-units-trans (:unit thresh))
        errs (model/find-beyond-threshold-errors)]
    (when (seq errs)
      [:li (link-to (str "/errores/sin-modificar/")
                    (str "Hay " (count errs)                     
                         " artículos que no han sido modificados desde hace " thresh-count " " thresh-unit " o más."))])))

(defpartial show-error-counts []
  [:ul.nav.nav-tabs.nav-stacked
   ;; All the partials for error counts
   (iva-errors-count-li)
   (duplicate-value-errors-count-li :nom_art)
   (duplicate-value-errors-count-li :codigo)
   (empty-value-errors-count-li :nom_art)
   (empty-value-errors-count-li :codigo)
   (beyond-threshold-errors-count-li)])

(defpage "/ajustes/bd/" []
  (let [content {:title "Errores en la base de datos"
                 :content [:div.container-fluid (show-error-counts)
                           [:div.form-actions (link-to {:class "btn btn-success"} "/ajustes/" "Regresar a ajustes")]]
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

(defpage "/ajustes/reset/" []
  (model/setup!)
  "done!")