(ns zip.core
  "Wrapper around java.util.zip to manage zip files."
  (:use zip.utils)
  (:require [clojure.java.io :as io])
  (:import [java.util.zip ZipOutputStream ZipEntry ZipException
            ZipFile ZipInputStream]
           [java.io ByteArrayInputStream ByteArrayOutputStream]))

(import-static java.util.zip.ZipOutputStream DEFLATED STORED)

(defrecord Archive [resource entries comment]
  java.io.Closeable
  (close [this]
    (.close resource)))

(defn make-archive [& {:keys [resource entries comment] :or {:comment ""}}]
  {:pre [resource entries]}
  (Archive. resource entries comment))

;;; Creating zip files
(def compression-methods {:deflated DEFLATED :stored STORED})

(defmulti compress
  (fn [source target & opts] (type source)))

(defmethod compress clojure.lang.IPersistentCollection
  [source target & {:keys [comment method]}]
  (let [out (io/output-stream target)
        zout (ZipOutputStream. out)]
    (doto zout
      (.setLevel (get compression-methods method DEFLATED))
      (.setComment (or comment "")))
    (doseq [{:keys [filename comment inputstream directory?]} source
            :let [e (ZipEntry. (fix-dir-name filename))]
            :when (not directory?)]
      (when comment
        (.setComment e comment))
      (.putNextEntry zout e)
      (io/copy @inputstream zout))
    (try (.close zout)
         (catch ZipException err
           (.close out)
           (throw err)))))

(defmethod compress Archive
  [source target & opts]
  (apply compress (:entries source) target opts))

(defn files->entries [& files]
  (let [root (or (-> files first io/file .getParent) "")
        filelist (mapcat (comp flatten list-files io/file) files)]
    (map (partial make-relative-entry root) filelist)))

(defn compress-files [files to & opts]
  (apply compress (apply files->entries files) to opts))

;;; Decompressing zip files
(defprotocol Decompress
  (open-archive [this]))

(extend-protocol Decompress
  Archive
  (open-archive [arch] arch)

  java.lang.String
  (open-archive [s]
    (open-archive (io/file s)))

  java.io.File
  (open-archive [f]
    (let [arch (ZipFile. f)
          files (enumeration-seq (.entries arch))
          entries (for [e files]
                    {:inputstream (-> (.getInputStream arch e) io/input-stream delay)
                     :filename (.getName e)
                     :directory? (.isDirectory e)
                     :comment (.getComment e)})]
      (make-archive :resource arch :entries entries)))

  java.lang.Object
  (open-archive [obj]
    (let [zi (ZipInputStream. (io/input-stream obj))
          files (take-while identity (repeatedly #(.getNextEntry zi)))
          entries (for [e files]
                    {:inputstream (delay zi)
                     :filename (.getName e)
                     :directory? (.isDirectory e)
                     :comment (.getComment e)})]
      (make-archive :resource zi :entries entries))))

(defn extract-file [{:keys [filename inputstream directory?]} path]
  (let [f (io/file path filename)]
    (io/make-parents f)
    (if directory?
      (.mkdirs f)
      (io/copy @inputstream f))))

(defmulti extract-files
  (fn [from to] (type from)))

(defmethod extract-files
  clojure.lang.IPersistentCollection
  [arch path]
  (doseq [f arch]
    (extract-file f path)))

(defmethod extract-files
  Archive
  [arch to]
  (extract-files (:entries arch) to))

(defmethod extract-files
  String
  [from to]
  (with-open [arch (open-archive from)]
    (extract-files arch to)))
