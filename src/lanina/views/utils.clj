(ns lanina.views.utils
  (:require [clj-time.core :as t]
            [clojure.string :as str]))

;;; Fixme: adjust offset for daytime saving shenanigans

(defn now []
  (let [time (t/to-time-zone (t/now) (t/time-zone-for-offset -5))]
    (str (t/day time) "/" (t/month time) "/" (t/year time))))

(defn now-with-time []
  (let [time (t/to-time-zone (t/now) (t/time-zone-for-offset -5))]
    (str (t/day time) "/" (t/month time) "/" (t/year time) "|" (t/hour time) ":" (t/minute time) ":" (t/sec time))))

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