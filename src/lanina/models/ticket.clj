(ns lanina.models.ticket
  (:use
   somnium.congomongo)
  (:require [lanina.models.utils :as db]
            [lanina.views.utils :as t]
            [lanina.utils :as utils]))

(def ticket-coll :tickets)

(def individual-article-props [:art_id :quantity :nom_art :precio_venta :iva :codigo])
(def ticket-props [:ticket-number :folio :date :time :articles :pay])

(defn get-next-ticket-number []
  (let [date (t/now)
        n (:ticket-number
           (first
            (fetch ticket-coll :where {:date date} :only [:ticket-number] :sort {:ticket-number -1})))]
    (if n (inc n) 1)))

(defn get-next-folio []
  (if-let [f (:folio (first (fetch ticket-coll :only [:folio] :sort {:folio -1})))]
    (inc f)
    1))

(defn setup! []
  (when (collection-exists? ticket-coll)
    (drop-coll! ticket-coll))
  (create-collection! ticket-coll))

(defn fix-articles
  [articles]
  (for [a articles]
    {:art_id (:_id a) :quantity (:quantity a) :nom_art (:nom_art a) :iva (:iva a)
     :precio_venta (:precio_venta a) :codigo (:codigo a) :total (* (:precio_venta a) (:quantity a))}))

(defn insert-ticket [pay articles]
  (let [date (t/now)
        time (t/now-hour)
        number (get-next-ticket-number)
        folio (get-next-folio)]
    (insert! ticket-coll
             {:ticket-number number :time time :date date :folio folio :articles (fix-articles articles) :pay pay})))

(defn search-by-date [date]
  (when (t/valid-date? date)
    (fetch ticket-coll :where {:date date} :sort {:time 1})))

(defn search-by-folio [folio]
  (fetch ticket-coll :where {:folio folio} :sort {:date 1 :time 1}))

(defn get-by-folio [folio]
  (fetch-one ticket-coll :where {:folio folio}))

(defn search-by-date-range
  ([from] (when (t/valid-date? from)
            (fetch ticket-coll :where {:date {:$gte from}} :sort {:$natural 1})))
  ([from to] (when (and (t/valid-date? from) (t/valid-date? to))
               (fetch ticket-coll :where {:date {:$gte from :$lte to}} :sort {:$natural 1}))))

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
