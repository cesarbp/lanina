(ns lanina.utils
  (:import [java.io File])
  (:require [clojure.java.io :as io]))

(defn coerce-to
  ([jclass] (coerce-to jclass nil))
  ([jclass default]
     (fn [arg]
       (try (eval `(new ~jclass ~arg))
            (catch Exception e default)))))

(defn slurp-binary-file [^File file]
  (io! (with-open [reader (io/input-stream file)]
         (let [buffer (byte-array (.length file))]
           (.read reader buffer)
           buffer))))

(defn delete-file-recursively
  "Delete file f. If it's a directory, recursively delete all its contents.
Raise an exception if any deletion fails unless silently is true."
  [f & [silently]]
  (let [f (io/file f)]
    (if (.isDirectory f)
      (doseq [child (.listFiles f)]
        (delete-file-recursively child silently)))
    (io/delete-file f silently)))
