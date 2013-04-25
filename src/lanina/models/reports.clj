(ns lanina.models.reports
  (:require [lanina.models.ticket :as ticket]
            [lanina.views.utils :as t :only [now]]))

(defn gen-report-data
  ([from] (gen-report-data from (t/now)))
  ([from to]
      (let [tickets (ticket/search-by-date-range from to)
            total (atom 0.0)
            rows (atom [])
            total-exto (atom 0.0)
            total-gvdo (atom 0.0)
            count (atom 0)
            count-individual (atom 0)
            add-row #(swap! rows conj %)
            inc-total #(swap! total + %)
            inc-exto #(swap! total-exto + %)
            inc-gvdo #(swap! total-gvdo + %)
            verbose ["Fecha" "Hora" "Nombre" "Código" "IVA" "Precio" "Cantidad" "Total"]]
        (doseq [t tickets a (:articles t)]
          (add-row [(:date t) (:time t) (:nom_art a) (:codigo a) (:iva a) (:precio_venta a)
                    (:quantity a) (:total a)])
          (inc-total (:total a))
          (swap! count-individual inc)
          (swap! count + (:quantity a))
          (if (== 0 (:iva a))
            (inc-exto (:total a))
            (inc-gvdo (:total a))))
        {:count @count :count-individual @count-individual :total-exto @total-exto :total-gvdo @total-gvdo
         :total @total :rows @rows :verbose verbose :from from :to to})))
