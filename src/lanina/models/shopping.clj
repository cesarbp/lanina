(ns lanina.models.shopping
  (:use somnium.congomongo
        [lanina.views.utils :only [now valid-date? fix-date]]
        [lanina.utils :only [coerce-to]])
  (:require [lanina.models.utils :as db]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [lanina.models.article :as article]
            [clojure-csv.core :as csv]))

(def purchases-coll :purchases)

(def props #{:date :total :arts})
(def art-props #{:bimporte :biva :bpre :bu :blin :bcant :bcodi :bprov :bcj :barti :bramo})
(def props-ordered [:date :bcodi :barti :bpre :iva :bcj :bu :bcant :bimporte :blin :bramo :bprov])
(def verbose
  {:bcant "Cantidad"
   :barti "Nombre de artículo"
   :bpre "Presentación"
   :bcj "Costo por caja"
   :bu "Costo unitario"
   :blin "Línea"
   :bramo "Ramo"
   :bimporte "Importe"
   :biva "IVA"
   :bprov "Proveedor"
   :bcodi "Código de barras"
   :date "Fecha de compra"
   :total "Total"})

(defn- coerce-row
  [row-map]
  (let [m (atom {})
        add-prop (fn [k v] (swap! m conj {k v}))]
    (doseq [[k v] row-map :when (art-props k)]
      (cond (or (= :bcant k) (= :bpre k))
            (add-prop k ((coerce-to Long 0) v))
            (or (= :bimporte k) (= :biva k))
            (add-prop k ((coerce-to Double 0.0) v))
            :else (add-prop k v)))
    (add-prop :bcj ((coerce-to Double 0.0)
                    ((if (== 16 (:biva @m))
                       :bcj_con
                       :bcj_sin)
                     row-map)))
    (add-prop :bu ((coerce-to Double 0.0)
                   ((if (== 16 (:biva @m))
                      :bu_con
                      :bu_sin)
                    row-map)))
    @m))

(defn- maps-from-csv
  [fpath]
  (let [ls (csv/parse-csv (slurp fpath))
        fields (map (comp keyword #(.toLowerCase %))
                    (first ls))]
    (reduce (fn [acc l]
              (conj acc
                    (->> l
                         (zipmap fields)
                         coerce-row)))
            []
            (rest ls))))

(defn- import-from-csv!
  [fpath]
  (let [ms (maps-from-csv fpath)
        date "2013-05-06"
        total (reduce + (map :bimporte ms))]
    (insert! purchases-coll {:total total :date date :arts ms})))

(def db-file "resources/db/COMPRAS.csv")

(defn setup!
  []
  (when (collection-exists? purchases-coll)
    (println "Deleting colletion" purchases-coll)
    (drop-coll! purchases-coll))
  (println "Creating collection" purchases-coll)
  (create-collection! purchases-coll)
  (import-from-csv! db-file))
