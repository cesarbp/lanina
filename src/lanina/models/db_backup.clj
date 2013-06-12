(ns lanina.models.db-backup
  (:use somnium.congomongo)
  (:require [lanina.views.utils :refer [now valid-date?]]
            [clojure.java.io :as io]
            [clj-commons-exec :refer [sh]]
            [lanina.utils :refer [delete-file-recursively]]
            [zip.core :refer [compress-files extract-files]]
            [clojure.string :as s]))

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

(defn fix-path
  [p]
  (condp = (type p)
    String (-> p io/file str (s/replace #"\\" "/"))
    java.io.File (-> p str (s/replace #"\\" "/"))
    nil))

(defn file-exists?
  [f]
  (.exists (io/file f)))

(defn list-dir
  [d]
  (when (file-exists? d)
    (->> d io/file file-seq (map fix-path) rest)))

(declare restore-from-zip!)

(def mongo-path "MongoDB/")
(def mongo-data-path "MongoDB/data/")
(def mongodump-path "MongoDB/bin/mongodump.exe")
(def mongorestore-path "MongoDB/bin/mongorestore.exe")
(def backups-dir "respaldos/")

(defn there-are-backups?
  []
  (->> backups-dir list-dir (filter #(re-seq #"\.zip$" %)) seq))

(defn restore-from-automatic!
  "Restore from the latest backup found in respaldos/"
  []
  (let [backups (-> (list-dir "respaldos/") sort reverse)
        invalid-date? (complement valid-date?)
        latest (first (drop-while #(->> % (re-seq #"^respaldos/(.+)\.zip$") first second invalid-date?)
                                  backups))]
    (when latest
      (restore-from-zip! latest))))

(defn restore-from-zip!
  ([] (restore-from-automatic!))
  ([zip-path]
     (let [zip-path (fix-path zip-path)
           extract-dir (str backups-dir "temp/")
           zip-name (->> zip-path (re-seq #"/([^/]+).zip$") first second)
           dump-dir (str extract-dir zip-name "/lanina/")]
       (try
         (println "Restaurando base de datos de" zip-path)
         (extract-files zip-path extract-dir)
         (println
          @(sh [mongorestore-path
                "--db" "lanina"
                dump-dir]))
         (delete-file-recursively extract-dir)
         (println "Restauracion exitosa")
         true
         (catch Exception e
           (println "La restauracion ha fallado" e)
           false)))))

(defn zip-and-delete!
  "Zips a directory and deletes the directory. The zip is placed
inside the directory's parent."
  [d zname]
  (let [zname (if (re-seq #"\.zip$" zname) zname (str zname ".zip"))
        parent (-> d io/file .getParent)
        zip-path (str parent "/" zname)]
    (compress-files [d] zip-path)
    (delete-file-recursively d)))

(defn backup-response
  "resp is one of :error :success"
  [& {:keys [resp error-message]}]
  (let [m {:resp resp}]
    (if error-message
      (assoc m :error-message error-message)
      m)))

(defn backup-db!
  "Uses mongodump, creates a zip and deletes the files created by
mongodump. The file is the date when the zip is created. If that zip
already exists it fails."
  ([] (backup-db! backups-dir))
  ([target-dir]
     (let [date (now)
           out (str (fix-path target-dir) "/" date)
           zip-path (str out ".zip")]
       (if (file-exists? zip-path)
         (backup-response :resp :error :error-message "El archivo ya existe.")
         (try @(sh [mongodump-path
                    "--db" "lanina"
                    "--out" out])
              (zip-and-delete! out date)
              (println "Respaldo hecho")
              (when (= backups-dir target-dir)
                (update-backup!)
                (println "Respaldo actualizado"))
              (backup-response :resp :success)
              (catch Exception e
                (println "Respaldo fallido:" e)
                (backup-response :resp :error :error-message (str "Respaldo fallido: " e))))))))
