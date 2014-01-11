(ns lanina.models.logs
  (:use somnium.congomongo))

(def log-coll :article-logs)

(defn setup! []
  (when (collection-exists? log-coll)
    (drop-coll! log-coll))
  (create-collection! log-coll))

(def log-keys [:art-id :changes :date :type :cleared])
(def log-types [:deleted :updated :added])

(defn fetch-uncleared
  [art-id]
  (fetch-one log-coll :where {:art-id art-id :cleared false}))

(defn add-logs! [art-id type changes date]
  (when-not (seq (fetch-uncleared art-id))
    (insert! log-coll {:art-id art-id :type type :date date :changes changes :cleared false})))

(defn remove-log! [id]
  (destroy! log-coll {:art-id id}))

(defn retrieve-by-date [date]
  (fetch log-coll :where {:date date} :sort {:date 1}))

(defn retrieve-by-article [art-id]
  (fetch log-coll :where {:art-id art-id} :sort {:date 1}))

(defn retrieve-all []
  (fetch log-coll :sort {:date 1}))

(defn was-cleared [art-id date type]
  (let [old (fetch-one log-coll :where {:art-id art-id :date date :type type})]
    (update! log-coll old
             (into old {:cleared true}))))

(defn remove-logs []
  (drop-coll! log-coll)
  (setup!))
