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

(defn align-right
  [s]
  (format "%28s" s))

(defn justify-number
  [n]
  (format "$ %6s" (format-decimal n)))

(defn justify-bigger-number
  [n]
  (format "$ %7s" (format-decimal n)))

(defn show-price
  [n p t]
  (align-right (str n " x " (format-decimal p) " = " (justify-number t))))

(defn show-big-price
  [n p t]
  (format "%28s" (str n " x " (format-decimal p) " = " (justify-bigger-number t))))

(defn show-text-price
  [s p]
  (align-right (str s (justify-number p))))

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
                "-----------------------------"
                "\r\n"
                (format "    SUMA ==>        $%7.2f" total)
                "\r\n"
                (format "  CAMBIO ==>        $%7.2f" change)
                "\r\n"
                (format "EFECTIVO ==>        $%7.2f" pay)]
        body (mapcat
              (fn [art]
                ["\r\n"
                 (:nom_art art)
                 "\r\n"
                 (show-price (:quantity art) (:precio_venta art)
                             (:total art))])
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
                "-----------------------------"
                "\r\n"
                (center-string "Codigo Cantidad Costo Importe" col-size)
                "\r\n"
                "-----------------------------"
                "\r\n"]
        body (mapcat (fn [art]
                       [(:nom_art art)
                        "\r\n"
                        "Codigo: " (:codigo art)
                        "\r\n"
                        (show-big-price (:quantity art)
                                        (:costo_caja art)
                                        (:total art))
                        "\r\n"
                        (apply str (repeat col-size \-))
                        "\r\n"])
                     prods)
        footer [(str "ARTICULOS......    " n-arts)
                "\r\n"
                (str "No CAJAS.......    " boxes)
                "\r\n"
                (str "TOTAL..........    " (justify-bigger-number total))]]
    (concat header body footer)))

(defn print-purchase-text
  [prods boxes total n-arts date]
  (apply str (print-purchase-seq prods boxes total n-arts date)))

(defn print-employee-list-text
  [prods date employee]
  (let [col-size (get-col-size)
        header (str (center-string "LA NIÑA" col-size)
                    "\r\n"
                    (center-string (str "EMPLEADO: " employee)
                                   col-size)
                    "\r\n"
                    (center-string (str "FECHA: " date) col-size)
                    "\r\n")
        body
        (apply str
               (mapcat (fn [art]
                         ["\r\n"
                          "---------------------------"
                          "\r\n"
                          (:nom_art art)
                          "\r\n"
                          (format "%-13s  %s" (:codigo art) (justify-number (:precio_venta art)))
                          ])
                       prods))]
    (str header body "\r\n" "---------------------------")))

(defn print-cashier-cut-text
  [tickets-n exto gvdo iva total date time]
  (let [col-size (get-col-size)
        header (str "L O N J A  M E R C A N T I L"
                    "\r\n"
                    (center-string "L A N I N A" col-size)
                    "\r\n"
                    (center-string "RFC: ROHE5108278T7" col-size)
                    "\r\n"
                    (center-string "GUERRERO No.45  METEPEC. MEX." col-size)
                    "\r\n"
                    "---------------------------"
                    "\r\n"
                    "CORTE DEL DIA: " date
                    "\r\n"
                    "         HORA: " time)
        body (str "\r\n\r\n"
                  "EXENTOS: . . . . " (justify-bigger-number exto)
                  "\r\n\r\n"
                  "GRAVADOS . . . . " (justify-bigger-number gvdo)
                  "\r\n\r\n"
                  "GRAVADOS SIN IVA " (justify-bigger-number (- gvdo iva))
                  "\r\n\r\n"
                  "IVA 16% . . . .  " (justify-bigger-number iva)
                  "\r\n\r\n"
                  "SUMA . . . . . . " (justify-bigger-number total)
                  "\r\n\r\n"
                  "Clientes . . . . " tickets-n)]

    (str header body)))

(defn print-credit-text
  [credit]
  (let [col-size (get-col-size)
        {:keys [articles payments name r c date]} credit
        total (reduce + (map :price articles))
        paid (reduce + (map second payments))
        remaining (max 0 (- total paid))
        last-payment (last payments)

        arts-str (apply str
                        (for [{:keys [name price]} articles]
                          (str "\r\n" name "\r\n" (format-decimal price))
                          ))]
    (str (center-string "Pago por abonos" col-size)
         "\r\n"
         (center-string (str "Cliente: " name) col-size)
         "\r\n"
         (center-string (str "Abono #" r c) col-size)
         "\r\n"
         (center-string (str "Fecha inicio:" date) col-size)
         "\r\n"
         (center-string (str "Ultimo pago:" (first last-payment)) col-size)
         "\r\n"
         "----------------------------"
         "\r\n"
         "Total a pagar " (justify-number total)
         "\r\n"
         "Pagado:       " (justify-number paid)
         "\r\n"
         "----------------------------"
         "\r\n"
         "Restante:     " (justify-number remaining)
         "\r\n\r\n"
         "Articulos:"
         arts-str)))

(defn print-method-1
  "Sends to LPT1"
  [s]
  (let [s (apply str s (repeat 12 "\r\n"))]
    (try
      (println s)
      (with-open [out (PrintWriter. (FileWriter. "lpt1"))]
        (.print out s))
      (catch Exception e
        (println "Impresora no encontrada.")))))

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
    (future (print-fn s))))

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

(defn print-credit
  [credit]
  (-> credit
      (print-credit-text)
      (print-text)))
