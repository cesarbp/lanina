(ns lanina.models.adjustments
  (:use somnium.congomongo)
  (:require [lanina.models.article :as article]
            [lanina.views.utils :as utils]
            [lanina.models.utils :as db]))

(def globals-coll :settings)

(defn setup! []
  (when (collection-exists? globals-coll)
    (drop-coll! globals-coll)
    (println "Deleted collection " globals-coll))
  (create-collection! globals-coll)
  (insert! globals-coll {:iva 16.0 :date (utils/now) :prev []})
  (insert! globals-coll {:modify-threshold 6 :unit "months" :date (utils/now)})
  (insert! globals-coll {:image-path "resources/public/img/" :date (utils/now)}))

(defn get-image-path []
  (:image-path (fetch-one globals-coll :where {:image-path {:$ne nil}})))

(defn adjust-image-path [new-path]
  (when (db/valid-path? new-path)
    (let [old (fetch-one globals-coll :where {:image-path {:$ne nil}})
          new (db/get-updated-map old {:image-path new-path})]
      (update! globals-coll old new)
      :success)))

(defn get-current-iva []
  (:iva (fetch-one globals-coll :where {:iva {:$ne nil}} :only [:iva])))

;;; If an article hasn't been modified in a time lower than this threshold it
;;; should be shown as a database error.
;;; Returns the amount in days
(def valid-modify-threshold-units
  ["months" "days" "years"])

(def valid-modify-threshold-units-trans
  {"months" "meses"
   "days" "días"
   "years" "años"})

(defn get-modify-threshold []
  (let [doc (fetch-one globals-coll :where {:modify-threshold {:$ne nil}} :only [:modify-threshold :unit])
        unit (:unit doc) 
        factor (cond (= "months" unit) 30 (= "days" unit) 1 (= "years" unit) 365)]
    (* factor (:modify-threshold doc))))

(defn get-modify-threshold-doc []
  (fetch-one globals-coll :where {:modify-threshold {:$ne nil}}))

(defn adjust-modify-threshold [threshold unit]
  (let [original (fetch-one globals-coll :where {:modify-threshold {:$ne nil}})
        modified {:modify-threshold threshold :unit unit :date (utils/now-with-time)}
        new (db/get-updated-map original modified)]
    (update! globals-coll original new)))

(defn adjust-article-global [prop old new]
  (let [article-coll article/article-coll
        originals (fetch article-coll :where {prop old})
        update-date (utils/now-with-time)
        new-map {prop new :date update-date}]
    (doseq [orig originals]
      (update! article-coll orig (db/get-updated-map orig new-map)))))

(defn adjust-individual-article-prop [article-id prop new-value]
  (let [article-coll article/article-coll
        art (article/get-by-id-nostr article-id)
        new-map {prop new-value}
        new-art (db/get-updated-map art new-map)]
    (update! article-coll art new-art)))

(defn adjust-iva [new-iva]
  (assert (and (number? new-iva) (pos? new-iva)))
  (let [original (fetch-one globals-coll :where {:iva {:$ne nil}})
        new-iva-map {:iva new-iva :date (utils/now-with-time)}
        new (db/get-updated-map original new-iva-map)
        article-coll article/article-coll
        current-iva (get-current-iva)]
    (do
      (assert (not= new-iva original))
      (update! globals-coll original new)
      (adjust-article-global :iva current-iva new-iva))))

;;; Fixme: use the proper mongo query to remove the filter
(defn find-iva-errors []
  (let [correct-iva (get-current-iva)
        article-coll article/article-coll]
    (map (comp str :_id)
         (filter (fn [a]
                   (not= (:iva a) 0))
                 (fetch article-coll :where {:iva {:$ne correct-iva}} :only [:iva])))))

;;; Fixme: Faster to use mongo query?
(defn find-empty-value-errors [prop]
  (let [article-coll article/article-coll
        all (fetch article-coll :only [prop])]
    (map article/id-to-str (remove (fn [art] (seq (prop art))) all))))

;;; Fixme: Is it actually faster to use mongo map/reduce?
(defn find-duplicate-value-errors [prop]
  (let [article-coll article/article-coll
        all (fetch article-coll :only [prop])
        grouped-by-name
        (reduce (fn [acc next-art]
                  (update-in acc [(prop next-art)] (fn [old new] (if (vector? old) (conj old new) [new])) (str (:_id next-art))))
                {} all)]
    (filter (fn [[name ids]] (> (count ids) 1)) grouped-by-name)))

(defn find-duplicate-barcode-errors []
  (let [all-dupes (find-duplicate-value-errors :codigo)]
    (remove (fn [[bc ids]] (= "0" bc)) all-dupes)))

;;; Articles which havent been modified in a time over the threshold
(defn find-beyond-threshold-errors []
  (let [thresh (get-modify-threshold)
        all-arts (article/get-all-only [:date])]
    (map :_id
         (filter (fn [art]
                   (or (not (utils/valid-date? (:date art))) (<= thresh (utils/days-from-now (:date art)))))
                 all-arts))))

;;; Count all of the errors
(defn count-all-errors []
  (reduce + (map count [(find-iva-errors) (find-empty-value-errors :codigo) (find-empty-value-errors :nom_art) (map second (find-duplicate-barcode-errors)) (map second (find-duplicate-value-errors :nom_art)) (find-beyond-threshold-errors)])))