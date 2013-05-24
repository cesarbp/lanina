(ns lanina.server
  (:gen-class)
  (:use [somnium.congomongo :only [collection-exists? fetch]]
        [lanina.views.setup :only [initialize!]]
        [lanina.views.utils :only [now]])
  (:require [noir.server :as server]
            [clj-commons-exec :as exec]
            [lanina.models.utils :as db]
            [lanina.models.db-backup :as db-backup]))

(server/load-views-ns 'lanina.views)

(def mongo-dir "MongoDB/bin/")

(defn backup-db
  []
  (when (db-backup/need-backup?)
    (try @(exec/sh [(str mongo-dir "mongodump" "--db" "lanina" "--out" (str "respaldos/" (now)))])
         (db-backup/update-backup!)
         (catch Exception e))))

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8080"))]
    (println "Conectando a la base de datos")
    (db/maybe-init)
    (when-not (and (collection-exists? :articles) (< 0 (count (fetch :articles))))
      (println "Inicializando base de datos.")
      (initialize!))
    (when (and (collection-exists? :articles)
               (collection-exists? :purchases)
               (collection-exists? :db-backups)
               (collection-exists? :settings))
      (backup-db))
    (server/start port {:mode mode
                        :ns 'lanina})))
