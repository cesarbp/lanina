(ns lanina.views.bill
  "Generacion de los pdf para las facturas"
  (:use noir.core
        hiccup.form
        lanina.views.common
        [hiccup.element :only [link-to javascript-tag]]
        [lanina.utils :only [coerce-to]]
        [lanina.views.utils :only [now now-hour fix-date]]
        [noir.response :only [redirect json]])
  (:require [clj-pdf.core :as pdf]
            [lanina.views.utils :as util]
            [lanina.views.ticket :as ticket]
            [lanina.models.bill :as bill]
            [lanina.models.catalogs :as client]
            [lanina.models.ticket :as ticket-db])
  (:import [java.awt Desktop]
           [java.io File]))

(defn pdf-path [nombre]
  (str "facturas/LANINA-"
       (util/now) "-"
       (first (clojure.string/split nombre #"\s+")) "-"
       (quot (System/currentTimeMillis) 1000)
       ".pdf"))

(defn small-header
  [s]
  [:phrase {:size 8
            :style :bold} s])

(def smalls
  {1 "UNO"
   2 "DOS"
   3 "TRES"
   4 "CUATRO"
   5 "CINCO"
   6 "SEIS"
   7 "SIETE"
   8 "OCHO"
   9 "NUEVE"
   10 "DIEZ"
   11 "ONCE"
   12 "DOCE"
   13 "TRECE"
   14 "CATORCE"
   15 "QUINCE"
   16 "DIECISÉIS"
   17 "DIECISIETE"
   18 "DIECIOCHO"
   19 "DIECINUEVE"
   20 "VEINTE"
   21 "VEINTIUNO"
   22 "VEINTIDÓS"
   23 "VEINTITRÉS"
   24 "VEINTICUATRO"
   25 "VEINTICINCO"
   26 "VEINTISÉIS"
   27 "VEINTISIETE"
   28 "VEINTIOCHO"
   29 "VEINTINUEVE"
   30 "TREINTA"
   40 "CUARENTA"
   50 "CINCUENTA"
   60 "SESENTA"
   70 "SETENTA"
   80 "OCHENTA"
   90 "NOVENTA"
   100 "CIEN"
   200 "DOSCIENTOS"
   300 "TRESCIENTOS"
   400 "CUATROCIENTOS"
   500 "QUINIENTOS"
   600 "SEISCIENTOS"
   700 "SETECIENTOS"
   800 "OCHOCIENTOS"
   900 "NOVECIENTOS"})

(def decs
  {2 "VEINTI"})

(defn total-letters
  [n]
  (let [intpart (long n)
        dec (* 100 (- n intpart))
        dec (if (zero? dec) 0 (long (Math/round dec)))
        cents (format "%02d/100" dec)
        rev (->> (str intpart)
                 (map #(- (int %) 48))
                 (reverse)
                 (map vector (range))
                 (reverse))]
    (str "CANTIDAD EN LETRA:"
         (loop [[[idx n :as nxt] & rst] rev acc ""]
           (if-not nxt
             (if (seq acc) acc " CERO")
             (cond
              (and (zero? n) (= 3 idx))
              (recur rst (str acc " MIL"))
              (zero? n)
              (recur rst acc)
              :else
              (case idx
                6 (recur rst (str acc " " (if (= 1 n)
                                            "UN MILLÓN"
                                            (str
                                             (smalls n) " MILLONES"))))
                (5 2) (recur rst (str acc " "
                                      (if (and (= 1 n)
                                               (not= [0 0]
                                                     (map second (take 2 rst))))
                                        "CIENTO"
                                        (smalls (* n 100)))))
                (4 1) (let [x (Long/valueOf (str n (second (first rst))))]
                        (if (or (< 9 x 20) (< 20 x 30))
                          (recur (drop 1 rst) (str acc " " (smalls x)
                                                   (if (= 4 idx)
                                                     " MIL" "")))
                          (recur rst (str acc " " (smalls (* 10 n))
                                          (if (zero? (second (first rst)))
                                            ""
                                            " Y")))))
                3 (recur rst
                         (str acc " " (if (= 1 n)
                                        "MIL"
                                        (str (smalls n) " MIL"))))
                0 (recur rst
                         (str acc " " (smalls n)))))))
         " " cents " M.N.")))

(defn merge-seqs
  [a b]
  (into a (drop (count a) b)))

(defn make-bill
  [{:keys [nombre-cliente calle colonia municipio estado numero-ext cp rfc-cliente correo
           factura fecha anio no-aprobacion serie-certificado
           articulos total pretax-exto pretax-gvdo tax-gvdo
           sello-digital]}]
  (let [path (pdf-path nombre-cliente)
        articulos-part (partition-all 10 articulos)
        base-rows (repeat 10 (vec (repeat 7 (str \u200c)))) ;; Just whitespace
        rows (map (fn [group-arts]
                    (merge-seqs
                     (mapv (fn [art]
                             [(:codigo art)
                              (str (:quantity art))
                              (:unidad art)
                              (:nom_art art)
                              (util/format-decimal (:price-before-tax art))
                              (if (= "gvdo" (:type art))
                                ""
                                (util/format-decimal (:before-tax art)))
                              (if (= "gvdo" (:type art))
                                (util/format-decimal (:before-tax art))
                                "")])
                           group-arts)
                     base-rows))
                  articulos-part)
        base [{:right-margin 25
               :left-margin 25
               :bottom-margin 25
               :top-margin 25
               :size "a4"
               :font {:size 8}
               :pages true}]
        cadena-original (str "||"
                             ;; DATOS COMPROBANTE
                             1 " |"
                             serie-certificado " |"
                             factura  " |"
                             fecha  " |"
                             no-aprobacion  " |"
                             anio  " |"
                             "" " |"
                             "EFECTIVO"  " |"
                             "" " |"
                             (util/format-decimal pretax-gvdo) " |"
                             (util/format-decimal pretax-exto) " |"
                             (util/format-decimal total) " |"
                             "CAJERO" " |"
                             "MEXICO" " |"
                             "" "|"
                             "" "|"
                             "MXN" " |"
                             "" "|"
                             "" "|"
                             "" "|"
                             "" "|"
                             ;; DATOS EMISOR
                             "ROHE5108278T7" " |"
                             "ELISA RODRIGUEZ HERNANDEZ" " |"
                             ;; DOMICILIO EXPEDICION
                             "GUERRERO" " |"
                             "45" " |"
                             "" "|"
                             "CENTRO (CABECERA MUNICIPAL)" " |"
                             "" "|"
                             "" "|"
                             "METEPEC" " |"
                             "MEXICO" " |"
                             "MEXICO" " |"
                             "" "|"
                             ;; REGIMEN FISCAL
                             "" "|"
                             ;; RECEPTOR
                             rfc-cliente " |"
                             nombre-cliente " |"
                             ;; DOMICILIO CLIENTE
                             calle " |"
                             numero-ext " |"
                             colonia " |"
                             municipio "|"
                             cp "|"
                             estado "|"
                             "" "|"
                             "" "|"
                             "" "|"
                             "" "|"
                             ;; ARTICULOS
                             (apply str
                                    (map (fn [art]
                                           (str
                                            (:quantity art) " |"
                                            (:unidad art) " |"
                                            (:codigo art) " |"
                                            (:nom_art art) " |"
                                            (:precio_venta art) " |"
                                            (:total art) " |"
                                            "" "|"
                                            "" "|"
                                            "" "|"
                                            "" "|"))
                                         articulos))
                             ;; RETENCION DE IMPUESTOS
                             ;; TRASLADO DE IMPUESTOS
                             ;; COMPLEMENTO
                             "||")
        pages
        (map
         (fn [ten-rows]
           [;; header
            [:table {:padding 1}
             [[:table {:header [{:color [175 175 175]} "Lonja Mercantil \"La niña\""]
                       :border false
                       :cell-border false}
               ["ELISA RODRÍGUEZ HERNÁNDEZ"]
               ["RFC ROHE5108278T7"]
               ["GUERRERO 45 COL. CENTRO (CABECERA MUNICIPAL)"]
               ["MUNICIPIO METEPEC CP. 52140"]
               ["ESTADO DE MÉXICO, MÉXICO"]]
              [:table {:header [{:color [150 150 150]} "DATOS DEL RECEPTOR"]
                       :cell-border false}
               [nombre-cliente]
               [(str "RFC " rfc-cliente)]
               [(str calle " " numero-ext " COL. " colonia)]
               [(str "MUNICIPIO " municipio " CP" cp)]
               [(str estado ", MÉXICO")]]]]
            ;; Subheader - client data
            [:table {:offset 5
                     :header [{:color [200 200 200]}
                              ["FACTURA" "FECHA DEL PEDIDO DE COMPRA"
                               "No. y AÑO DE APROBACIÓN" "SERIE DEL CERTIFICADO"]]
                     :cell-border false}
             [(str factura) fecha (str no-aprobacion " - " anio) serie-certificado]]
            ;; Body
            (into
             [:table {:header [{:color [220 220 220]}
                               (mapv small-header
                                     ["Código" "Cantidad" "Medida"
                                      "Descripción" "P. Unitario" "Total Exento" "Total Gravado"])]
                      :font {:size 8}
                      :widths [12 7 7 38 12 12 12]
                      :padding 1
                      :spacing 0
                      :offset 5
                      :cell-border false}]
             ten-rows)
            ;; Totals Table
            [:table {:offset 0
                     :widths [48 28 12 12]
                     :cell-border false
                     :border false}
             [[:cell {:set-border [:right]}
               (total-letters total)]
              "SUBTOTAL ANTES DE IMPUESTOS"
              [:cell {:set-border [:left :right]} (util/format-decimal pretax-exto)]
              [:cell {:set-border [:left :right]} (util/format-decimal pretax-gvdo)]]
             [[:cell {:set-border [:right]} ""] "IMPUESTO SOBRE LA VENTA"
              [:cell {:set-border [:left :right]} "0.00"]
              [:cell {:set-border [:left :right]} (util/format-decimal tax-gvdo)]]
             [[:cell {:set-border [:right]} ""] "TOTAL"
              [:cell {:set-border [:left :right :top :bottom]
                      :colspan 2}
               [:phrase {:style :bold
                         :size 10}
                (util/format-decimal total)]]]]
            ;; Footer
            [:pdf-table {:bounding-box [100 100]
                         :spacing-before 20
                         :border 0}
             [20 80]
             [[:pdf-cell {:set-border []}
               [:image "img/rfc.png"]]
              [:pdf-cell {:set-border []}
               [:pdf-table {:bounding-box [100 100]}
                [100]
                [[:pdf-cell {:set-border []} "CADENA ORIGINAL"]]
                [[:phrase {:size 6} cadena-original]]
                [[:pdf-cell {:set-border []} "SELLO DIGITAL"]]
                [[:phrase {:size 6} sello-digital]]]]]]])
         rows)
        pages (interpose [:pagebreak] pages)]
    (pdf/pdf (into base pages) path)
    (if (Desktop/isDesktopSupported)
      (.open (Desktop/getDesktop) (File. path))
      path)))

(defpartial bill-form []
  (form-to {:class "form-horizontal" :id "modify-article-form" :name "modify-article"}
           [:post "/facturas/impresion/"]
           [:fieldset
            [:div.control-group
             (label {:class "control-label"}
                    "nombre-cliente" "Nombre del Cliente")
             [:div.controls
              (text-field {:autocomplete "off" :id "nombre"} "nombre-cliente")]]

            [:div.control-group
             (label {:class "control-label"}
                    "rfc-cliente" "RFC Cliente")
             [:div.controls
              (text-field {:autocomplete "off"
                           :id "rfc"} "rfc-cliente")]]

            [:div.control-group
             (label {:class "control-label"}
                    "correo" "Correo Electrónico")
             [:div.controls
              (text-field {:autocomplete "off" :id "correo"} "correo")]]

            [:div.control-group
             (label {:class "control-label"}
                    "calle" "Calle")
             [:div.controls
              (text-field {:autocomplete "off"
                           :id "calle"} "calle")]]

            [:div.control-group
             (label {:class "control-label"}
                    "numero-ext" "Número Exterior")
             [:div.controls
              (text-field {:autocomplete "off"
                           :id "numero-ext"} "numero-ext")]]

            [:div.control-group
             (label {:class "control-label"}
                    "colonia" "Colonia")
             [:div.controls
              (text-field {:autocomplete "off"
                           :id "colonia"} "colonia")]]

            [:div.control-group
             (label {:class "control-label"}
                    "cp" "Código Postal")
             [:div.controls
              (text-field {:autocomplete "off"
                           :id "cp"
                           :class "only-numbers"} "cp")]]

            [:div.control-group
             (label {:class "control-label"}
                    "estado" "Estado")
             [:div.controls
              (text-field {:autocomplete "off"
                           :id "estado"} "estado")]]

            [:div.control-group
             (label {:class "control-label"}
                    "no-aprobacion" "No. Aprobación")
             [:div.controls
              (text-field {:autocomplete "off"
                           :id "aprob"} "no-aprobacion")]]

            [:div.control-group
             (label {:class "control-label"}
                    "serie-certificado" "Serie Certificado")
             [:div.controls
              (text-field {:autocomplete "off"} "serie-certificado")]]

            [:div.control-group
             (label {:class "control-label"}
                    "sello-digital" "Sello Digital")
             [:div.controls
              (text-field {:autocomplete "off"} "sello-digital")]]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"}
                           "Generar Factura")]))

(defpage "/facturas/nuevo/" {:as items}
  (when (seq items)
    (let [folio (bill/get-next-bill-number)
          time (util/now-with-time)
          anio (util/now-year)
          pairs (:pairs (ticket/sanitize-ticket items))
          prods (ticket/fetch-prods pairs)
          prods (sort-by :nom_art prods)
          total (reduce + (map :total prods))
          pretax-gvdo (reduce + (map :before-tax (filter #(= "gvdo" (:type %)) prods)))
          pretax-exto (reduce + (map :before-tax (filter #(= "exto" (:type %)) prods)))
          tax-gvdo (reduce + (map :taxed prods))
          saved (bill/save-latest folio time anio prods total pretax-exto pretax-gvdo tax-gvdo)]))
  (let [content {:title "Factura"
                 :content [:div.container-fluid
                           (bill-form)
                           (javascript-tag
                            "$('form input').first().focus()")]
                 :nav-bar true
                 :active "Ventas"}]
    (main-layout-incl content [:base-css :search-css :jquery :base-js :bill-js :numbers-js])))

(defpage [:post "/facturas/impresion/"] {:as datos}
  (let [latest (bill/get-latest)
        datos (zipmap (keys datos) (map clojure.string/upper-case (vals datos)))
        bill (merge latest datos)]
    (try (client/add-client! (:nombre-cliente datos) (:rfc-cliente datos)
                             (:correo datos) (:calle datos) (:numero-ext datos) (:colonia datos)
                             (:municipio datos) (:cp datos) (:estado datos))
         (make-bill bill)
         (catch Exception e
           (println (.getMessage e))))
    (util/flash-message (str "La factura se ha creado, enviar a <strong>" (:correo datos) "</strong>")
                        "success")
    (redirect "/")))

(defpage "/facturas/initialize" []
  (bill/setup!)
  "Success!")

(defpage "/facturas/clientes" []
  (client/install-clients!)
  "Success!")

(defpage "/clientes/" {:keys [nombre]}
  (json (client/get-client-by-name nombre)))

(defpage "/clientes/todos/" []
  (let [all (client/get-all-client-names)]
    (when-not (seq all)
      (client/install-clients!))
    (json all)))

(defn bill-from-ticket
  [folio]
  (when-let [folio ((coerce-to Long) folio)]
    (when-let [tckt (ticket-db/get-by-folio folio)]
      (let [prods (sort-by :nom_art (:articles tckt))
            prods (map (fn [art]
                         (assoc art
                           :price-before-tax (if (= "gvdo" (:type art))
                                               (/ (:precio_venta art)
                                                  (+ 1.0 (/ (:iva art) 100)))
                                               (:precio_venta art))
                           :before-tax (if (= "gvdo" (:type art))
                                               (/ (:total art)
                                                  (+ 1.0 (/ (:iva art) 100)))
                                               (:total art))))
                       prods)
            total (:total tckt)
            pretax-gvdo (reduce (fn [acc nxt]
                                  (+ acc (/ (:total nxt) (+ 1 (/ (:iva nxt) 100)))))
                                0.0
                                (filter #(= "gvdo" (:type %)) prods))
            pretax-exto (reduce + (map :total (filter #(= "exto" (:type %)) prods)))
            tax-gvdo (reduce (fn [acc nxt]
                               (+ acc (- (:total nxt)
                                         (/ (:total nxt) (+ 1 (/ (:iva nxt) 100))))))
                             0.0
                             (filter #(= "gvdo" (:type %)) prods))
            time (util/now-with-time)
            anio (util/now-year)
            saved (bill/save-latest folio time anio prods total pretax-exto pretax-gvdo tax-gvdo)]
        :success))))

(defpage "/tickets/folio/:folio/factura/" {:keys [folio]}
  (if-let [bill (bill-from-ticket folio)]
    (let [content {:title "Factura"
                   :content [:div.container-fluid
                             (bill-form)
                             (javascript-tag
                              "$('form input').first().focus()")]
                   :nav-bar true
                   :active "Ventas"}]
      (main-layout-incl content [:base-css :search-css :jquery :base-js :bill-js :numbers-js]))
    (let [content {:title "Factura"
                   :content [:div.alert.alert-error
                             "Este ticket no existe."]
                   :nav-bar true
                   :active "Ventas"}]
      (home-layout content))))
