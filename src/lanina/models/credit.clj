(ns lanina.models.credit
  "Model for sales by credit"
  (:use somnium.congomongo)
  (:require [lanina.views.utils :refer [now]]))

(def credit-coll :credit)

(def credit-fields #{:r :c :name :payments :article :price :date :free})

(def verbose {:payments "Pagos"
              :name "Nombre"
              :article "Artículo"
              :price "Precio"
              :date "Fecha de inicio"})

(defn new-credit
  [[r c] name article price]
  {:r r
   :c c
   :name name
   :article article
   :price price
   :payments []
   :date nil})

(defn setup!
  [r c]
  (when (collection-exists? credit-coll)
    (println "Removing collection" credit-coll)
    (drop-coll! credit-coll))
  (println "Creating collection" credit-coll)
  (create-collection! credit-coll)
  (insert! credit-coll {:n "setup" :rows r :columns c})
  (doseq [i (range r) c (range c)]
    (insert! credit-coll (new-credit [i c] nil nil nil)))
  true)

(defn get-rc
  []
  (let [m (fetch-one credit-coll :where {:n "setup"})]
    [(:rows m) (:columns m)]))

(defn get-credit
  ([[r c]] (get-credit r c))
  ([r c]
     (fetch-one credit-coll :where {:r r :c c})))

(defn available?
  ([r c] (available? [r c]))
  ([l] (nil? (-> l get-credit :name seq))))

(defn get-table
  []
  (let [[r c] (get-rc)]
    (vec (for [i (range r)]
           (vec (for [j (range c)]
                  (get-credit i j)))))))

(defn installed?
  []
  (collection-exists? credit-coll))

(defn calc-remaining
  [payments price]
  (->> payments
       (reduce (fn [s [_ p]]
                    (+ s p))
               0)
       (- price)
       (max 0)))

(defn verify-credit!
  [r c]
  (let [{:keys [price payments] :as m} (get-credit r c)
        t (reduce + (map second payments))]
    (if (>= t price)
      (let [ncredit (-> m (dissoc :r :c) (assoc :free true))]
        (update! credit-coll m ncredit)
        (insert! credit-coll (new-credit [r c] nil nil nil))
        true)
      false)))

(defn add-payment!
  [r c pay]
  (let [m (get-credit r c)
        date (now)]
    (update! credit-coll m (update-in m [:payments] conj [date pay]))
    (let [freed (verify-credit! r c)]
      {:resp :success :freed freed})))

(defn create-credit!
  [r c name article price]
  (let [cred (get-credit r c)]
    (when (available? r c)
      (let [new-cred (assoc cred
                       :name name :article article :price price
                       :date (now))]
        (update! credit-coll cred new-cred)
        true))))

(defn get-clients
  []
  (map :name (fetch credit-coll :where {:name {:$ne nil}} :only [:name])))

;;; TODO - figure this out
(defn fix-regex
  [s]
  (let [rs (zipmap #{\a \e \i \o \u} (repeat "\\p{L}"))]
    (->> s
         (replace rs)
         (apply str))))

(defn find-client
  [name]
  (fetch credit-coll :where {:name (re-pattern (str "(?i)" name))} :sort {:date 1}))
