(ns lanina.server
  (:gen-class)
  (:use [somnium.congomongo :only [collection-exists? fetch]]
        [lanina.views.setup :only [initialize!]]
        [lanina.views.utils :only [now]])
  (:require [noir.server :as server]
            [lanina.models.utils :as db]
            [lanina.models.db-backup :as db-backup])
  (:import [java.util Locale]))

(server/load-views-ns 'lanina.views)

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8080"))]
    (Locale/setDefault Locale/US)
    (println "Conectando a la base de datos")
    (db/maybe-init)
    (when-not (and (collection-exists? :articles) (< 0 (count (fetch :articles))))
      (println "No se ha encontrado base de datos.")
      (if (db-backup/there-are-backups?)
        (db-backup/restore-from-automatic!)
        (do
          (println "Instalando base de datos.")
          (initialize!))))
    (when (and (collection-exists? db-backup/db-backup-coll)
               (db-backup/need-backup?))
      (db-backup/backup-db!))
    (server/start port {:mode mode
                        :ns 'lanina})))
