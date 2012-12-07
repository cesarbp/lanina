;;; Contains functions to convert dbf into clojure maps, clojure maps into
;;; dbf, mongo collections into dbf and viceversa

;;; Main functions: parse-dbf, write-to-dbf!

(ns dbf.core
  (:import [com.linuxense.javadbf DBFReader DBFWriter DBFField]
           [java.io FileOutputStream FileInputStream])
  (:require [clojure.java.io :as io]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clojure.string :as s]
            [lanina.models.article :as article])
  (:use [somnium.congomongo :only [fetch collection-exists? create-collection! insert!]]
        lanina.utils))

;;; Reader (Parser)
(defn get-all-fields [dbf-reader]
  (let [count (.getFieldCount dbf-reader)]
    (map (fn [i] (keyword (.getName (.getField dbf-reader i)))) (range count))))

(defn record-map [fields record]
  (let [cleaned                         ; Convert dates to strings and no whitespace at the end of fields
        (map (fn [v]
               (cond
                (= java.util.Date (type v))                 
                (let [d (tc/from-date v)]
                  (str (t/year d) "/" (t/month d) "/" (t/day d)))
                (string? v)
                (s/replace v #"\s+$" "")
                :else v))
             (seq record))]
    (apply hash-map (flatten (map vector fields cleaned)))))

(defn get-all-records [dbf-reader]
  "Gets all the remaining records that have not been accessed from the reader.
  Depends on the state of the reader."
  (letfn [(build-record-list [acc dbf-reader]
            (let [next-record (.nextRecord dbf-reader)]
              (if (seq next-record)
                (recur (conj acc next-record) dbf-reader)
                acc)))]
    (build-record-list [] dbf-reader)))

(defn parse-dbf [dbf-path]
  "Returns a collection of maps, converting a dbf file into a sequence of
clojure hash maps"
  (with-open [in (io/input-stream (FileInputStream. dbf-path))]
    (let [r (DBFReader. in)
          fields (get-all-fields r)
          records (get-all-records r)]
      (map record-map (repeat fields) records))))

;;; Writer
(defn determine-field-types [record-map]
  "Requires a record as a clojure hash-map just like one of those returned by parse-dbf.
Returns a map of field names -> dbf type"
  (letfn [(get-dbf-type [v]
            (cond (number? v)
                  DBFField/FIELD_TYPE_N
                  (string? v)
                  DBFField/FIELD_TYPE_C
                  (= (type v) java.util.Date)
                  DBFField/FIELD_TYPE_D))]
    (->> record-map
         (map (fn [[field-name val]] {field-name (get-dbf-type val)}))
         (apply merge))))

;;; TODO - 1 record sample is not good enough when the db is full of crap
(defn ^DBFField create-fields [schema]
  "Creates the fields with the schema provided (record-map with correct types and correct
number of fields)"
  (let [field-length 50]               ;Global field-length
    (into-array DBFField
     (map (fn [[field-name field-type]]
            (doto (DBFField.)
              (.setName (name field-name))
              (.setDataType field-type)
              (.setFieldLength field-length)))
          (determine-field-types schema)))))

(defn fix-number-fields [schema record-map]
  "If the record-map doesnt have the right amount of fields according to the schema it adds
an 'empty' field with the value from the schema"
  (let [remaining (clojure.set/difference (set (keys schema)) (set (keys record-map)))]
    (into record-map (reduce (fn [acc k] (into acc {k (schema k)})) {} remaining))))

(defn ^Object map-to-record [schema fields record-map]
  "Takes the values of a record in the form of a clojure hashmap and turns them into a
java array for use in a dbf writer"
  (let [record-map (fix-number-fields schema record-map)
        fixed-record
        (map (fn [field]
               (let [field-type (.getDataType field)
                     record-val ((keyword (.getName field)) record-map)]
                 (if (= field-type DBFField/FIELD_TYPE_N)
                   (if (number? record-val)
                     (double record-val)
                     0.0)
                   (str record-val))))
             fields)]
    (into-array Object fixed-record)))

(defn write-to-dbf! [schema all-records out-filename]
  "Requires a collection of all the records to be placed in the dbf file in the
form of clojure hash maps and writes the dbf file"
  (let [fields (create-fields schema)
        writer (doto (DBFWriter.)
                 (.setFields fields))]
    (doseq [record all-records]
      (try
        (.addRecord writer (map-to-record schema fields record))
        (catch Exception e
          (println (str "Error: " e "\n" (seq (map-to-record fields record))))
          (throw (Throwable. "Error!!!")))))
    (with-open [out (FileOutputStream. out-filename)]
      (.write writer out))))

(defn mongo-coll-to-dbf! [coll-name out-filename]
  (let [records (map #(dissoc % :_id :prev) (fetch coll-name))
        schema (cond (= :articles coll-name) article/schema
                     :else (first records))]
    (write-to-dbf! schema records out-filename)))

(defn dbf-to-mongo! [coll-name dbf-path]
  "Appends the records in the dbf file to the collection, creates it if it doesn't
exist, this can generate duplicate documents if not careful"
  (when-not (collection-exists? coll-name)
    (create-collection! coll-name))
  (let [data (parse-dbf dbf-path)]
    (when (seq data)
      (doseq [doc data]
        (insert! coll-name doc)))))