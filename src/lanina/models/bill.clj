(ns lanina.models.bill
  "Respaldo de facturas en base de datos"
  (:use
   somnium.congomongo)
  (:require [lanina.models.utils :as db]
            [lanina.views.utils :as t]
            [lanina.utils :as utils]))

(def bill-coll :bills)

(defn get-next-bill-number []
  (if-let [f (:folio (first (fetch bill-coll :where {:folio {:$ne nil}}
                                   :only [:folio] :sort {:folio -1})))]
    (inc f)
    1))

(defn insert-bill [{:keys [nombre-cliente correo-cliente
                           calle-cliente colonia-cliente municipio-cliente estado-cliente numero-cliente
                           cp-cliente rfc-cliente
                           factura fecha anio no-aprobacion serie-certificado
                           articulos subtotal iva total
                           sello-digital]}]
  (let [folio (get-next-bill-number)]
    (insert! bill-coll
             {:folio folio
              :correo-cliente correo-cliente
              :calle calle-cliente
              :colonia colonia-cliente
              :municipio municipio-cliente
              :estado estado-cliente
              :numero numero-cliente
              :cp cp-cliente
              :nombre-cliente nombre-cliente
              :rfc-cliente rfc-cliente
              :factura factura
              :fecha fecha
              :anio anio
              :no-aprobacion no-aprobacion
              :serie-certificado serie-certificado
              :articulos articulos
              :subtotal subtotal
              :iva iva
              :total total
              :sello-digital sello-digital})
    :success))

(defn save-latest
  [folio time anio arts total pretax-exto pretax-gvdo tax-gvdo]
  (destroy! bill-coll {:latest 1})
  (insert! bill-coll {:latest 1
                      :factura (str folio)
                      :anio (str anio)
                      :fecha (str time)
                      :articulos arts
                      :total total
                      :folio folio
                      :pretax-exto pretax-exto
                      :pretax-gvdo pretax-gvdo
                      :tax-gvdo tax-gvdo}))

(defn get-latest
  []
  (fetch-one bill-coll :where {:latest 1}))

(defn setup!
  []
  (when (collection-exists? bill-coll)
    (println "Dropping coll" bill-coll)
    (drop-coll! bill-coll))
  (println "Creating coll" bill-coll)
  (create-collection! bill-coll))
