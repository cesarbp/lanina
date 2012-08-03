(ns lanina.models.article
  (:use
   somnium.congomongo)
  (:require [lanina.models.utils :as db]
            [clojure.string :as str]))

(def article-coll :articles)
(def unmodifiable-props #{})

;;; Utils
(defn is-int [s]
  (try (Integer/parseInt s)
       true
       (catch Exception e
         false)))

(defn is-double [s]
  (and (not (is-int s))
       (try (Double/parseDouble s)
            true
            (catch Exception e
              false))))

(defn get-type-of
  "Gets the type of the key in the articles collection"
  [k]
  (class (k (fetch-one article-coll))))

(defn coerce-to-useful-type
  [[k v]]
  (let [cl (get-type-of k)]
    (if (= cl java.lang.String)
      [k (.toUpperCase (eval `(new ~cl ~v)))]
      [k (eval `(new ~cl ~v))])))

(defn coerce-to-useful-types [ms]
  (map (fn [m]
         (reduce (fn [acc [k v]]
                   (cond (= k :codigo) (into acc {k v})          ;Leave barcodes as strings
                         (is-int v) (into acc  {k (Integer/parseInt v)})
                         (is-double v) (into acc {k (Double/parseDouble v)})
                         :else (into acc {k v})))
                 {}
                 m))
       ms))

;;; Use the db

(defn id-to-str [doc]
  (when (:_id doc)
    (assoc doc :_id (str (:_id doc)))))

(defn get-article [barcode]
  (id-to-str (fetch-one article-coll :where {:codigo barcode} :only [:nom_art :codigo :prev_con :prev_sin])))

(defn get-articles-regex [regex]
  (map id-to-str
       (fetch article-coll :where {:nom_art regex} :only [:nom_art] :sort {:nom_art 1})))

(defn get-by-id [id]
  (when (db/valid-id? id)
    (id-to-str
     (fetch-one article-coll :where {:_id (object-id id)}))))

(defn get-by-id-only [id only]
  (when (db/valid-id? id)
    (id-to-str
     (fetch-one article-coll :where {:_id (object-id id)} :only only))))

(defn get-keys
  "Get the keys of the articles collection documents"
  []
  (keys (fetch-one article-coll)))

(defn update-article [new]
  (let [old (fetch-one article-coll :where {:_id (object-id (:_id new))})]
    (update! article-coll old
             (into old (first (coerce-to-useful-types (list (dissoc new :_id))))))))

;;; fill the db
(def db-file "src/lanina/models/db-csv/tienda.csv")

(defn csv-to-maps [f]
  "Takes a csv file and creates a map for each row of column: column-value"
  (let [ls (str/split (slurp f) #"\n")
        cs (first ls)
        rs (rest (rest ls))
        colls (map (comp keyword #(.toLowerCase %)) (str/split cs #","))
        regs (map #(str/split % #",") rs)]
    (map (fn [r]
           (zipmap colls r))
         regs)))

(defn fill-article-coll! [ms]
  "Appends the maps to the article collection"
  (db/maybe-init)
  (when-not (collection-exists? article-coll)
    (create-collection! article-coll)
    (println (str "Created collection " article-coll)))
  (doseq [m ms]
    (insert! article-coll m)))

(defn setup! []
  "Dangerous! drops articles collection if exists and creates a new one with the
csv of the articles"
  (db/maybe-init)
  (when (collection-exists? article-coll)
    (drop-coll! article-coll)
    (println (str "Deleted collection " article-coll)))
  (-> db-file
      (csv-to-maps)
      (coerce-to-useful-types)
      (fill-article-coll!)))

;;; Searching an article

(def verbose-names
  {:unidad "Unidad"
   :stk "Stock"
   :lin "Línea"
   :ramo "Ramo"
   :fech_ac "Fecha ac"
   :cu_sin "Costo unitario sin IVA"
   :cu_con "Costo unitario con IVA"
   :pres "Presentes"
   :ubica "Ubicación"
   :prov "Proveedor"
   :iva "IVA"
   :gan "Porcentaje de ganancia"
   :fech_an "Fecha an"
   :exis "En existencia"
   :prev_con "Precio de venta con IVA"
   :prev_sin "Precio de venta sin IVA"
   :ccj_con "Caja con IVA"
   :ccj_sin "Caja sin IVA"
   :nom_art "Nombre del artículo"
   :codigo "Código de barras"
   :tam "Tamaño"})

(defn valid-barcode? [s]
  (and (>= 13 (count s))
       (every? (comp is-int str) s)))

(defn get-by-barcode
  "Get an article by its barcode"
  [bc]
  (when (valid-barcode? bc)
    (fetch-one article-coll :where {:codigo bc} :only [:_id :codigo :nom_art :prev_con :prev_sin])))

(defn get-by-search
  "Search for an article name"
  [q]
  (let [query (re-pattern (str "(?i)^.*" q ".*$"))]
    (fetch article-coll :where {:nom_art query} :only [:_id :codigo :nom_art :prev_con :prev_sin])))

;;; Delete an article
(defn delete-article [id]
  (destroy! article-coll {:_id (object-id id)}))

;;; Adding an article
(defn add-article [art-map]
  (insert! article-coll art-map))