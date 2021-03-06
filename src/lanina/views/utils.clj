(ns lanina.views.utils
  (:use [somnium.congomongo :only [fetch-one]])
  (:require [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.string :as str]
            [noir.session :as session])
  (:import [org.joda.time DateTime]))

;;; Fixme: adjust offset for daytime saving shenanigans
(defn format-int [n]
  (when (integer? n)
    (format "%02d" n)))

(defn format-decimal [n]
  (when (number? n)
    (if (float? n)
      (format "%.02f" n)
      (format "%.02f" (double n)))))

(defn get-current-utc
  []
  (:utc-offset (fetch-one :settings :where {:utc-offset {:$ne nil}})))

(defn get-valid-utc
  []
  (:valid (fetch-one :settings :where {:utc-offset {:$ne nil}})))

(defn now []
  (let [time (DateTime.)]
    (format "%d-%02d-%02d" (t/year time) (t/month time) (t/day time))))

(defn now-with-time []
  (let [time (DateTime.)]
    (format "%d-%02d-%02dT%02d:%02d:%02d" (t/year time) (t/month time) (t/day time)
            (t/hour time) (t/minute time) (t/sec time))))

(defn now-hour []
  (let [time (DateTime.)]
    (format "%02d:%02d:%02d" (t/hour time) (t/minute time) (t/sec time))))

(defn now-year []
  (let [time (DateTime.)]
    (str (t/year time))))

(defn fix-date
  "Assumes year has 4 digits. Returns date in dd-mm-yyyy format"
  [date]
  (let [noslash (clojure.string/replace date #"/" "-")
        splt (clojure.string/split noslash #"-")]
    (if (not= 4 (count (first splt)))
      (clojure.string/join "-" (reverse splt))
      noslash)))

(defn parse-date [date]
  (try (tf/parse (tf/formatters :date-hour-minute-second) date)
       (catch Exception e
         (try (tf/parse (tf/formatters :date) date)
              (catch Exception e
                (try (tf/parse (tf/formatters :hour-minute-second) date)
                     (catch Exception e)))))))

(defn valid-date? [date]
  (when (and (string? date) (seq date))
    (not (nil? (parse-date (fix-date date))))))

;;; Date is expected to be in format "yyyy/mm/dd" or "yyyy-mm-dd" or with year and day in reverse order
(defn days-ago [date]
  (let [d (parse-date (fix-date date))]
    (int (/ (t/mins-ago d) 60 24))))

(defn date-range
  "The range is from a date n units (days/months/years) from now to now. Only date, no time."
  [f n]
  (let [now (now)
        then (t/minus (parse-date now) (f n))]
    [(format "%d-%02d-%02d" (t/year then) (t/month then) (t/day then)) now]))

(defn month-range [n]
  (date-range t/months n))

(defn day-range [n]
  (date-range t/days n))

(defn year-range [n]
  (date-range t/years n))

(defn start-of [denom date]
  (if-let [d (parse-date date)]
    (let [year (t/year d)
          month (t/month d)
          day (t/day d)]
      (cond (= :day denom) (format "%d-%02d-%02d" year month day)
            (= :month denom) (format "%d-%02d-%02d" year month 1)
            (= :year denom) (format "%d-%02d-%02d" year 1 1)))))

(defn start-of-day []
  (start-of :day (now)))

(defn start-of-month []
  (start-of :month (now)))

(defn start-of-year []
  (start-of :year (now)))

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

(defn message [content type]
  (str "<div class=alert-" type ">" content "</div>"))
