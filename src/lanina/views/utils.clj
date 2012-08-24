(ns lanina.views.utils
  (:require [clj-time.core :as t]
            [clojure.string :as str]))

(defn now []
  (let [time (t/now)]
    (str (t/day time) "/" (t/month time) "/" (t/year time))))

(defn url-encode
  [s]
  (let [unreserved #"[^A-Za-z0-9_~.+-]+"
        s (if (keyword? s) (name s) (str s))]
    (str/replace s unreserved
                 (fn [c]
                   (str/join
                    (map (partial format "%%%02X")
                         (.getBytes c "UTF-8")))))))

(defn- double-escape [^String x]
  (.replace x "\\" "\\\\"))

(defn- parse-bytes [encoded-bytes]
  (->> (re-seq #"%.." encoded-bytes)
       (map #(subs % 1))
       (map #(.byteValue (Integer/parseInt % 16)))
       (byte-array)))

(defn url-decode
  [s]
  (str/replace s
               #"(?:%..)+"
               (fn [chars]
                 (-> (parse-bytes chars)
                     (String. "UTF-8")
                     (double-escape)))))