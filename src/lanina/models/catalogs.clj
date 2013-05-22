(ns lanina.models.catalogs
  (:use somnium.congomongo
        [lanina.utils :only [coerce-to]])
  (:require [clojure.java.io :refer [reader]]
            [clojure.string :refer [split]]
            [clojure-csv.core :as csv]))

(def catalogs-coll :catalogs)
(def props #{:type :nocata :nombre})
(def lamina-props #{:cve :mat :nombre :marca :nocata})
(def lamina-props-ordered [:cve :mat :nombre :marca :nocata])
(def verbose
  {:cve "Clave"
   :mat "Materia"
   :nombre "Nombre"
   :marca "Marca"
   :nocata "Número de catálogo"
   :type "Tipo"})

(defn add-lamina!
  [nocata cve nombre mat marca]
  (let [nocata (if (integer? nocata) nocata ((coerce-to Long) nocata))
        cve (if (integer? cve) cve ((coerce-to Long) cve))
        nombre (when (seq nombre) nombre)
        m {:type "lamina"
           :nocata nocata
           :cve cve
           :nombre nombre
           :mat mat}]
    (if-not (and nocata cve nombre)
      :error-invalid
      (if (fetch-one catalogs-coll :where m)
        :error-duplicated
        (do (insert! catalogs-coll m)
            :success)))))

(defn- coerce-row
  [row-map]
  (if (= "1" (:cve row-map))
    (println row-map))
  (reduce (fn [acc [k v]]
            (if (or (= :cve k) (= :nocata k))
              (conj acc {k ((coerce-to Long) v)})
              (conj acc {k v})))
          {:type "lamina"}
          row-map))

(defn- import-from-csv!
  [fpath]
  (let [ls (csv/parse-csv (slurp fpath))
        fields (map (comp keyword #(.toLowerCase %))
                    (first ls))]
    (reduce (fn [_ l]
              (->> l
                   (zipmap fields)
                   coerce-row
                   (insert! catalogs-coll)))
            nil
            (rest ls))))

(def db-path "resources/db/LAMINAS.csv")

(defn setup!
  []
  (when (collection-exists? catalogs-coll)
    (println "Dropping coll" catalogs-coll)
    (drop-coll! catalogs-coll))
  (println "Creating coll" catalogs-coll)
  (create-collection! catalogs-coll)
  (import-from-csv! db-path))

;;; Use the db
(defn get-all
  [type]
  (fetch catalogs-coll :where {:type type} :sort {:nombre 1}))

(defn get-all-laminas
  []
  (get-all "lamina"))
