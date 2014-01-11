(ns lanina.models.catalogs
  (:use somnium.congomongo)
  (:require [clojure.java.io :refer [reader]]
            [lanina.utils :refer [coerce-to]]
            [clojure.string :refer [split]]
            [clojure-csv.core :as csv]
            [clojure.string :as s]
            [lanina.views.utils :refer [fix-date]]))

(def catalogs-coll :catalogs)
(def catalog-types-coll :catalog-types)
(def valid-types (sorted-set "cadena" "flotante" "entero" "fecha"))

(defn get-type
  [type]
  (fetch-one catalog-types-coll :where {:type (s/upper-case type)}))

(defn get-all-type-names
  []
  (remove nil? (map :type (fetch catalog-types-coll :only [:type]))))

(defn get-entry
  [n & [name]]
  (if name
    (fetch-one catalogs-coll :where {"NUMERO DE CATALOGO" n "NOMBRE" (s/upper-case name)})
    (fetch-one catalogs-coll :where {"NUMERO DE CATALOGO" n})))

(defn add-type!
  ;;; Extra field-types
  [name fields-types]
  (let [fields (keys fields-types)
        types (vals fields-types)
        fields-types (merge fields-types
                            {"NUMERO DE CATALOGO" "entero"
                             "NOMBRE" "cadena"
                             "TIPO" "cadena"})
        valid? (and (seq name)
                    (not (get-type name))
                    (every? valid-types types)
                    (every? seq fields))
        fields-types (zipmap (map s/upper-case (keys fields-types))
                             (vals fields-types))]
    (if-not valid?
      :error
      (do (insert! catalog-types-coll
                   {:type (s/upper-case name) :fields-types fields-types})
          :success))))

(defn get-next-cat-number
  []
  ((fnil inc 0)
   (get
    (first
     (fetch catalogs-coll
            :where {"NUMERO DE CATALOGO"
                    {:$ne nil}}
            :only ["NUMERO DE CATALOGO"]
            :sort {"NUMERO DE CATALOGO" -1}))
    (keyword "NUMERO DE CATALOGO"))))

(defn coerce-fields
  [fields-vals fields-types]
  (prn fields-vals fields-types)
  (reduce (fn [m [k s]]
            (let [v (case (fields-types k)
                      "fecha" (fix-date s)
                      "entero" ((coerce-to Long) s)
                      "flotante" ((coerce-to Double) s)
                      "cadena" (s/upper-case s))]
              (conj m {k v})))
          {}
          fields-vals))

(defn new-entry
  [type fields-vals]
  (let [fields-types (:fields-types (get-type type))]
    (coerce-fields fields-vals fields-types)))

(defn valid-entry?
  [entry]
  (boolean
   (and (seq (entry :NOMBRE))
        (entry (keyword "NUMERO DE CATALOGO"))
        (= (get-next-cat-number) (entry (keyword "NUMERO DE CATALOGO")))
        (seq (entry :TIPO))
        (not (get-entry (entry (keyword :NOMBRE)))))))

(defn- valid-modification?
  [entry fields-types]
  (let [valid-fields (set (keys fields-types))
        entry (dissoc entry :_id)]
    (and (seq (entry :NOMBRE))
         (entry (keyword "NUMERO DE CATALOGO"))
         (seq (entry :TIPO))
         (every? valid-fields (keys entry))
         (every? identity (vals entry)))))

(defn add-entry!
  [type fields-vals]
  (let [fields-vals (-> fields-vals
                        (assoc (keyword "NUMERO DE CATALOGO") (get-next-cat-number))
                        (assoc :TIPO (s/upper-case type)))
        entry (new-entry type fields-vals)]
    (if-not (valid-entry? entry)
      :error
      (do (insert! catalogs-coll entry)
          :success))))

(defn modify-entry
  "Modify by catalog number and name"
  [n name modified-fields]
  (prn n name modified-fields)
  (let [modified-fields (dissoc modified-fields
                                :TIPO (keyword "NUMERO DE CATALOGO"))
        old (get-entry n name)
        type (get-type (:TIPO old))
        fields-types (:fields-types type)
        coerced (coerce-fields modified-fields fields-types)
        new-entry (merge old coerced)]
    (prn new-entry fields-types)
    (if-not (valid-modification? new-entry fields-types)
      :error
      (do (update! catalogs-coll old new-entry)
          :success))))

