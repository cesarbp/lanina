(ns lanina.models.logs
  (:use somnium.congomongo))

(def log-coll :article-logs)

(defn setup! []
  (when-not (collection-exists? log-coll)
    (create-collection! log-coll)))

(def log-keys [:art-id :changes :date :type])
(def log-types [:deleted :updated :added])

(defn add-logs! [art-id type changes date]
  (insert! log-coll {:art-id art-id :type type :date date :changes changes}))

(defn retrieve-by-date [date]
  fetch log-coll :where {:date date} :sort {:date 1})

(defn retrieve-by-article [art-id]
  (fetch log-coll :where {:art-id art-id} :sort {:date 1}))

(defn retrieve-all []
  (fetch log-coll :sort {:date 1}))