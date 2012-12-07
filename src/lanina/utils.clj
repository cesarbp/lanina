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