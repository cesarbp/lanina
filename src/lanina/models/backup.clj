;;; Backup the contents of a page like in a shopping list or sales list so the user
;;; can go back to it after closing it

(ns lanina.models.backup
  (:use somnium.congomongo))

(def backup-coll :backups)

(defn setup! []
  (when (collection-exists? backup-coll)
    (drop-coll! backup-coll))
  (create-collection! backup-coll))

;;; :type must be :list or :sale
(def props [:number :html :type])

(defn get-next-number [type]
  (inc (or (:number (first (fetch backup-coll :where {:type type} :sort {:number -1})))
           0)))

(defn add-backup [html type]
  (let [nextn (get-next-number type)]
    (insert! backup-coll {:number nextn :type type :html html})))

(defn clear-all [type]
  (destroy! backup-coll {:type type}))

(defn get-latest [type]
  (first (fetch backup-coll :where {:type type} :sort {:number -1})))
