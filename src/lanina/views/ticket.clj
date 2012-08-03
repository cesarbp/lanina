(ns lanina.views.ticket
  (:use noir.core
        hiccup.form
        lanina.views.common)
  (:require [lanina.models.article :as article]
            [lanina.views.utils :as utils]))

(defpartial ticket-row [prod]
  [:tr
   [:td (:codigo prod)]
   [:td (:nom_art prod)]
   [:td (:precio_unitario prod)]
   [:td (:total prod)]])

(defpartial ticket-table [prods]
  (form-to [:post "/tickets/nuevo/"]
    [:table.table.table-condensed
     [:tr
      [:th "Código de barras"]
      [:th "Nombre de artículo"]
      [:th "Precio unitario"]
      [:th "Total"]]
     (map ticket-row prods)]
    [:div.form-actions
     (submit-button {:class "btn btn-primary"} "Imprimir ticket")]))

(defpage "/tickets/nuevo/" {:as items}
  (let [pairs (zipmap (keys items) (map #(Integer/parseInt %) (vals items)))
        prods (reduce (fn [acc [bc times]]
                        (let [article (article/get-by-barcode bc)
                              name (:nom_art article)
                              price (if (> (:prev_con article) 0.0)
                                      (:prev_con) (:prev_sin article))
                              total (* price times)]
                          
                          (into acc [{:nom_art name :precio_unitario price :total total :codigo bc :cantidad times}])))
                      [] pairs)
        total (reduce + (map :total prods))
        content {:title (str "Total a cobrar: $" (format "%.2f" total))
                 :content [:div.container
                           (ticket-table prods)]
                 :active "Ventas"}]
    (home-layout content)))