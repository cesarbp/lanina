(ns lanina.models.printing
  (:use somnium.congomongo
        [lanina.views.utils :only [format-decimal]]
        [lanina.models.article :only [is-number?]]
        [lanina.utils :only [coerce-to]]
        [lanina.views.utils :only [now-hour]])
  (:import [javax.print DocFlavor$INPUT_STREAM DocFlavor$STRING PrintServiceLookup DocPrintJob Doc
            SimpleDoc PrintService]
           [javax.print.attribute HashPrintRequestAttributeSet]
           [javax.print.attribute.standard MediaSizeName]
           [javax.swing JTextArea]
           [java.io FileInputStream PrintWriter FileWriter]
           [java.awt Font GraphicsEnvironment FontMetrics Graphics]
           [java.awt.print PrinterJob PageFormat Printable]))

(def printing-coll :printing)

(defn system-fonts []
  (let [ge (GraphicsEnvironment/getLocalGraphicsEnvironment)]
    (vec (.getAllFonts ge))))

(def fonts
  (system-fonts))

(defn create-font
  [idx new-size]
  (.deriveFont (fonts idx) (float new-size)))

(defn find-courier-font-index
  "Used to set default font in setup"
  []
  (let [fonts (system-fonts)]
    (->> fonts
         (map-indexed (fn [i f] [i (re-seq #".*Courier.*" (.getFontName f))]))
         (filter second)
         (ffirst))))

(defn setup!
  []
  (when (collection-exists? printing-coll)
    (println "Dropping coll" printing-coll)
    (drop-coll! printing-coll))
  (println "Creating coll" printing-coll)
  (create-collection! printing-coll)
  (insert! printing-coll {:name "properties" :type 1 :col-size 30 :font-size 6 :font (find-courier-font-index) :valid-font-sizes [4 5 6 7 8 9 10 11 12 13 14 15 16]}))

(defn get-current-print-type
  []
  (:type (fetch-one printing-coll :where {:name "properties"})))

(defn get-current-font
  []
  (:font (fetch-one printing-coll :where {:name "properties"})))

(defn get-valid-font-sizes
  []
  (:valid-font-sizes (fetch-one printing-coll :where {:name "properties"})))

(defn get-current-font-size
  []
  (:font-size (fetch-one printing-coll :where {:name "properties"})))

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

(defn update-font!
  [idx]
  (let [idx ((coerce-to Long) idx)
        font (when idx (get fonts idx))
        m (get-props)]
    (if font
      (do
        (update! printing-coll m (assoc m :font idx))
        :success)
      :failure)))

(defn update-font-size!
  [n]
  (let [n ((coerce-to Long) n)
        m (get-props)]
    (if (and n (pos? n))
      (do (update! printing-coll m (assoc m :font-size n))
          :success)
      :failure)))

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

(defn print-ticket-seq
  [prods pay total change ticket-number folio date]
  (let [col-size (get-col-size)
        time (now-hour)
        header [(center-string "LA NIÑA" col-size)
                "\r\n"
                (center-string "R.F.C: ROHE510827-8T7" col-size)
                "\r\n"
                (center-string "GUERRERO 45 METEPEC MEX" col-size)
                "\r\n"
                (center-string (str date " " time " TICKET:" ticket-number) col-size)
                "\r\n"
                (center-string (str "FOLIO:" folio) col-size)]
        footer ["\r\n"
                "SUMA ==> $" (format-decimal total)
                "\r\n"
                "CAMBIO ==> $" (format-decimal change)
                "\r\n"
                "EFECTIVO ==> $" (format-decimal pay)]
        body (mapcat
              (fn [art]
                ["\r\n"
                 (:nom_art art)
                 "\r\n"
                 (str (:quantity art) " x " (format-decimal (:precio_venta art)))
                 (str " = " (format-decimal (:total art)))])
              prods)]
    (concat header body footer)))

(defn print-ticket-text
  [prods pay total change ticket-number folio date]
  (apply str (print-ticket-seq prods pay total change ticket-number folio date)))

(defn print-purchase-seq
  [prods boxes total n-arts date]
  (let [col-size (get-col-size)
        header [(center-string "LONJA MERCANTIL LA NIÑA" col-size)
                "\r\n"
                (center-string date col-size)
                "\r\n"
                (center-string "Listado de Compras en ticket" col-size)
                "\r\n"
                (center-string "Cantidad Costo Importe Codigo" col-size)
                "\r\n"]
        body (mapcat (fn [art]
                       [(:nom_art art)
                        "\r\n"
                        (str (:quantity art) " "
                             (format-decimal (:costo_caja art))
                             " " (format-decimal (:total art))
                             " " (:codigo art))
                        "\r\n"
                        (apply str (repeat col-size \-))
                        "\r\n"])
                     prods)
        footer [(str "ARTICULOS......" n-arts)
                "\r\n"
                (str "No CAJAS......." boxes)
                "\r\n"
                (str "TOTAL.........." (format-decimal total))]]
    (concat header body footer)))

(defn print-purchase-text
  [prods boxes total n-arts date]
  (apply str (print-purchase-seq prods boxes total n-arts date)))

(defn print-employee-list-text
  [prods date employee]
  (let [col-size (get-col-size)
        header (str (center-string "LA NIÑA" col-size)
                    "\r\n"
                    (center-string (str "LISTA PARA EMPLEADO: " employee)
                                   col-size)
                    "\r\n"
                    (center-string (str "FECHA: " date) col-size)
                    "\r\n")
        body
        (apply str
               (mapcat (fn [art]
                         ["\r\n"
                          (str (:codigo art) " " (:precio_venta art))
                          "\r\n"
                          (:nom_art art)])
                       prods))]
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
  "Uses JtextArea."
  [s]
  (let [ta (JTextArea. s)]
    (.setFont ta (create-font (get-current-font) (get-current-font-size)))
    (try
      (.print ta nil nil false nil nil false)
      (catch Exception _
        (.print ta)))))

(defn print-method-3
  "Uses JtextArea. Prompts for printer"
  [s]
  (let [ta (JTextArea. s)]
    (.setFont ta (create-font (get-current-font) (get-current-font-size)))
    (.print ta)))

;; (defn print-method-3
;;   "Uses SimpleDoc with String Flavor and default print service"
;;   [s]
;;   (let [flavor DocFlavor$STRING/TEXT_PLAIN
;;         ps (PrintServiceLookup/lookupDefaultPrintService)
;;         pjob (.createPrintJob ps)
;;         doc (SimpleDoc. s flavor nil)]
;;     (.print pjob doc nil)))

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
  [prods boxes total n-arts date]
  (print-text (print-purchase-text prods boxes total n-arts date)))

(defn print-cashier-cut
  [tickets-n exto gvdo iva total date time]
  (-> (print-cashier-cut-text tickets-n exto gvdo iva total date time)
      (print-text)))

(defn print-employee-list
  [prods date employee]
  (-> (print-employee-list-text prods date employee)
      (print-text)))
