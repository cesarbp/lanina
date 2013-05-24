(ns lanina.models.article
  (:use
   somnium.congomongo
   [lanina.views.utils :only [now valid-date? fix-date days-ago now-with-time]]
   [lanina.models.adjustments :only [valid-iva? get-modify-threshold get-valid-ivas]])
  (:require [lanina.models.utils :as db]
            [clojure.string :as str]
            [lanina.utils :as utils]
            [clojure-csv.core :as csv]))

(def article-coll :articles)
(def unmodifiable-props #{})

;;; Utils
(defn is-int? [s]
  (try (Integer/valueOf s)
       true
       (catch Exception e
         false)))

(defn is-double? [s]
  (try (Double/valueOf s)
       true
       (catch Exception e
         false)))

(defn is-number? [s]
  (or (is-int? s) (is-double? s)))

(defn get-type-of
  "Gets the type of the key in the articles collection"
  [k]
  (class (k (fetch-one article-coll))))

;;; Deprecated - I think :)
(defn coerce-to-useful-type
  [[k v]]
  (let [cl (get-type-of k)]
    (if (= cl java.lang.String)
      [k (.toUpperCase (eval `(new ~cl ~v)))]
      [k (eval `(new ~cl ~v))])))

;;; Metadata
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

(def new-art-props
  [:img
   :date
   :prev
   :unidad
   :stk
   :lin
   :ramo
   :iva
   :pres
   :gan
   :costo_unitario
   :costo_caja
   :precio_venta
   :ubica
   :prov
   :exis
   :codigo
   :nom_art
   :tam])

(def new-art-props-sorted
  [:nom_art :codigo :pres :gan :iva :costo_caja :costo_unitario :precio_venta :prov :lin :ramo :tam :exis :ubica :img])

;;; points to the correct data type on each field to solve some dbf <-> mongo
;;; conversion headaches
;;; special fields like prev are not included
(def schema
  {:img ""
   :date ""
   :unidad ""
   :stk 0
   :lin ""
   :ramo ""
   :cu_sin 0.0
   :cu_con 0.0
   :pres 0
   :ubica ""
   :prov ""
   :iva 0.0
   :gan 0.0
   :exis 0
   :prev_con 0.0
   :prev_sin 0.0
   :ccj_con 0.0
   :ccj_sin 0.0
   :nom_art ""
   :codigo ""
   :tam ""
   })

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

(def verbose-names-new
  {:_id "Identificador en la base de datos"
   :img "Nombre de imagen"
   :date "Fecha de última modificación"
   :prev "Versiones previas"
   :unidad "Unidad"
   :stk "Stock"
   :lin "Línea"
   :ramo "Ramo"
   :costo_unitario "Costo unitario"
   :costo_caja "Costo de caja"
   :precio_venta "Precio de venta"
   :pres "Presentación"
   :ubica "Ubicación"
   :prov "Proveedor"
   :iva "IVA"
   :gan "Ganancia"
   :exis "En existencia"
   :codigo "Código de barras"
   :nom_art "Nombre de artículo"
   :tam "Tamaño"})

(defn verbose-extra-fields [iva]
  {:cu_extra (if iva "Costo unitario sin IVA" "Costo unitario con IVA")
   :ccj_extra (if iva "Costo de caja sin IVA" "Costo de caja con IVA")
   :prev_extra (if iva "Precio de venta sin IVA" "Precio de venta con IVA")})

(def lines
  ["ABARROTES" "ROPA" "CARNES" "DESECHABLES" "DULCES" "MEDICAMENTOS" "FERRETERIA" "JARCERIA" "JUGUETES" "LACTEOS" "MATERIALES" "MERCERIA" "PANES" "PAPELERIA" "PERFUMERIA" "PLASTICOS" "REGALOS" "SEMILLAS" "VINOS" "OTROS"])

(def units
  ["PIEZA" "KILO" "METRO" "PAQUETE" "BOTELLA" "BOTE" "CAJA" "FRASCO" "BOLSA"])

;;; Initialize the db

(defn to-int [s]
  (try (Integer/valueOf (str s))
       (catch Exception e
         nil)))

(defn to-double
  [s]
  (try (Double/valueOf (str s))
       (catch Exception e
         nil)))

(defn sanitize-price [orig-map new-map iva-kw noiva-kw]
  (cond (not (:iva new-map)) 0.0
        (== 0 (:iva new-map)) (to-double (noiva-kw orig-map))
        (< 0 (:iva new-map)) (to-double (iva-kw orig-map))))

(defn map-to-article
  "Takes a row of a csv as a hashmap and returns the map as it should be in the db"
  [m]
  (let [res (atom {})
        mod-map (fn [k v]
                  (swap! res conj {k v}))]
    (mod-map :img "")
    (let [d (:fech_ac m)
          d (if (valid-date? d)
              (fix-date d)
              (now))]
      (mod-map :date (or (:date m) d)))
    (mod-map :prev [])
    (mod-map :unidad (when (:unidad m) (clojure.string/upper-case (:unidad m))))
    (mod-map :stk (to-int (:stk m)))
    (mod-map :lin (when (:lin m) (clojure.string/upper-case (:lin m))))
    (mod-map :ramo (when (:ramo m) (clojure.string/upper-case (:ramo m))))
    (mod-map :iva (to-double (:iva m)))
    (mod-map :costo_unitario (or (to-double (:costo_unitario m))
                                 (sanitize-price m @res :cu_con :cu_sin)))
    (mod-map :costo_caja (or (to-double (:costo_caja m))
                             (sanitize-price m @res :ccj_con :ccj_sin)))
    (mod-map :precio_venta
             (or (to-double (:precio_venta m))
                 (sanitize-price m @res :prev_con :prev_sin)))
    (mod-map :pres (to-int (:pres m)))
    (mod-map :ubica (:ubica m))
    (mod-map :prov (:prov m))
    (mod-map :gan (to-double (:gan m)))
    (mod-map :exis (to-int (:exis m)))
    (mod-map :codigo (:codigo m))
    (mod-map :nom_art (:nom_art m))
    (mod-map :tam (:tam m))
    @res))

(defn map-to-article-only
  "Only is a sequence or set of keys, only these keys will be added to the returned map"
  [m only]
  (let [to-add (set only)
        res (atom {})
        mod-map (fn [k v]
                  (swap! res conj {k v}))]
    (when (to-add :img) (mod-map :img ""))
    (let [d (:fech_ac m)
          d (if (valid-date? d)
              (fix-date d)
              (now))]
      (when (to-add :date) (mod-map :date (or (:date m) d))))
    (when (to-add :prev) (mod-map :prev []))
    (when (to-add :unidad) (mod-map :unidad (when (:unidad m) (clojure.string/upper-case (:unidad m)))))
    (when (to-add :stk) (mod-map :stk (to-int (:stk m))))
    (when (to-add :lin) (mod-map :lin (when (:lin m) (clojure.string/upper-case (:lin m)))))
    (when (to-add :ramo) (mod-map :ramo (when (:ramo m) (clojure.string/upper-case (:ramo m)))))
    (when (to-add :iva) (mod-map :iva (to-double (:iva m))))
    (when (to-add :costo_unitario) (mod-map :costo_unitario (or (to-double (:costo_unitario m))
                                                (sanitize-price m @res :cu_con :cu_sin))))
    (when (to-add :costo_caja) (mod-map :costo_caja (or (to-double (:costo_caja m))
                                            (sanitize-price m @res :ccj_con :ccj_sin))))
    (when (to-add :precio_venta)
      (mod-map :precio_venta
               (or (to-double (:precio_venta m))
                   (sanitize-price m @res :prev_con :prev_sin))))
    (when (to-add :pres) (mod-map :pres (to-int (:pres m))))
    (when (to-add :ubica) (mod-map :ubica (:ubica m)))
    (when (to-add :prov) (mod-map :prov (:prov m)))
    (when (to-add :gan) (mod-map :gan (to-double (:gan m))))
    (when (to-add :exis) (mod-map :exis (to-int (:exis m))))
    (when (to-add :codigo) (mod-map :codigo (:codigo m)))
    (when (to-add :nom_art) (mod-map :nom_art (:nom_art m)))
    (when (to-add :tam) (mod-map :tam (:tam m)))
    @res))

(defn fix-maps [ms] (map map-to-article ms))

;;; Deprecated
(defn coerce-to-useful-types [ms]
  (map (fn [m]
         (reduce (fn [acc [k v]]
                   (cond (= k :codigo) (into acc {k v})          ;Leave barcodes as strings
                         (is-int? v) (into acc  {k (Integer/parseInt v)})
                         (is-double? v) (into acc {k (Double/parseDouble v)})
                         :else (into acc {k v})))
                 {}
                 m))
       ms))

;;; Add additional fields
;;; Deprecated
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

;;; fill the db
;(def db-file "install/tienda.csv")
(def db-file "install/TIENDA.csv")

(defn csv-to-maps
  "Takes a csv file and creates a map for each row of column: column-value"
  [f]
  (let [ls (csv/parse-csv (slurp f))
        cs (first ls)
        rs (rest ls)
        colls (map (comp keyword #(.toLowerCase %)) cs)]
    (map (fn [r]
           (zipmap colls r))
         rs)))

(defn fill-article-coll!
  "Appends the maps to the article collection"
   [ms]
  (db/maybe-init)
  (when-not (collection-exists? article-coll)
    (create-collection! article-coll)
    (println (str "Created collection " article-coll)))
  (doseq [m ms]
    (try
      (insert! article-coll m)
      (catch Exception e
        (println (str "Error at " m))))))

(defn setup!
  "Dangerous! drops articles collection if exists and creates a new one with the
csv of the articles"
  []
  (db/maybe-init)
  (when (collection-exists? article-coll)
    (drop-coll! article-coll)
    (println (str "Deleted collection " article-coll)))
  (-> db-file
      (csv-to-maps)
      ;(coerce-to-useful-types)
      ;(add-dates-prev-img-to-articles)
      (fix-maps)
      (fill-article-coll!)))

;;; Use the db
(defn id-to-str [doc]
  (when (:_id doc)
    (assoc doc :_id (str (:_id doc)))))

(defn get-article [barcode]
  (id-to-str (fetch-one article-coll :where {:codigo barcode} :only [:nom_art :codigo :precio_venta])))

(defn get-all []
  (map id-to-str (fetch article-coll)))

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
  (id-to-str
   (first (filter #(= date (:date %))
                  (:prev (get-by-id id))))))

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
   (fetch-one article-coll :where {:nom_art name} :only [:nom_art :codigo :precio_venta :costo_caja])))

(defn get-by-provider [prov]
  (map id-to-str
   (fetch article-coll :where {:prov prov} :only [:_id :codigo :nom_art :precio_venta :costo_caja :iva])))

(defn get-keys
  "Get the keys of the articles collection documents"
  []
  (keys (fetch-one article-coll)))

(defn valid-barcode? [s]
  (and (>= 13 (count s))
       (every? (comp is-int? str) s)))

(defn add-fields [article]
  (let [iva (when (valid-iva? (:iva article)) (:iva article))
        to-add (atom {})
        add (fn [k v] (swap! to-add conj {k v}))]
    (if-not iva
      article
      (let [complement-iva (if (== 0 iva) 16.0 0.0)
            mult (/ (+ 100.0 complement-iva) 100)
            ccj-extra (when (number? (:costo_caja article)) (* mult (:costo_caja article)))
            cu-extra (when (number? (:costo_unitario article)) (* mult (:costo_unitario article)))
            prev-extra (when (number? (:precio_venta article)) (* mult (:precio_venta article)))]
        (when ccj-extra (add :ccj_extra ccj-extra))
        (when cu-extra (add :cu_extra cu-extra))
        (when prev-extra (add :prev_extra prev-extra))
        (merge article @to-add)))))

(defn get-by-barcode
  "Get an article by its barcode"
  [bc]
  (when (valid-barcode? bc)
    (id-to-str
     (fetch-one article-coll :where {:codigo bc} :only [:_id :codigo :nom_art :iva :precio_venta :costo_caja :iva]))))

(defn get-by-search
  "Search for an article name"
  [q]
  (let [query (re-pattern (str "(?i)^.*" q ".*$"))]
    (fetch article-coll :where {:nom_art query} :only [:_id :codigo :nom_art :precio_venta :costo_caja :iva])))

;;; Delete an article
(defn delete-article [id]
  (destroy! article-coll {:_id (object-id id)}))

(defn update-error [error-map k error-val]
  (update-in error-map [k] (fn [old new] (if (seq old) (conj old new) [new])) error-val))

;;; Find errors
(defn errors-warnings
  [article]
  (let [m article
        res (atom {:errors {}
                   :warnings {}
                   :article article})
        add-error (fn [k msg]
                    (swap! res update-in [:errors k]
                           (fn [prev] (if prev (conj prev msg) [msg]))))
        add-warning (fn [k msg]
                      (swap! res update-in [:warnings k]
                             (fn [prev] (if prev (conj prev msg) [msg]))))
        thresh (get-modify-threshold)
        id (:_id m)]
    (if-let [n (get-by-name (:nom_art m))]
      (when-not (and id (= id (:_id n)))
        (add-error :nom_art "Este nombre de artículo ya existe")))
    (if-let [n (get-by-barcode (:codigo m))]
      (when-not (and id (= id (:_id n)))
        (add-error :codigo "Este código de barras ya existe")))
    (when-not (> thresh (days-ago (:date m)))
      (add-error :date "Lleva demasiado tiempo sin una modificación"))
    (when-not (:img m) (add-warning :img "No contiene imagen"))
    (when-not (valid-date? (:date m)) (add-error :date "La fecha de última modificación es inválida"))
    (when-not (:stk m)
      (add-warning :stk "No tiene stock"))
    (when (and (:stk m)
               (not (is-int? (:stk m)))
               (not (<= 0 (:stk m))))
      (add-error :stk "Stock inválido"))
    (when-not ((set lines) (:lin m)) (add-error :lin "Línea inválida"))
    (when-not (:ramo m) (add-warning :ramo "No tiene ramo"))
    (let [valid-ivas (get-valid-ivas)]
      (when-not ((set valid-ivas) (:iva m))
        (add-error :iva (str "El IVA es inválido. Valores válidos de IVA: " (clojure.string/join "," valid-ivas)))))
    (when-not (and (is-int? (:pres m)) (< 0 (:pres m)))
      (add-error :pres "La presentación es inválida"))
    (when-not (is-number? (:gan m))
      (add-error :gan "La ganancia es inválida"))
    (when-not (and (is-number? (:costo_unitario m))
                   (< 0 (:costo_unitario m)))
      (add-error :costo_unitario "El costo unitario es inválido"))
    (when-not (and (is-number? (:costo_caja m))
                   (< 0 (:costo_caja m)))
      (add-error :costo_caja "El costo por caja es inválido"))
    (when (and (is-number? (:costo_unitario m))
               (is-number? (:costo_caja m))
               (is-number? (:pres m))
               (not (== (:costo_caja m) (* (:pres m) (:costo_unitario m)))))
      (add-warning :costo_caja "El costo de caja no coincide con los valores de presentación y costo unitario"))
    (when-not (and (is-number? (:precio_venta m))
                   (< 0 (:precio_venta m)))
      (add-error :precio_venta "El precio de venta es inválido"))
    (when (and (is-number? (:gan m))
               (is-number? (:precio_venta m))
               (is-number? (:costo_unitario m))
               (not (== (:precio_venta m)
                        (* (:costo_unitario m)
                           (/ (+ 100 (:gan m)) 100)))))
      (add-warning :precio_venta "El precio de venta no coincide con los valores de ganancia y costo unitario"))
    (when-not (and (string? (:ubica m)) (seq (:ubica m)))
      (add-warning :ubica "No tiene ubicación"))
    (when-not (:exis m)
      (add-warning :exis "No tiene en existencia"))
    (when (and (:exis m) (not (is-int? (:exis m))))
      (add-error :exis "En existencia inválido"))
    (when-not (and (string? (:codigo m)) (seq (:codigo m)))
      (add-error :codigo "No tiene código de barras"))
    (when-not (valid-barcode? (:codigo m))
      (add-error :codigo "Código de barras inválido"))
    (when-not (and (string? (:nom_art m)) (seq (:nom_art m)))
      (add-error :nom_art "No tiene nombre de artículo"))
    (if (or (seq (:errors @res)) (seq (:warnings @res)))
      (do (swap! res conj {:_id (:_id m)})
          @res)
      nil)))

;;; Adding an article
(defn add-article!
  "art-map is possibly the post received by the server, it should have no fields other
than those belonging to the article"
  [art-map]
  (let [art (map-to-article art-map)
        {errors :errors} (errors-warnings art)]
    (if-not (empty? errors)
      errors
      (do (insert! article-coll art)
          :success))))

;;; Sorting an article
(defn sort-by-vec [art v]
  (let [v (reverse v)
        vals (map (fn [k] [k (art k)])
                  v)]
    (into (seq (eval `(dissoc ~art ~@v))) vals)))

(defn find-changes [old new]
  (let [ks (keys new)
        new (map-to-article-only new ks)
        equal? (fn [x y]
                 (if (and (number? x) (number? y))
                   (== x y)
                   (= x y)))]
    (for [k ks :when (not (equal? (k old) (k new)))]
      [k (k old) (k new)])))

(defn update-article!
  "Takes the post but it should have no keys other than the fields of the article to be updated"
  [id pst]
  (let [old (fetch-one article-coll :where {:_id (object-id id)})
        fixed (map-to-article-only pst (keys pst))
        date-modified (now-with-time)
        fixed (merge fixed {:date date-modified})
        new (merge old fixed)
        {errors :errors} (errors-warnings new)
        to-add (db/get-updated-map old new)]
    (if-not (empty? errors)
      errors
      (do (update! article-coll old to-add)
          :success))))
