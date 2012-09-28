(ns lanina.models.ticket
  (:use
   somnium.congomongo)
  (:require [lanina.models.utils :as db]
            [clj-time.core       :as time]
            [lanina.utils        :as utils]))

(def ticket-coll :tickets)

(def individual-article-props [:type :quantity :nom_art :precio_unitario :total :codigo :cantidad])
(def ticket-props [:ticket-number :folio :date :articles :pay])

(defn get-next-ticket-number []
  (let [now (time/now)
        date (str (format "%02d" (time/day now)) "/" (format "%02d" (time/month now)) "/" (format "%02d" (time/year now)))
        numbers (map :ticket-number
                     (fetch ticket-coll :where {:date date} :only [:ticket-number]))]
    (if (seq numbers)
      (inc (first (sort-by - numbers)))
      1)))

(defn get-next-folio []
  (let [folios (map :folio
                    (fetch ticket-coll :only [:folio]))]
    (if (seq folios)
      (inc (first (sort-by - folios)))
      1)))

(defn setup! []
  (when-not (collection-exists? ticket-coll)
    (create-collection! ticket-coll)))

(defn insert-ticket [pay articles]
  (let [now (time/now)
        date (str (format "%02d" (time/day now)) "/" (format "%02d" (time/month now)) "/" (format "%02d" (time/year now)))
        number (get-next-ticket-number)
        folio (get-next-folio)]
    (insert! ticket-coll
             {:ticket-number number :date date :folio folio :articles articles :pay pay})))

(defn fix-date [date]
  (if (some #{\/ \-} (take 4 date))
    (clojure.string/replace date #"-" "/")
    (clojure.string/join "/" (reverse (clojure.string/split (clojure.string/replace date
                                                                                    #"-" "/") #"/")))))

(defn search-by-date [date]
  (let [date (fix-date date)]
    (fetch ticket-coll :where {:date date})))

(defn search-by-folio [folio]
  (fetch ticket-coll :where {:folio folio}))

(defn get-by-folio [folio]
  (fetch-one ticket-coll :where {:folio folio}))

(defn search-by-date-with-limits
  ([date from]
     (let [tickets (search-by-date date)
           from ((utils/coerce-to Long 0) from)]
       (when (seq tickets)
         (if (= 0 from)
           tickets
           (filter #(>= (:ticket-number %) from) tickets)))))
  ([date from to]
     (let [from ((utils/coerce-to Long 0) from)
           to ((utils/coerce-to Long) to)]
       (if (or (not to) (> from to))
         (search-by-date-with-limits date from)
         (let [tickets (search-by-date date)]
           (when (seq tickets)
             (filter #(and (>= (:ticket-number %) from)
                           (<= (:ticket-number %) to))
                     tickets)))))))
