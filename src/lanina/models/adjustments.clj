(ns lanina.models.adjustments
  (:use somnium.congomongo
        lanina.utils)
  (:require [lanina.views.utils :as utils]
            [lanina.models.utils :as db]))

(def globals-coll :settings)

(defn setup! []
  (when (collection-exists? globals-coll)
    (drop-coll! globals-coll)
    (println "Deleted collection " globals-coll))
  (create-collection! globals-coll)
  (insert! globals-coll {:iva [0.0 16.0]})
  (insert! globals-coll {:modify-threshold 6 :unit "months"})
  (insert! globals-coll {:image-path "/img/"})
  (insert! globals-coll {:collections [:articles :tickets :settings :article-logs :users]})
  (insert! globals-coll {:utc-offset -5 :valid [-5 -6]})
  (insert! globals-coll {:name "backups"
                         :amount 12
                         :unit "hours"
                         :start "00:00"
                         :primary ""
                         :secondary "/dev/sdb1/"}))

(defn get-backup-settings []
  (dissoc (fetch-one globals-coll :where {:name "backups"}) :date :prev))

(defn get-image-path []
  (:image-path (fetch-one globals-coll :where {:image-path {:$ne nil}})))

(defn get-collection-names []
  (:collections (fetch-one globals-coll :where {:collections {:$ne nil}})))

(defn get-valid-ivas []
  (apply sorted-set (:iva (fetch-one globals-coll :where {:iva {:$ne nil}} :only [:iva]))))

(defn valid-iva? [n]
  (if (not (number? n))
    false
    (loop [ivas (get-valid-ivas)]
      (cond (empty? ivas) false
            (== n (first ivas)) true
            :else (recur (rest ivas))))))

(defn adjust-valid-ivas
  [ivas]
  (when (every? number? ivas)
    (update! globals-coll (fetch-one globals-coll :where {:iva {:$ne nil}}) (mapv double ivas))
    :success))

;;; If an article hasn't been modified in a time lower than this threshold it
;;; should be shown as a database error.
;;; Returns the amount in days
(def valid-time-units
  ["months" "days" "years"])

(def valid-time-units-trans
  {"months" "meses"
   "days" "días"
   "years" "años"
   "hours" "horas"})

(defn get-modify-threshold []
  (let [doc (fetch-one globals-coll :where {:modify-threshold {:$ne nil}} :only [:modify-threshold :unit])
        unit (:unit doc)
        factor (cond (= "months" unit) 30 (= "days" unit) 1 (= "years" unit) 365)]
    (* factor (:modify-threshold doc))))

(defn get-modify-threshold-doc []
  (fetch-one globals-coll :where {:modify-threshold {:$ne nil}}))

(def resources-root "resources/public/")

(defn remove-first-slash-path [path]
  (clojure.string/replace path #"^/" ""))

(defn full-image-path [path]
  (str resources-root (remove-first-slash-path path)))

(defn adjust-image-path [new-path]
  (when (db/valid-path? (full-image-path new-path))
    (let [old (fetch-one globals-coll :where {:image-path {:$ne nil}})
          new {:image-path new-path}]
      (update! globals-coll old new)
      :success)))

(defn adjust-modify-threshold [threshold unit]
  (let [original (fetch-one globals-coll :where {:modify-threshold {:$ne nil}})
        modified {:modify-threshold threshold :unit unit :date (utils/now-with-time)}
        new (db/get-updated-map original modified)]
    (update! globals-coll original new)))

(defn adjust-backup-settings [backup-map]
  (let [original (fetch-one globals-coll :where {:name "backups"})
        new-backups-map (into backup-map {:date (utils/now-with-time)})
        new (db/get-updated-map original new-backups-map)]
    (update! globals-coll original new)))
