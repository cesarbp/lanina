(ns lanina.models.article
  (:use
   somnium.congomongo)
  (:require [lanina.models.utils :as db]
            [lanina.views.utils :as view-utils]
            [clojure.string :as str]
            [lanina.utils :as utils]))

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

;;; Add additional fields
(defn add-dates-prev-img-to-articles [ms]
  (map (fn [m]
         (let [fech_ac (:fech_ac m)
               fech_an (:fech_an m)
               date (cond (and (string? fech_ac) (seq fech_ac)) fech_ac
                          (and (string? fech_an) (seq fech_an)) fech_an)]
           (if (seq date)
             (-> m
                 (into {:date date :prev [] :img ""})
                 (dissoc :fech_ac :fech_an))
             m)))
       ms))

;;; Use the db  

(defn id-to-str [doc]
  (when (:_id doc)
    (assoc doc :_id (str (:_id doc)))))

(defn get-article [barcode]
  (id-to-str (fetch-one article-coll :where {:codigo barcode} :only [:nom_art :codigo :prev_con :prev_sin])))

(defn get-all-only [only]
  (map id-to-str (fetch article-coll :only only)))

(defn get-articles-regex [regex]
  (map id-to-str
       (fetch article-coll :where {:nom_art regex} :only [:nom_art] :sort {:nom_art 1})))

(defn get-by-id [id]
  (when (db/valid-id? id)
    (id-to-str
     (fetch-one article-coll :where {:_id (object-id id)}))))

(defn get-by-id-date [id date]
  (first (filter #(= date (:date %))
                 (:prev (get-by-id id)))))

(defn get-by-id-nostr [id]
  (when (db/valid-id? id)
    (fetch-one article-coll :where {:_id (object-id id)})))

(defn get-by-id-only [id only]
  (when (db/valid-id? id)
    (id-to-str
     (fetch-one article-coll :where {:_id (object-id id)} :only only))))

(defn get-by-match [where]
  (fetch-one article-coll :where where))

(defn get-by-name [name]
  (id-to-str
   (fetch-one article-coll :where {:nom_art name} :only [:nom_art :codigo :prev_con :prev_sin])))

(defn get-by-provider [prov]
  (map id-to-str
   (fetch article-coll :where {:prov prov} :only [:_id :codigo :nom_art :prev_con :prev_sin :ccj_con :ccj_sin :iva])))

(defn get-keys
  "Get the keys of the articles collection documents"
  []
  (keys (fetch-one article-coll)))

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
      (add-dates-prev-img-to-articles)
      (fill-article-coll!)))

;;; Searching an article
(def art-props
  [:img
   :date
   :prev
   :unidad 
   :stk 
   :lin 
   :ramo
   ;; Old properties
   ;; :fech_ac
   ;; :fech_an 
   :cu_sin 
   :cu_con 
   :pres 
   :ubica 
   :prov 
   :iva 
   :gan 
   :exis 
   :prev_con 
   :prev_sin 
   :ccj_con 
   :ccj_sin 
   :nom_art 
   :codigo 
   :tam])

(def verbose-names
  {:img "Nombre de imagen"
   :date "Fecha de última modificación"
   :unidad "Unidad"
   :stk "Stock"
   :lin "Línea"
   :ramo "Ramo"
   :fech_ac "Fecha actual"
   :cu_sin "Costo unitario sin IVA"
   :cu_con "Costo unitario con IVA"
   :pres "Presentación"
   :ubica "Ubicación"
   :prov "Proveedor"
   :iva "IVA"
   :gan "Ganancia"
   :fech_an "Fecha anterior"
   :exis "En existencia"
   :prev_con "Precio de venta con IVA"
   :prev_sin "Precio de venta sin IVA"
   :ccj_con "Costo de caja con IVA"
   :ccj_sin "Costo de caja sin IVA"
   :nom_art "Nombre de artículo"
   :codigo "Código de barras"
   :tam "Tamaño"})

(def lines
  ["ABARROTES" "ROPA" "CARNES" "DESECHABLES" "DULCES" "MEDICAMENTOS" "FERRETERIA" "JARCERIA" "JUGUETES" "LACTEOS" "MATERIALES" "MERCERIA" "PANES" "PAPELERIA" "PERFUMERIA" "PLASTICOS" "REGALOS" "SEMILLAS" "VINOS" "OTROS"])

(def units
  ["PIEZA" "KILO" "METRO" "PAQUETE" "BOTELLA" "BOTE" "CAJA" "FRASCO" "BOLSA"])

(defn valid-barcode? [s]
  (and (>= 13 (count s))
       (every? (comp is-int str) s)))

(defn get-by-barcode
  "Get an article by its barcode"
  [bc]
  (when (valid-barcode? bc)
    (fetch-one article-coll :where {:codigo bc} :only [:_id :codigo :nom_art :prev_con :prev_sin :ccj_con :ccj_sin :iva])))

(defn get-by-search
  "Search for an article name"
  [q]
  (let [query (re-pattern (str "(?i)^.*" q ".*$"))]
    (fetch article-coll :where {:nom_art query} :only [:_id :codigo :nom_art :prev_con :prev_sin :ccj_con :ccj_sin :iva])))

;;; Delete an article
(defn delete-article [id]
  (destroy! article-coll {:_id (object-id id)}))

(defn update-error [error-map k error-val]
  (update-in error-map [k] (fn [old new] (if (seq old) (conj old new) [new])) error-val))

;;; Validate a new article
(defn validate-article [art-map]
  (let [empty-errors (reduce (fn [errors [k v]]
                               (cond (and (some #{:codigo :nom_art} [k])
                                          (not (seq v)))
                                     (update-error errors k (str "El campo \"" (k verbose-names) "\" no puede estar vacío"))
                                     :else errors))
                             {} art-map)]
    (reduce (fn [errors [k v]]
              (cond (and (= :codigo k) (not= "0" v) (seq (fetch-one article-coll :where {:codigo v})))
                    (update-error errors :codigo "Este código ya existe")
                    (and (= :nom_art k) (seq (fetch-one article-coll :where {:nom_art v})))
                    (update-error errors :nom_art "Este nombre ya existe")
                    (and (some #{:ccj_con :prev_con :ccj_sin :prev_sin} [k])
                         (or (number? v) (seq v))
                         (not ((utils/coerce-to Double) v)))
                    (update-error errors k (str "El \"" (k verbose-names) "\" tiene que ser numérico"))
                    :else errors))
            empty-errors
            art-map)))

;;; Adding an article
(defn add-article [art-map]
  (let [errors (validate-article art-map)]
    (if (seq errors)
      errors
      (do (insert! article-coll (first (coerce-to-useful-types [art-map])))
          :success))))

;;; Sorting an article
(defn sort-by-vec [art v]
  (let [v (reverse v)
        vals (map (fn [k] [k (art k)])
                  v)]
    (into (seq (eval `(dissoc ~art ~@v))) vals)))

(defn get-different-fields [old new]
  (reduce (fn [acc [k v]]
            (when-not (= v (k old))
                      (into acc {k v})))
          {}
          new))

;;; Updating an article
(defn update-article [new]
  (let [old (fetch-one article-coll :where {:_id (object-id (:_id new))})
        errors (validate-article (get-different-fields old new))
        date (view-utils/now-with-time)
        updated (db/get-updated-map old
                                (into (into old (first (coerce-to-useful-types (list (dissoc new :_id)))))
                                      {:date date}))]
    (if (seq errors)
      errors
      (do (update! article-coll old updated)
          :success))))