(defn delete-entry
  [n & [name]]
  (if name
    (destroy! catalogs-coll {"NUMERO DE CATALOGO" n "NOMBRE" name})
    (destroy! catalogs-coll {"NUMERO DE CATALOGO" n}))
  :success)

(defn get-all-of-type
  [type]
  (fetch catalogs-coll :where {"TIPO" type} :sort {"NOMBRE" 1}))

(defn delete-category
  [cat]
  (if-not (get-type cat)
    :error
    (do
      (doseq [c (get-all-of-type cat)]
        (destroy! catalogs-coll c))
      (destroy! catalog-types-coll {:type cat})
      :success)))

;;======================
;; To install clients ;;
;;======================

(defn install-clients!
  []
  (destroy! catalog-types-coll {:type "CLIENTES"})
  (destroy! catalogs-coll {:TIPO "CLIENTES"})
  (insert! catalog-types-coll
           {:type "CLIENTES"
            :fields-types {"NOMBRE" "cadena"
                           "RFC" "cadena"
                           "CALLE" "cadena"
                           "COLONIA" "cadena"
                           "MUNICIPIO" "cadena"
                           "ESTADO" "cadena"
                           "NUMERO-EXT" "cadena"
                           "CP" "entero"
                           "CORREO" "cadena"
                           "NUMERO DE CATALOGO" "entero"
                           "TIPO" "cadena"}}))

(defn get-client-by-name
  [name]
  (let [name (str name)
        name (clojure.string/upper-case name)]
    (dissoc (fetch-one catalogs-coll :where {"NOMBRE" name})
            :_id)))

(defn get-all-client-names
  []
  (map :NOMBRE (fetch catalogs-coll :where {:TIPO "CLIENTES"} :only ["NOMBRE"])))

(defn add-client!
  [name rfc correo calle numero-ext colonia municipio cp estado]
  (when (and (seq name)
             (seq rfc)
             (seq calle))
    (destroy! catalogs-coll {:TIPO "CLIENTES"
                             :RFC rfc})
    (insert! catalogs-coll {:TIPO "CLIENTES"
                            :NOMBRE name
                            :RFC rfc
                            :CALLE calle
                            :COLONIA colonia
                            :MUNICIPIO municipio
                            :ESTADO estado
                            :NUMERO-EXT numero-ext
                            :CP cp
                            :CORREO correo
                            (keyword "NUMERO DE CATALOGO") (get-next-cat-number)})))

;;======================
;; To install láminas ;;
;;======================
(defn- coerce-row
  [row-map]
  (let [lamina-verbose
        {:cve "CLAVE"
         :mat "MATERIA"
         :nombre "NOMBRE"
         :marca "MARCA"
         :nocata "NUMERO DE CATALOGO"
         :tipo "TIPO"}]
    (reduce (fn [acc [k v]]
              (if (or (= :cve k) (= :nocata k))
                (conj acc {(k lamina-verbose) ((coerce-to Long) v)})
                (conj acc {(k lamina-verbose) (s/upper-case v)})))
            {:TIPO "LAMINAS"}
            row-map)))

;; To install láminas
(defn- import-from-csv!
  [fpath]
  (let [ls (csv/parse-csv (slurp fpath))
        fields (map (comp keyword #(.toLowerCase %))
                    (first ls))]
    (insert! catalog-types-coll
             {:type "LAMINAS"
              :fields-types {"CLAVE" "entero"
                             "MATERIA" "cadena"
                             "NOMBRE" "cadena"
                             "MARCA" "cadena"
                             "NUMERO DE CATALOGO" "entero"
                             "TIPO" "cadena"}})

    (reduce (fn [_ l]
              (->> l
                   (zipmap fields)
                   coerce-row
                   (insert! catalogs-coll)))
            nil
            (rest ls))))

(def db-path "install/LAMINAS.csv")

(defn setup!
  []
  (when (collection-exists? catalogs-coll)
    (println "Dropping coll" catalogs-coll)
    (println "Dropping coll" catalog-types-coll)
    (drop-coll! catalogs-coll)
    (drop-coll! catalog-types-coll))
  (println "Creating coll" catalogs-coll)
  (create-collection! catalogs-coll)
  (println "Creating coll" catalog-types-coll)
  (create-collection! catalog-types-coll)
  (import-from-csv! db-path))
