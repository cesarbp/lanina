(ns lanina.models.cashier
  (:use somnium.congomongo
        [lanina.views.utils :only [now now-hour]]))

;;; Kinda inneficient but not used very often

(def cashier-coll :cashier)
(def props #{:date :type :cash :time})
(def types #{"close" "open"})

(def cashier-flow-coll :cashier-flow)
(def cashier-flow-types #{"APERTURA" "CIERRE" "DEPOSITO" "RETIRO" "VENTA"})
(def cashier-flow-props #{:date :type :amount :time})
(defn setup!
  []
  (when (collection-exists? cashier-coll)
    (println "Deleting coll" cashier-coll)
    (drop-coll! cashier-coll)
    (when (collection-exists? cashier-flow-coll)
      (println "Deleting coll" cashier-flow-coll)
      (drop-coll! cashier-flow-coll)))
  (println "Creating coll" cashier-coll)
  (create-collection! cashier-coll)
  (println "Creating coll" cashier-flow-coll)
  (create-collection! cashier-flow-coll))

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

(defn save-flow
  [t amount]
  (when (cashier-is-open?)
    (let [date (now)
          time (now-hour)]
      (insert! cashier-flow-coll
               {:date date :type t
                :amount amount :time time}))))

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
        (save-flow "APERTURA" amt)
        :success))))

(defn close-cashier
  []
  (when (cashier-is-open?)
    (let [opn (get-todays-latest)
          m (new-cashier-map "close" (:cash opn))]
      (save-flow "CIERRE" (:cash opn))
      (update! cashier-coll opn (assoc opn :type "close"))
      :success)))

(defn get-flows
  [date]
  (fetch cashier-flow-coll
         :where {:date date}
         :sort {:time 1}))

(defn get-current-cash
  []
  (when (cashier-is-open?)
    (:cash (get-todays-latest))))

(defn add-money!
  [amt & [t]]
  (let [t (or t "VENTA")]
    (when (and (cashier-is-open?)
               (number? amt)
               (pos? amt))
      (let [m (get-todays-latest)
            new-m (assoc m :cash (+ (:cash m) amt))]
        (update! cashier-coll m new-m)
        (save-flow t amt)
        :success))))

(defn withdraw-money!
  [amt]
  (let [m (get-todays-latest)]
    (when (and (cashier-is-open?)
               (number? amt)
               (pos? amt)
               (<= amt (:cash m)))
      (update! cashier-coll
               m
               (assoc m :cash (- (:cash m) amt)))
      (save-flow "withdraw" amt)
      :success)))
