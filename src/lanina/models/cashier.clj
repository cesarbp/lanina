(ns lanina.models.cashier
  (:use somnium.congomongo
        [lanina.views.utils :only [now now-hour]]))

;;; Kinda inneficient but not used very often

(def cashier-coll :cashier)
(def props #{:date :type :cash :time})
(def types #{"close" "open"})

(defn setup!
  []
  (when (collection-exists? cashier-coll)
    (println "Deleting coll" cashier-coll)
    (drop-coll! cashier-coll))
  (println "Creating coll" cashier-coll)
  (create-collection! cashier-coll))

(defn get-todays-latest
  []
  (first
   (fetch cashier-coll
          :where {:date (now)}
          :sort {:time -1})))

(defn cashier-is-open?
  []
  (let [m (get-todays-latest)]
    (= "open" (:type m))))

(defn new-cashier-map
  [t amt]
  {:date (now)
   :time (now-hour)
   :cash amt
   :type t})

(defn open-cashier
  [amt]
  (when-not (cashier-is-open?)
    (let [m (new-cashier-map "open" amt)]
      (when (and (number? amt) (<= 0 amt))
        (insert! cashier-coll m)
        :success))))

(defn close-cashier
  []
  (when (cashier-is-open?)
    (let [opn (get-todays-latest)
          m (new-cashier-map "close" (:cash opn))]
      (insert! cashier-coll m)
      :success)))

(defn get-current-cash
  []
  (when (cashier-is-open?)
    (:cash (get-todays-latest))))

(defn add-money
  [amt]
  (when (and (cashier-is-open?)
             (number? amt)
             (pos? amt))
    (let [m (get-todays-latest)
          new-m (assoc m :cash (+ (:cash m) amt))]
      (update! cashier-coll m new-m)
      :success)))

(defn withdraw-money
  [amt]
  (let [m (get-todays-latest)]
    (when (and (cashier-is-open?)
               (number? amt)
               (pos? amt)
               (<= amt (:cash m)))
      (update! cashier-coll
               m
               (assoc m :cash (- (:cash m) amt)))
      :success)))
