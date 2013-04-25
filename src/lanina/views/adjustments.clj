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
            [dbf.core :as dbf]
            [clojure.java.io :as io]
            [clj-commons-exec :as exec]
            [zip.core :as z]
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
        status (model/adjust-valid-ivas)]
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

(defpage "/ajustes/" []
  (let [errors-warnings (error/find-errors-warnings)
        error-count (:error-count errors-warnings)
        warning-count (:warning-count errors-warnings)
        previous-backup-settings (model/get-backup-settings)
        content {:title "Ajustes"
                 :content [:div
                           [:div.container-fluid
                            {:style "background-image: url(\"../img/bedge_grunge.png\")"} (change-iva-form)]
                           [:div.container-fluid (change-password-form)]
                           [:div.container-fluid
                            {:style "background-image: url(\"../img/bedge_grunge.png\")"} (error-notice error-count)]
                           [:div.container-fluid
                            (warning-notice warning-count)]
                           [:div.container-fluid
                            (change-modify-threshold-form)]
                           [:div.container-fluid
                            {:style "background-image: url(\"../img/bedge_grunge.png\")"} (change-image-dir-form)]
                           [:div.container-fluid
                            (change-backup-settings-form previous-backup-settings)]]
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
            [:option {:value v} v])
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

(defpartial art-error-form [id art errors]
  (form-to {:class "form-horizontal" :id "modify-article-form" :name "modify-article"}
           [:post (str "/ajustes/errores/" id "/confirmar/")]
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
            (submit-button {:class "btn btn-primary"} "Modificar")]))

(defpage "/ajustes/errores/:id/" {id :id}
  (let [art (article/get-by-id id)
        {errors :errors} (article/errors-warnings art)
        errors-sorted (article/sort-by-vec errors article/new-art-props-sorted)
        content {:title (str "Errores de " (:nom_art art))
                 :content [:div.container-fluid (art-error-form id art errors-sorted)]
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
        type (:type pst)
        pst (dissoc pst :id :type)
        resp (article/update-article! id pst)
        date (t/now)]
    (if (= :success resp)
      (do (logs/add-logs! id :updated {} date)
          (session/flash-put! :messages '({:type "alert-success" :text "El artículo ha sido modificado"}))
          (resp/redirect (str "/ajustes/" type "/")))
      (do (session/flash-put! :messages (for [[k es] resp e es]
                                          {:type "alert-error" :text e}))
          (resp/redirect (str "/ajustes/" type "/" id "/"))))))

(defpage "/ajustes/reset/" []
  (model/setup!)
  "done!")

(defpartial coll-select [collection-names]
  [:select {:name :coll}
   (map (fn [name]
          [:option {:value (keyword name)} name])
        collection-names)])

(defpartial backup-db-form [collection-names]
  (form-to {:class "form form-horizontal"} [:post "/db/backup/"]
           [:legend "Respaldar una colección de la base de datos"]
           [:p.alert "Los archivos se guardarán dentro de dump/nombre_de_coleccion/nombre_de_coleccion.bson dentro de los directorios primario, secundario y opcionalmente el directorio especificado abajo."]
           [:div.control-group
            (label {:class "control-label"} :dir "Opcional: Tercer directorio de respaldo (default /home/ubuntu/www/lanina/)")
            [:div.controls
             (text-field :dir)]]
           [:div.control-group
            (label {:class "control-label"} :zip "Respaldar toda la base de datos en formato zip.")
            (check-box :zip true)]
           [:div.control-group
            (label {:class "control-label"} :coll "Nombre de la colección")
            [:div.controls
             (coll-select collection-names)]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Respaldar")]))

(defpartial use-db-backup-form []
  (form-to {:class "form form-horizontal"} [:post "/db/import/"]
           [:legend "Restaurar una colección a partir de un respaldo o un archivo zip"]
           [:p.alert "Indicar el directorio donde se encuentra el respaldo o indicar la ubicación completa del archivo zip. Ejemplos: C:/respaldos/, C:/respaldos/lanina.zip"]
           [:div.control-group
            (label {:class "control-label"} :dir "Opcional: Directorio donde se encuentra el respaldo. Si no se especifica se busca el zip dump/lanina.zip en el directorio de respaldo primario.")
            [:div.controls
             (text-field :dir)]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Restaurar")]))

(defpartial import-dbf-form [collection-names]
  (form-to {:class "form form-horizontal"} [:post "/db/import/dbf/"]
           [:legend "Importar un archivo dbf a una colección del sistema"]
           [:p.alert "Indicar la ubicación completa del archivo dbf, esto agrega los registros del dbf a la colección escogida y la crea si no existe."]
           [:div.control-group
            (label {:class "control-label"} :coll "Nombre de la colección a ser modificada")
            [:div.controls
             (coll-select collection-names)]]
           [:div.control-group
            (label {:class "control-label"} :path "Dirección completa del archivo dbf")
            [:div.controls
             (text-field :path)]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Importar DBF")]))

(defpartial export-db-form [collection-names]
  (form-to {:class "form form-horizontal"} [:post "/db/export/"]
           [:legend "Descargar una colección a disco en otro formato"]
           [:div.control-group
            (label {:class "control-label"} :coll "Nombre de la colección")
            [:div.controls
             (coll-select collection-names)]]
           [:div.control-group
            (label {:class "control-label"} :format "Formato de archivo")
            [:div.controls
             [:select {:name :format}
              [:option {:value :dbf} "DBF"]
              [:option {:value :csv} "CSV"]]]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Descargar")]))

(defpage [:post "/db/export/"] {:keys [coll format]}
  (let [fname (str (name coll) "." format)]
    (dbf/mongo-coll-to-dbf! coll fname)
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

(defn restore-from-zip [zip-path]
  (if-not (re-seq #".zip$" zip-path)
    (throw (java.lang.IllegalArgumentException. "Invalid zip file path"))
    (do
      (z/extract-files zip-path (str (.getParent (io/file zip-path)) "/tempdir/"))
      (list @(exec/sh ["mongorestore" (str (.getParent (io/file zip-path)) "/tempdir/lanina/")])))))

(defpage [:post "/db/import/"] {:keys [dir]}
  (let [dir (if (seq dir) dir (str (io/file (:primary (model/get-backup-settings)) "dump" "lanina.zip")))
        results  (if (re-find #".zip$" dir)
                   (restore-from-zip dir)
                   (list @(exec/sh ["mongorestore" dir])))]
    (system-messages results)
    (when (re-find #".zip$" dir)
      (delete-file-recursively (str (.getParent (io/file dir)) "/tempdir")))
    (resp/redirect "/respaldos/")))

(defpage [:post "/db/import/dbf/"] {:keys [coll path]}
  (try (dbf/dbf-to-mongo! coll path)
       (session/flash-put! :messages (list {:type "alert-success" :text (str "Se agregaron exitosamente los registros a la colección " (name coll))}))
       (catch Exception e
         (session/flash-put! :messages (list {:type "alert-error" :text (str "No se pudieron agregar los registros, verifique la dirección del archivo")}))))
  (resp/redirect "/respaldos/"))

(defn backup-multiple-colls-seq
  "Creates a lazy-seq of commands to backup each coll in coll-names
on each of the directories.
dorun or do something similar to the seq to actually back them up."
  [[primary secondary dir] coll-names]
  (for [d [primary secondary dir] c coll-names :when (seq d)]
    (try @(exec/sh ["mongodump" "--collection" (name c) "--db" "lanina"] {:dir d})
         (catch java.io.IOException e
           {:exit 1
            :error (str e)}))))

(defn fix-target-fname [target]
  (if (re-find #".zip\s*$" target)
    (s/trim target)
    (str (s/trim target) ".zip")))

(defn zip-backup-dir [dir]
  (z/compress-files [dir] (str (.getParent (io/file dir)) "/lanina.zip"))
  (when (re-seq #"/dump/" dir)
    (delete-file-recursively dir)))

(defpage [:post "/db/backup/"] {:keys [dir coll zip]}
  (let [{:keys [primary secondary]} (model/get-backup-settings)
        results (if (= "true" zip)
                  (do (dorun (backup-multiple-colls-seq [primary secondary dir]
                                                        (model/get-collection-names)))
                      (doseq [d [primary secondary dir] :when (seq d)]
                        (zip-backup-dir (str (io/file d "dump" "lanina"))))
                      (list {:exit 0
                             :out "Archivos zip creados exitosamente."}))
                  (map (fn [d]
                         (if (seq d)
                           (try
                             @(exec/sh ["mongodump" "--collection" (name coll) "--db" "lanina"] {:dir d})
                             (catch java.io.IOException e
                               {:exit 1
                                :error (str e)}))
                           @(exec/sh ["mongodump" "--collection" (name coll) "--db" "lanina"])))
                       [primary secondary dir]))]
    (system-messages results)
    (resp/redirect "/respaldos/")))

(defpage [:post "/ajustes/respaldos/"] {:keys [amount unit start primary secondary]}
  (let [amount ((coerce-to Integer 12) amount)
        m {:amount amount :unit unit :primary primary :secondary secondary :start start}]
    (model/adjust-backup-settings m)
    (session/flash-put! :messages (list {:type "alert-success" :text (str "Se modificaron exitosamente las condiciones de respaldo")}))
    (resp/redirect "/ajustes/")))

(defpage "/respaldos/" []
  (let [content {:content [:div.container-fluid
                           (backup-db-form (model/get-collection-names))
                           (use-db-backup-form)
                           (export-db-form (model/get-collection-names))
                           (import-dbf-form (model/get-collection-names))]
                 :title "Respaldos de la base de datos"
                 :active "Herramientas"
                 :nav-bar true}]
    (home-layout content)))
