(ns lanina.models.utils
  (:use
   somnium.congomongo
   [somnium.congomongo.config :only [*mongo-config*]])
  (:require [clojure.data.json :as json]))

(defn split-mongo-url [url]
  "Parses mongodb url from heroku, like mongodb://user:pass@localhost:1234/db"
  (let [matcher (re-matcher #"^.*://(.*?):(.*?)@(.*?):(\d+)/(.*)$" url)] ;; Setup the regex.
    (when (.find matcher) ;; Check if it matches.
      (zipmap [:match :user :pass :host :port :db] (re-groups matcher)))))

(defn maybe-init []
  "Checks if connection and collection exist, otherwise initialize."
  (when (not (connection? *mongo-config*))
    (let [mongo-url (or (get (System/getenv) "MONGOHQ_URL") ;; Heroku location
                        "mongodb://admin:admin@localhost:27017/lanina") ;; Local db
          config (split-mongo-url mongo-url)]
      (println "Initializing mongo @ " mongo-url)
      (mongo! :db (:db config) :host (:host config) :port (Integer. (:port config)))
      (authenticate (:user config) (:pass config))
      (when-not (collection-exists? :users)
          (create-collection! :users)))))

;;; Extend json
(defn- write-json-mongodb-objectid [x out escape-unicode?]
  (json/write-json (str x) out escape-unicode?))

(extend org.bson.types.ObjectId json/Write-JSON
  {:write-json write-json-mongodb-objectid})

(defn valid-id? [s]
  (try (object-id s)
       true
       (catch IllegalArgumentException e
         false)))

(defn get-updated-map
  "Takes an old map and a new version of it and puts the old one inside the :prev of the returned map
the maps should be article maps"
  [old new]
  (let [new (dissoc new :_id :prev)
        prev-version (dissoc old :_id :prev)
        prev-prev (:prev old)]
    (if-not prev-prev
      new
      (let [new-prev (conj prev-prev prev-version)]
        (merge new {:prev new-prev})))))

(defn valid-path? [s]
  (.isDirectory (java.io.File. s)))
