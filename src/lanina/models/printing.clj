(ns lanina.models.printing
  (:use somnium.congomongo
        [lanina.views.utils :only [format-decimal]]
        [lanina.models.article :only [is-number?]]
        [lanina.utils :only [coerce-to]]
        [lanina.views.utils :only [now-hour]])
  (:import [javax.print DocFlavor$INPUT_STREAM DocFlavor$STRING PrintServiceLookup DocPrintJob Doc
            SimpleDoc]
           [javax.print.attribute HashPrintRequestAttributeSet]
           [javax.print.attribute.standard MediaSizeName]
           [javax.swing JTextArea]
           [java.io FileInputStream PrintWriter FileWriter]
           [java.awt Font]))

(def printing-coll :printing)

(defn setup!
  []
  (when (collection-exists? printing-coll)
    (println "Dropping coll" printing-coll))
  (println "Creating coll" printing-coll)
  (create-collection! printing-coll)
  (insert! printing-coll {:name "properties" :type 1 :col-size 30}))

(defn get-print-type
  []
  (:type (fetch-one printing-coll :where {:name "properties"})))

(defn get-col-size
  []
  (:col-size (fetch-one printing-coll :where {:name "properties"})))

(defn get-props
  []
  (fetch-one printing-coll :where {:name "properties"}))

(defn update-print-type!
  [n]
  (when (is-number? n)
    (let [m (get-props)]
      (update! printing-coll m (assoc m :type ((coerce-to Long) n))))
    :success))

(defn update-col-size!
  [n]
  (when (is-number? n)
    (let [m (get-props)]
      (update! printing-coll m (assoc m :col-size ((coerce-to Long) n))))
    :success))

(defn center-string
  "Pads left side with enough spaces to center s"
  [s col-size]
  (let [cnt (count s)
        side (int (/ (- col-size cnt) 2))
        side-spaces (apply str (repeat side \space))]
    (str side-spaces s)))

(defn print-ticket-text
  [prods pay total change ticket-number folio date]
  (let [col-size (get-col-size)
        time (now-hour)
        header (str (center-string "LA NIÑA" col-size)
                    "\r\n"
                    (center-string "R.F.C: ROHE510827-8T7" col-size)
                    "\r\n"
                    (center-string "GUERRERO 45 METEPEC MEX" col-size)
                    "\r\n"
                    (center-string (str date " " time " TICKET:" ticket-number) col-size)
                    "\r\n"
                    (center-string (str "FOLIO:" folio) col-size))
        body (StringBuilder.)
        footer (str "\r\n"
                    "SUMA ==> $" (format-decimal total)
                    "\r\n"
                    "CAMBIO ==> $" (format-decimal change)
                    "\r\n"
                    "EFECTIVO ==> $" (format-decimal pay))]
    (doseq [art prods]
      (.append body (str "\r\n"
                         (:nom_art art)
                         "\r\n"
                         (:quantity art) " x " (format-decimal (:precio_venta art))
                         " = " (format-decimal (:total art)))))
    (str header body footer)))

(defn print-purchase-text
  [prods]
  (let [col-size (get-col-size)
        header (str (center-string "Compras" col-size)
                    "\r\n")
        body (StringBuilder.)]
    (doseq [art prods]
      (.append body (str (:codigo art) " x " (:quantity art)
                         "\r\n"
                         (:nom_art art)
                         "\r\n")))
    (str header body)))

(defn print-employee-list-text
  [prods]
  (let [col-size (get-col-size)
        header (str (center-string "Lista para acomodar" col-size)
                    "\r\n")
        body (StringBuilder.)]
    (doseq [art prods]
      (.append body (str "\r\n"
                         (:codigo art)
                         "\r\n"
                         (:nom_art art))))
    (str header body)))

(defn print-cashier-cut-text
  [tickets-n exto gvdo iva total date time]
  (let [col-size (get-col-size)
        header (str (center-string "Corte de caja" col-size)
                    "\r\n"
                    (center-string  (str date " " time) col-size)
                    "\r\n"
                    (center-string "------------" col-size)
                    "\r\n")
        body (str (center-string (str "Tickets: " tickets-n) col-size)
                  "\r\n"
                  (center-string (str "Exentos: " (format-decimal exto)) col-size)
                  "\r\n"
                  (center-string (str "Gravados: " (format-decimal gvdo)) col-size)
                  "\r\n"
                  (center-string (str "IVA: " (format-decimal iva)) col-size)
                  "\r\n"
                  (center-string (str "Total: " (format-decimal total)) col-size))]
    (str header body)))

(defn print-method-1
  "Sends to LPT1"
  [s]
  (try
    (with-open [out (PrintWriter. (FileWriter. "lpt1"))]
      (.print out s))
    (catch Exception e
      (println "Impresora no encontrada."))))

(defn print-method-2
  "Uses JtextArea. Prompts for printer."
  [s]
  (let [ta (JTextArea. s)]
    (.setFont ta (Font. Font/MONOSPACED Font/PLAIN 10))
    (.print ta)))

(defn print-method-3
  "Uses SimpleDoc with String Flavor and default print service"
  [s]
  (let [flavor DocFlavor$STRING/TEXT_PLAIN
        ps (PrintServiceLookup/lookupDefaultPrintService)
        pjob (.createPrintJob ps)
        doc (SimpleDoc. s flavor nil)]
    (.print pjob doc nil)))

(defn print-method-4
  "Uses SimpleDoc with Input stream flavor and default print service
and a file to read from"
  [s]
  (spit "temp123.txt" s)
  (let [pd (FileInputStream. "temp123.txt")
        flavor DocFlavor$INPUT_STREAM/AUTOSENSE
        ps (PrintServiceLookup/lookupDefaultPrintService)
        pjob (.createPrintJob ps)
        doc (SimpleDoc. s flavor nil)]
    (.print pjob doc nil)))

(defn print-text
  [s]
  (let [print-type (get-print-type)
        print-fn
        (condp = print-type
          1 print-method-1
          2 print-method-2
          3 print-method-3
          4 print-method-4)]
    (future (print-fn s))
    (future (println s))))

(defn print-ticket
  [prods pay total change ticket-number folio date]
  (let [txt (print-ticket-text prods pay total change ticket-number folio date)]
    (print-text txt)))

(defn print-purchase
  [prods]
  (print-text (print-purchase-text prods)))

(defn print-cashier-cut
  [tickets-n exto gvdo iva total date time]
  (-> (print-cashier-cut-text tickets-n exto gvdo iva total date time)
      (print-text)))
