(ns lanina.views.db-backup
  "Views to backup and restore the database"
  (:use noir.core
        lanina.views.common
        hiccup.form)
  (:require [lanina.models.db-backup :as backup]
            [clojure.string :refer [trim]]
            [lanina.views.utils :refer [flash-message]]
            [noir.response :refer [redirect]]))

(defpage [:post "/db/import/"] {:keys [p]}
  (let [p (trim p)
        ans (if (seq p)
              (backup/restore-from-zip! p)
              (backup/restore-from-zip!))]
    (if ans
      (flash-message "Restauración exitosa" "success")
      (flash-message "No se pudo restaurar tal vez el archivo zip no exista" "error"))
    (redirect "/respaldos/")))

(defpage [:post "/db/backup/"] {:keys [dest]}
  (let [d (trim dest)
        ans (if (seq d)
              (backup/backup-db! d)
              (backup/backup-db!))
        r (:resp ans)]
    (if (= :success r)
      (flash-message "Respaldo exitoso" "success")
      (flash-message (str "No se pudo realizar el respaldo. Razón: " (:error-message ans))
                     "error"))
    (redirect "/respaldos/")))

(defpartial restore-db-form []
  (form-to {:class "form form-horizontal"} [:post "/db/import/"]
           [:legend "Restaurar la base de datos a partir de un respaldo zip"]
           [:div.control-group
            (label {:class "control-label"} :p "Opcional: Dirección del archivo zip. Ejemplo: D:\\lanina\\respaldos\\2013-08-21.zip Si la dirección no se especifica, se utiliza el último respaldo en el directorio de respaldos.")
            [:div.controls
             (text-field :p)]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Restaurar")]))

(defpartial backup-db-form []
  (form-to {:class "form form-horizontal"} [:post "/db/backup/"]
           [:legend "Respaldar la base de datos en otro directorio"]
           [:p.alert "Respaldar a una USB o a otro directorio."]
           [:div.control-group
            (label {:class "control-label"} :dest "Dirección del directorio a donde se desee crear el respaldo. Por lo general una memoria USB. ESTE DIRECTORIO DEBE DE EXISTIR. Ejemplo: G:\\respaldos")
            [:div.controls
             (text-field :dest)]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Respaldar")]))

(defpage "/respaldos/" []
  (let [content {:content [:div.container-fluid
                           (backup-db-form)
                           (restore-db-form)]
                 :title "Respaldos y restauración"
                 :active "Herramientas"
                 :nav-bar true}]
    (home-layout content)))
