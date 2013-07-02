(ns lanina.models.credit
  "Model for sales by credit"
  (:use somnium.congomongo)
  (:require [lanina.views.utils :refer [now]]))

(def credit-coll :credit)

(def credit-fields #{:r :c :name :payments :articles :date :free})
(def articles-fields #{:name :price :purchased})
(def verbose {:payments "Pagos"
              :name "Nombre"
              :article "Artículo"
              :price "Precio"
              :date "Fecha de inicio"})

(defn new-articles
  [articles]
  (mapv (fn [[name price]]
          {:name name
           :price price
           :purchased false})
        articles))

(defn new-credit
  [[r c] name articles]
  {:r r
   :c c
   :name name
   :articles articles
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
    (insert! credit-coll (new-credit [i c] nil nil)))
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
  ([l] (let [cred (get-credit l)]
         (or (nil? (:name cred))
             (:free cred)))))

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
  [payments articles]
  (->> payments
       (reduce (fn [s [_ p]]
                    (+ s p))
               0)
       (- (reduce + (map :price articles)))
       (max 0)))

(defn purchase-all-articles
  [credit]
  (let [n (count (:articles credit))]
    (reduce #(update-in %1 [:articles %2 :purchased] (constantly true))
            credit
            (range n))))

(defn verify-credit!
  [r c]
  (let [{:keys [payments articles] :as m} (get-credit r c)
        price (reduce + (map :price articles))
        t (reduce + (map second payments))]
    (if (>= t price)
      (let [ncredit (-> m (dissoc :r :c) (assoc :free true) (purchase-all-articles))]
        (update! credit-coll m ncredit)
        (insert! credit-coll (new-credit [r c] nil nil))
        true)
      false)))

(defn purchaseable?
  [credit art-ns pay]
  (let [{:keys [articles payments]} credit
        used-money (->> articles
                        (filter :purchased)
                        (map :price)
                        (reduce +))
        cost (->> art-ns
                  (map articles)
                  (map :price)
                  (reduce +))
        paid-so-far (reduce + (map second payments))
        free-money (- (+ pay paid-so-far)
                      used-money
                      cost)]
    (<= 0 free-money)))

(defn add-payment!
  [r c pay to-purchase]
  (let [m (get-credit r c)
        date (now)
        purchaseable? (purchaseable? m to-purchase pay)]
    (if purchaseable?
      (let [new-m (update-in m [:payments] conj [date pay])
            new-m (reduce
                   #(update-in %1 [:articles %2 :purchased] (constantly true))
                   new-m
                   to-purchase)]
        (update! credit-coll m new-m)
        (let [freed (verify-credit! r c)]
          {:resp :success :freed freed}))
      {:resp :error :error :not-purchaseable})))

(defn create-credit!
  [r c name articles]
  (let [cred (get-credit r c)]
    (when (available? r c)
      (let [new-cred (assoc cred
                       :name name :articles (new-articles articles)
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
  (fetch credit-coll :where {:name (re-pattern (str "(?i)" name))} :sort {:date -1}))

(defn restart!
  []
  (drop-coll! credit-coll))
