(ns lanina.models.article
  (:use
   somnium.congomongo)
  (:require [lanina.models.utils :as db]
            [clojure.string :as str]))

(def article-coll :articles)

;;; Use the db

(defn get-article [barcode]
  (fetch-one article-coll :where {:codigo barcode} :only [:nom_art :codigo]))

;;; fill the db
(def db-file "src/lanina/models/db-csv/tienda.csv")

(defn csv-to-maps [f]
  "Takes a csv file and creates a map for each row of column: column-value"
  (let [ls (str/split (slurp f) #"\n")
        cs (first ls)
        rs (rest (rest ls))
        colls (map (comp keyword #(.toLowerCase %)) (str/split cs #","))
        regs (map #(str/split % #",") rs)]
    (map (fn [r]
           (zipmap colls r))
         regs)))

(defn is-int [s]
  (try (Integer/parseInt s)
       true
       (catch Exception e
         false)))

(defn is-double [s]
  (and (not (is-int s))
       (try (Double/parseDouble s)
            true
            (catch Exception e
              false))))

(defn coerce-to-useful-types [ms]
  (map (fn [m]
         (reduce (fn [acc [k v]]
                   (cond (= k :codigo) (into acc {k v})          ;Leave barcodes as strings
                         (is-int v) (into acc  {k (Integer/parseInt v)})
                         (is-double v) (into acc {k (Double/parseDouble v)})
                         :else (into acc {k v})))
                 {}
                 m))
       ms))

(defn fill-article-coll! [ms]
  "Appends the maps to the article collection"
  (db/maybe-init)
  (when-not (collection-exists? article-coll)
    (create-collection! article-coll)
    (println (str "Created collection " article-coll)))
  (doseq [m ms]
    (insert! article-coll m)))

(defn setup! []
  "Dangerous! drops articles collection if exists and creates a new one with the
csv of the articles"
  (db/maybe-init)
  (when (collection-exists? article-coll)
    (drop-coll! article-coll)
    (println (str "Deleted collection " article-coll)))
  (-> db-file
      (csv-to-maps)
      (coerce-to-useful-types)
      (fill-article-coll!)))