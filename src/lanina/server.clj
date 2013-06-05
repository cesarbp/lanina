(ns lanina.server
  (:gen-class)
  (:use [somnium.congomongo :only [collection-exists? fetch]]
        [lanina.views.setup :only [initialize!]]
        [lanina.views.utils :only [now]])
  (:require [noir.server :as server]
            [clj-commons-exec :as exec]
            [lanina.models.utils :as db]
            [lanina.models.db-backup :as db-backup]
            [zip.core :refer [compress-files]]
            [clojure.java.io :as io]
            [lanina.utils :refer [delete-file-recursively]]))

(server/load-views-ns 'lanina.views)

(def mongo-path "MongoDB/")

;;; TODO - zipping doesn't work properly

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8080"))]
    (println "Conectando a la base de datos")
    (db/maybe-init)
    (when-not (and (collection-exists? :articles) (< 0 (count (fetch :articles))))
      (println "Inicializando base de datos.")
      (initialize!))
    (when (and (collection-exists? db-backup/db-backup-coll)
               (db-backup/need-backup?))
      (try @(exec/sh [(str mongo-path "bin/mongodump.exe") "--db" "lanina" "--out"
                      (str "respaldos/" (now))])
           (println "Respaldo hecho")
           (db-backup/update-backup!)
           (println "Respaldo actualizado")
           (catch Exception e
             (println "Respaldo fallido" e))))
    (server/start port {:mode mode
                        :ns 'lanina})))
