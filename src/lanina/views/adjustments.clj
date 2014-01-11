(ns lanina.views.adjustments
  (:use noir.core
        lanina.views.common
        hiccup.form
        [hiccup.element :only [link-to]]
        [lanina.views.home :only [user-select]]
        lanina.utils)
  (:require [lanina.views.utils :as t]
            [lanina.models.article :as article]
            [lanina.models.adjustments :as model]
            [lanina.models.error :as error]
            [noir.response :as resp]
            [noir.session :as session]
            [lanina.models.user :as user]
            [lanina.models.logs :as logs]
            ;[dbf.core :as dbf]
            [clojure.java.io :as io]
            [clojure.string :as s]))

;;; changing IVA
(defpartial change-iva-form []
  (let [ivas (model/get-valid-ivas)
        ivas-str (clojure.string/join ", " ivas)]
    (form-to {:class "form-horizontal"} [:post "/ajustes/iva/"]
             [:legend "Ajuste de IVA"]
             [:fieldset
              [:div.control-group
               [:p.control-label {:style "clear:left;"} "Valores de IVA actuales"]
               [:div.controls
                [:p {:style "position:relative;top:5px;"} ivas-str]]]
              [:div.control-group
               (label {:class "control-label"} "iva" "Nuevos valores de IVA, separe por comas.")
               [:div.controls
                (text-field {:autocomplete "off"} "iva")]]]
             [:div.form-actions
              (submit-button {:class "btn btn-primary"} "Cambiar IVA")])))
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
  (let [remaining (clojure.set/difference (set model/valid-time-units) #{orig})]
    [:select {:name :unit}
     [:option {:value orig} (model/valid-time-units-trans orig)]
     (map (fn [opt]
            [:option {:value opt} (model/valid-time-units-trans opt)])
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

(defpartial backup-time-unit-select [default sname]
  (let [valid {"hours" "horas"
               "days" "días"}
        other (first (keys (dissoc valid default)))]
    [:select {:name sname}
     [:option {:value default} (valid default)]
     [:option {:value other} (valid other)]]))

(defpartial change-backup-settings-form [previous]
  (form-to [:post "/ajustes/respaldos/"]
           [:legend "Ajustes en el sistema de respaldos"]
           [:fieldset
            [:div.control-group
             (label {:class "control-label"} :amount "Cantidad")
             [:div.controls
              (text-field {:placeholder (str "Previo: " (:amount previous))} :amount (:amount previous))]]
            [:div.control-group
             (label {:class "control-label"} :unit "Unidad de tiempo")
             [:div.controls
              (backup-time-unit-select (:unit previous) :unit)]]
            [:div.control-group
             (label {:class "control-label"} :start "Hora de inicio")
             [:div.controls
              (text-field {:placeholder (str "Previo: " (:start previous))} :start (:start previous))]]
            [:div.control-group
             (label {:class "control-label"} :primary "Respaldo primario")
             [:div.controls
              (text-field {:placeholder (str "Previo: " (:primary previous))} :primary (:primary previous))]]
            [:div.control-group
             (label {:class "control-label"} :secondary "Respaldo secundario")
             [:div.control-group
              (text-field {:placeholder (str "Previo: " (:secondary previous))} :secondary (:secondary previous))]]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Cambiar")]))

;;; Database errors
(defpartial error-notice [error-count]
  (let [error-msg (when error-count (str "Hay " error-count " errores a corregir en la base de datos"))]
    [:div.container-fluid
     [:legend "Errores en la Base de Datos"]
     (if error-msg
       [:div
        [:p "Errores a corregir: " [:span.label.label-important error-msg]]
        [:div.form-actions (link-to {:class "btn btn-primary"} "/ajustes/errores/" "Ver errores")]]
       [:span.label.label-info "No hay errores para corregir en la base de datos"])]))
;;; Database Warnings
(defpartial warning-notice [warning-count]
  (let [warning-msg (when warning-count (str "Hay " warning-count " advertencias a corregir en la base de datos"))]
    [:div.container-fluid
     [:legend "Advertencias en la Base de Datos"]
     (if warning-msg
       [:div
        [:p "Advertencias a corregir: " [:span.label.label-important warning-msg]]
        [:div.form-actions (link-to {:class "btn btn-primary"} "/ajustes/advertencias/" "Ver advertencias")]]
       [:span.label.label-info "No hay errores para corregir en la base de datos"])]))

;;; Adjust threshold pages
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

(defpage [:post "/ajustes/iva/"] {:keys [iva]}
  (let [splt (clojure.string/split iva #"[,\s]+")
        mped (map article/to-double splt)
        status (model/adjust-valid-ivas mped)]
    (if (= :success status)
      (do (session/flash-put! :messages (list {:type "alert-success" :text "El IVA ha sido modificado"}))
          (resp/redirect "/ajustes/"))
      (do (session/flash-put! :messages (list {:type "alert-success" :text (str "\"" iva "\" contiene algún número inválido")}))
          (resp/redirect "/ajustes/")))))

(defpage [:post "/ajustes/imagenes/directorio/"] {:keys [new]}
  (let [status (model/adjust-image-path new)]
    (if (= status :success)
      (do (session/flash-put! :messages (list {:type "alert-success" :text "El directorio de imágenes ha sido modificado"}))
          (resp/redirect "/ajustes/"))
      (do (session/flash-put! :messages (list {:type "alert-error" :text "El directorio especificado es inválido o no existe"}))
          (resp/redirect "/ajustes/")))))

;;; TODO - find an efficient way to show errors

(defpage "/ajustes/" []
  (let [;; errors-warnings (error/find-errors-warnings)
        ;; error-count (:error-count errors-warnings)
        ;; warning-count (:warning-count errors-warnings)
        previous-backup-settings (model/get-backup-settings)
        content {:title "Ajustes"
                 :content [:div
                           [:div.container-fluid
                            {:style "background-image: url(\"../img/bedge_grunge.png\")"} (change-iva-form)]
                           [:div.container-fluid (change-password-form)]
                           ;; [:div.container-fluid
                           ;;  (change-modify-threshold-form)]
                           ;; [:div.container-fluid
                           ;;  {:style "background-image: url(\"../img/bedge_grunge.png\")"} (change-image-dir-form)]
                           ;; [:div.container-fluid
                           ;;  (change-backup-settings-form previous-backup-settings)]
                           ]
                 :nav-bar true
                 :active "Herramientas"}]
    (home-layout content)))

(defpartial show-wrong-articles [{errors :error-articles}]
  [:ul.nav.nav-tabs.nav-stacked
   (map (fn [{_id :_id}]
          (let [{:keys [nom_art codigo iva]}
                (article/get-by-id-only _id [:codigo :nom_art :iva :precio_venta])]
            [:li (link-to (str "/ajustes/errores/" _id "/")
                          (str "Nombre: " nom_art ", Código: " codigo " iva: \"" iva "\""))]))
        errors)])

(defpartial show-warnings-articles [{warnings :warning-articles}]
  [:ul.nav.nav-tabs.nav-stacked
   (map (fn [{_id :_id}]
          (let [{:keys [nom_art codigo iva]}
                (article/get-by-id-only _id [:codigo :nom_art :iva :precio_venta])]
            [:li (link-to (str "/ajustes/advertencias/" _id "/")
                          (str "Nombre: " nom_art ", Código: " codigo " iva: \"" iva "\""))]))
        warnings)])

(defpage "/ajustes/errores/" []
  (let [errors-warnings (error/find-errors-warnings)
        content {:title "Errores en la base de datos"
                 :content [:div.container-fluid (show-wrong-articles errors-warnings)
                           [:div.form-actions (link-to {:class "btn btn-success"} "/ajustes/" "Regresar a ajustes")]]
                 :nav-bar true
                 :active "Herramientas"}]
    (home-layout content)))

(defpage "/ajustes/advertencias/" []
  (let [errors-warnings (error/find-errors-warnings)
        content {:title "Errores en la base de datos"
                 :content [:div.container-fluid (show-warnings-articles errors-warnings)
                           [:div.form-actions (link-to {:class "btn btn-success"} "/ajustes/" "Regresar a ajustes")]]
                 :nav-bar true
                 :active "Herramientas"}]
    (home-layout content)))

;;; Not exactly the same as the views/article partials
(defpartial iva-select [current]
  (let [ivas (set (model/get-valid-ivas))
        fst (when (model/valid-iva? current) current (first ivas))
        rst (disj ivas fst)
        lst (cons fst rst)]
    [:select {:name :iva}
     (map (fn [v]
            [:option {:value v} v])
          lst)]))

(defpartial lin-select [orig]
  (let [valid (set article/lines)
        fst (if (valid orig) orig (first article/lines))
        rst (if (valid orig) (remove #{fst} article/lines) (rest article/lines))
        lst (cons fst rst)]
    [:select {:name :lin}
     (map (fn [v]
            [:option {:value v} (article/line-select v)])
          lst)]))

(defpartial unit-select [orig]
  (let [valid (set article/units)
        fst (if (valid orig) orig (first article/units))
        rst (if (valid orig) (remove #{fst} article/units) (rest article/units))
        lst (cons fst rst)]
    [:select {:name :unidad}
     (map (fn [v]
            [:option {:value v} v])
          lst)]))

(defpartial modifiable-input [k v]
  (cond
   (= :unidad k) (unit-select v)
   (= :lin k) (lin-select v)
   (= :iva k) (iva-select v)
   :else  (text-field k)))

(defpartial error-row [k v es modifiable]
  [:tr
   [:td (article/verbose-names-new k)]
   [:td v]
   [:td (when modifiable (modifiable-input k v))]
   [:td [:ul.unstyled (for [e es]
               [:li [:p.text-error e]])]]])

(defpartial art-error-form [id art errors next]
  (form-to {:class "form-horizontal" :id "modify-article-form" :name "modify-article"}
           [:post (str "/ajustes/errores/" id "/confirmar/")]
           (hidden-field :next next)
           [:table.table.table-condensed
            [:thead
             [:tr
              [:th "Nombre"]
              [:th "Valor Previo"]
              [:th "Valor Nuevo"]
              [:th "Errores"]]]
            [:tbody
             (for [[k es] errors]
               (error-row k (k art) es (seq es)))]]
           [:div.form-actions
            (if (every? (complement second) errors)
              (link-to {:class "btn btn-primary"} "/articulos/corregir/" "Regresar")
              (list
               (submit-button {:class "btn btn-primary"} "Modificar")
               (link-to {:class "btn btn-danger"} "/articulos/corregir/" "Cancelar y regresar")))]))

(defpage "/ajustes/errores/:id/" {:keys [id next]}
  (let [art (article/get-by-id id)
        {errors :errors} (article/errors-warnings art)
        errors-sorted (article/sort-by-vec errors article/new-art-props-sorted)
        content {:title (str "Errores de " (:nom_art art))
                 :content [:div.container-fluid
                           (when (every? (complement second) errors-sorted)
                             [:p.alert.alert-info "El artículo no tiene errores"])
                           (art-error-form id art errors-sorted next)]
                 :active "Ajustes"
                 :footer [:p "Gracias por visitar."]
                 :nav-bar true}]
    (home-layout content)))

(defpartial art-warning-form [id art warnings]
  (form-to {:class "form-horizontal" :id "modify-article-form" :name "modify-article"}
           [:post (str "/ajustes/advertencias/" id "/confirmar/")]
           [:table.table.table-condensed
            [:thead
             [:tr
              [:th "Nombre"]
              [:th "Valor Previo"]
              [:th "Valor Nuevo"]
              [:th "Advertencias"]]]
            [:tbody
             (for [[k es] warnings]
               (error-row k (k art) es (seq es)))]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Modificar")]))

(defpage "/ajustes/advertencias/:id/" {id :id}
  (let [art (article/get-by-id id)
        {errors :errors warnings :warnings} (article/errors-warnings art)
        warnings-sorted (article/sort-by-vec warnings  article/new-art-props-sorted)
        content {:title (str "Advertencias de " (:nom_art art))
                 :content (if (seq art)
                            [:div.container-fluid
                             (when-not (empty? errors)
                               [:div.alert.alert-error
                                [:p "Este artículo tiene errores, se requiere corregirlos antes de corregir las advertencias"]
                                (link-to {:class "btn"} (str "/ajustes/errores/" id "/") "Ir a corregir errores")])
                             (art-warning-form id art warnings-sorted)]
                              [:div.container-fluid [:p.error-notice "Este artículo no existe."]])
                 :active "Ajustes"
                 :footer [:p "Gracias por visitar."]
                 :nav-bar true}]
    (home-layout content)))

(defpage [:post "/ajustes/:type/:id/confirmar/"] {:as pst}
  (let [id (:id pst)
        nxt (:next pst)
        type (:type pst)
        pst (dissoc pst :id :type)
        resp (article/update-article! id pst)
        date (t/now)
        {:keys [nom_art]} (article/get-by-id id)
        search-q (t/url-encode nom_art)
        redirect-url "/articulos/corregir/"]
    (if (= :success resp)
      (do (logs/add-logs! id :updated {} date)
          (session/flash-put! :messages '({:type "alert-success" :text "El artículo ha sido modificado"}))
          (resp/redirect (if (seq nxt)
                           nxt
                           redirect-url)))
      (do (session/flash-put! :messages (for [[k es] resp e es]
                                          {:type "alert-error" :text e}))
          (resp/redirect (str "/ajustes/" type "/" id "/"))))))

(defpage "/ajustes/reset/" []
  (model/setup!)
  "done!")

;;; No longer used
(defpage [:post "/db/export/"] {:keys [coll format]}
  (let [fname (str (name coll) "." format)]
    ;(dbf/mongo-coll-to-dbf! coll fname)
    (resp/set-headers {"Content-Description" "File Transfer"
                       "Content-type" "application/octet-stream"
                       "Content-Disposition" (str "attachment; filename=" fname)
                       "Content-Transfer-Encoding" "binary"}
                      (java.io.ByteArrayInputStream.
                       (slurp-binary-file (java.io.File. fname))))))

(defn system-messages [results]
  (doseq [result results]
    (let [exit (:exit result)
          msg (:out result)
          error (:err result)]
      (if (= exit 0)
        (session/flash-put! :messages (list {:type "alert-success" :text (str "Se completó exitosamente la operación. El sistema dijo: " msg)}))
        (session/flash-put! :messages (list {:type "alert-error" :text (str " La operación no se completó. Mensaje de error: " error)}))))))


;;; No longer used
(defpage [:post "/db/import/dbf/"] {:keys [coll path]}
  (try ;(dbf/dbf-to-mongo! coll path)
       (session/flash-put! :messages (list {:type "alert-success" :text (str "Se agregaron exitosamente los registros a la colección " (name coll))}))
       (catch Exception e
         (session/flash-put! :messages (list {:type "alert-error" :text (str "No se pudieron agregar los registros, verifique la dirección del archivo")}))))
  (resp/redirect "/respaldos/"))
