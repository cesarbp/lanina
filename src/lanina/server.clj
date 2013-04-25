(ns lanina.server
  (:gen-class)
  (:use [somnium.congomongo :only [collection-exists? fetch]]
        [lanina.views.setup :only [initialize!]])
  (:require [noir.server :as server]
            [clj-commons-exec :as exec]
            [lanina.models.utils :as db]))

(server/load-views-ns 'lanina.views)

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8080"))]
    (println "Conectando a la base de datos")
    (db/maybe-init)
    (when-not (and (collection-exists? :articles) (< 0 (count (fetch :articles))))
      (println "Inicializando base de datos.")
      (initialize!))
    (server/start port {:mode mode
                        :ns 'lanina})))
