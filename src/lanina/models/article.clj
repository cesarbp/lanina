(ns lanina.models.article
  (:use
   somnium.congomongo)
  (:require [lanina.models.utils :as db]))

;;; Playing around

(defn rand-barcode []
  (apply str (repeatedly 13 #(rand-int 10))))

(def articles
  {:names ["agua"
           "leche"
           "carne"
           "chocolate"
           "pantalon"
           "blusa"
           "zapato"
           "lente"
           "comida"]
   :adjectives ["mineral"
                "condensada"
                "fria"
                "amargo"
                "para joven"
                "para nina"
                "negro"
                "azul"
                "rojo"
                "para perro"
                "para gato"]})

(defn rand-article []
  (str ((:names articles) (rand-int (count (:names articles))))
       " "
       ((:adjectives articles) (rand-int (count (:adjectives articles))))))

(def test-file "src/lanina/models/testfiles/testarticles.txt")

(defn fill-testfile [n]
  "Fill test file with n articles"
  (let [first-line (format "%-13s | nom_art\n" "cod_bar")
        second-line (apply str (concat (repeat 14 \-) "+" (repeat 14 \-) "\n"))]
    (->> (zipmap (repeatedly n rand-barcode) (repeatedly n rand-article))
         (reduce (fn [p [b a]] (concat p  "\n" b " | " a)) "")
         (rest)
         (apply str)
         (str first-line second-line)
         (spit test-file))))

;;; After filling a test file
;;; fill the db
(def test-coll :test-arts)
(def article-fields #{:code :name})
(require '[clojure.string :as str])

(defn f-to-maps [f]
  "Create a secuence of maps of each line in the file"
  (let [ls (str/split (slurp test-file) #"\n")
        cs (first ls)
        rs (rest (rest ls))
        colls (map keyword (str/split cs #","))
        regs (map #(str/split % #",") rs)]
    (map (fn [r]
           (zipmap colls r))
         regs)))

(defn fill-test-coll! [ms]
  "Fill the test-coll on the db with the collection of maps supplied"
  (db/maybe-init)
  (when-not (collection-exists? test-coll)
    (create-collection! test-coll)
    (println (str "Created collection " test-coll)))
  (doseq [m ms]
    (insert! test-coll m)))

(defn setup! [n]
  "n is the number of documents to add, randomly generated"
  (fill-testfile n)
  (->> test-file
       f-to-maps
       fill-test-coll!))