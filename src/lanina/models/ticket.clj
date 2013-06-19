(ns lanina.models.ticket
  (:use
   somnium.congomongo)
  (:require [lanina.models.utils :as db]
            [lanina.views.utils :as t]
            [lanina.utils :as utils]
            [clojure-csv.core :as csv]
            [lanina.models.article :as article :only [get-by-name]]))

(def ticket-coll :tickets)

(def individual-article-props [:art_id :quantity :nom_art :precio_venta :iva :codigo])
(def ticket-props [:ticket-number :folio :date :time :articles :pay :total])

(defn get-next-ticket-number []
  (if-let [tn (-> (fetch ticket-coll :where {:date (t/now)}
                         :only [:ticket-number] :sort {:ticket-number -1})
                  first
                  :ticket-number)]
    (inc tn)
    1))

(defn get-next-folio []
  (if-let [f (:folio (first (fetch ticket-coll :only [:folio] :sort {:folio -1})))]
    (inc f)
    1))

(defn fix-articles
  [articles]
  (for [a articles]
    {:art_id (:_id a) :quantity (:quantity a) :nom_art (:nom_art a) :iva (:iva a)
     :precio_venta (:precio_venta a) :codigo (:codigo a) :total (* (:precio_venta a) (:quantity a))}))

(defn insert-ticket [ticketn total pay articles date]
  (let [number (get-next-ticket-number)
        folio (get-next-folio)
        time (t/now-hour)]
    ;; Why ?
    (when (= ticketn number)
      (do (insert! ticket-coll
                   {:ticket-number number :total total :time time :date date :folio folio :articles (fix-articles articles) :pay pay})
          :success))))

(defn search-by-date [date]
  (when (t/valid-date? date)
    (fetch ticket-coll :where {:date date} :sort {:time 1})))

(defn search-by-folio [folio]
  (fetch ticket-coll :where {:folio folio} :sort {:date 1 :time 1}))

(defn get-by-folio [folio]
  (fetch-one ticket-coll :where {:folio folio}))

(defn search-by-date-range
  ([from] (when (t/valid-date? from)
            (fetch ticket-coll :where {:date {:$gte from}} :sort {:$date 1 :time 1})))
  ([from to] (when (and (t/valid-date? from) (t/valid-date? to))
               (fetch ticket-coll :where {:date {:$gte from :$lte to}} :sort {:$date 1 :time 1}))))

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

(defn fix-row
  [[nom_art quantity precio importe total pago ticketn date time folio iva]]
  (let [quantity ((utils/coerce-to Long 0) quantity)
        precio ((utils/coerce-to Double 0.0) precio)
        importe ((utils/coerce-to Double 0.0) importe)
        total ((utils/coerce-to Double 0.0) total)
        pago ((utils/coerce-to Double 0.0) pago)
        ticketn ((utils/coerce-to Long 0) ticketn)
        date (t/fix-date date)
        folio ((utils/coerce-to Long 0) folio)
        iva ((utils/coerce-to Double 0.0) iva)
        art (article/get-by-name nom_art)
        art-id (if (seq art) (:_id art) nom_art)
        codigo (if (seq art) (:codigo art) "0")]
    {:art_id art-id :quantity quantity :nom_art nom_art :precio_venta precio
     :iva iva :codigo codigo :ticket-number ticketn :folio folio :date date :time time
     :pay pago :total total}))

(defn compress-rows
  "Same ticketn folio date time pay on all the rows"
  [rs]
  (let [{:keys [ticket-number folio date time pay total]} (first rs)]
    {:ticket-number ticket-number :folio folio :date date :time time :pay pay
     :total (reduce + (map :total rs))
     :articles (mapv (fn [r] (dissoc r :ticket-number :folio :date :time :pay))
                     rs)}))

(defn import-from-csv!
  [fpath]
  (let [ls (csv/parse-csv (slurp fpath))
        _ (first ls)
        full (atom [])
        tmp (atom [])
        add-rows (fn [fixed-rows]
                   (swap! full into
                          (compress-rows fixed-rows)))]
    (doseq [r (rest ls)]
      (swap! tmp conj r)
      (when-not (empty (nth r 8))
        (let [total (nth r 4)
              pay   (nth r 5)
              time  (nth r 8)
              fixed-rows
              (for [rr @tmp]
                (-> rr
                    (assoc 4 total)
                    (assoc 5 pay)
                    (assoc 8 time)
                    (fix-row)))]
          (insert! ticket-coll (compress-rows fixed-rows))
          (reset! tmp []))))))

(def db-file "install/VENTAS.csv")

(defn setup!
  []
  (when (collection-exists? ticket-coll)
    (println "Dropping coll" ticket-coll)
    (drop-coll! ticket-coll))
  (println "Creating coll" ticket-coll)
  (create-collection! ticket-coll)
  (import-from-csv! db-file))
