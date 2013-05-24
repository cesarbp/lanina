(ns lanina.models.db-backup
  (:use somnium.congomongo
        [lanina.views.utils :only [now]]))

(def db-backup-coll :db-backups)

(defn setup!
  []
  (when (collection-exists? db-backup-coll)
    (println "Dropping coll" db-backup-coll)
    (drop-coll! db-backup-coll))
  (println "Creating coll" db-backup-coll)
  (create-collection! db-backup-coll)
  (insert! db-backup-coll {:last "2000-01-01"}))

(defn str-lower
  [a b]
  (> 0 (.compareTo a b)))

(defn get-last-backup
  []
  (-> (fetch-one db-backup-coll :where {:last {:$ne nil}})
      :last))

(defn get-last-backup-map
  []
  (fetch-one db-backup-coll :where {:last {:$ne nil}}))

(defn need-backup?
  []
  (-> (get-last-backup)
      (str-lower (now))))

(defn update-backup!
  []
  (let [m (get-last-backup-map)]
    (update! db-backup-coll m (assoc m :last (now)))))
