(ns lanina.server
  (:gen-class)
  (:require [noir.server :as server]
            [clj-commons-exec :as exec]))

(server/load-views-ns 'lanina.views)

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8080"))]
    (server/start port {:mode mode
                        :ns 'lanina})))
