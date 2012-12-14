(ns lanina.views.utils
  (:require [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.string :as str]
            [noir.session :as session]))

;;; Fixme: adjust offset for daytime saving shenanigans

(defn now []
  (let [time (t/to-time-zone (t/now) (t/time-zone-for-offset -6))]
    (str (t/day time) "/" (t/month time) "/" (t/year time))))

(defn now-with-time []
  (let [time (t/to-time-zone (t/now) (t/time-zone-for-offset -6))]
    (str (t/day time) "/" (t/month time) "/" (t/year time) "|" (t/hour time) ":" (t/minute time) ":" (t/sec time))))

;;; Fixme - this function is repeated in tickets
(defn fix-date [date]
  (let [date (apply str (take-while #(not= \| %) date))]
    (if (some #{\/ \-} (take 4 date))
      (clojure.string/replace date #"-" "/")
      (clojure.string/join "/" (reverse (clojure.string/split (clojure.string/replace date
                                                                                      #"-" "/") #"/"))))))
(defn valid-date? [date]
  (try (not (nil? (tf/parse (tf/formatter "dd/MM/yyyy") (fix-date date))))
       (catch Exception e false)))

;;; Date is expected to be in format "dd/mm/yyyy" or "dd-mm-yyyy" or with year and day in reverse order
(defn days-from-now [d]
  (let [d (tf/parse (tf/formatter "dd/MM/yyyy") (fix-date d))]
    (int (/ (t/mins-ago d) 60 24))))

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

(defn flash-message [content type]
  "Type should be one of \"error\" \"success\" \"info\""
  (session/flash-put! :messages (list {:type (str "alert-" type) :text content})))