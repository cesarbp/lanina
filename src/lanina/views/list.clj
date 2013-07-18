(ns lanina.views.list
  (:use noir.core
        hiccup.form
        lanina.views.common
        [hiccup.element :only [link-to]]
        [lanina.views.utils :only [now now-hour]])
  (:require [lanina.models.logs     :as logs]
            [lanina.models.article  :as article]
            [lanina.models.employee :as employee]
            [clj-time.core          :as time]
            [lanina.views.utils :refer [now]]
            [noir.session           :as session]
            [lanina.models.shopping :as shop]
            [noir.response         :as resp]
            [lanina.models.adjustments :as settings]
            [lanina.models.printing :as printing]))

(defpartial art-row [art]
  [:tr
   [:td (:codigo art)]
   [:td (:nom_art art)]
   [:td (text-field {:autocomplete "off" :class "input-small" :placeholder "##"} (:_id art))]])

(defpartial art-table [arts]
  (form-to [:post "/listas/nueva/"]
      [:table.table.table-condensed
       [:tr
        [:th "Código"]
        [:th "Nombre"]
        [:th "Número"]]
       (map art-row arts)
       [:tr
        [:div.form-actions
         (submit-button {:class "btn btn-primary"} "Continuar")]]]))

(defpage "/listas/" []
  (let [logs (filter (fn [l] (and (not (:cleared l))
                                  (or (= "updated" (:type l))
                                      (= "added"  (:type l)))))
                     (logs/retrieve-all))
        arts (remove nil? (map (fn [l] (article/get-by-id-only (:art-id l) [:nom_art :codigo]))
                               logs))
        content {:title "Listas"
                 :content [:div.container-fluid
                           (if (seq arts)
                             (art-table arts)
                             [:p {:class "alert alert-error"} "No hay listas para procesar"])
                           [:script "$('input:eq(1)').focus();"]]
                 :nav-bar true
                 :active "Listas"}]
    (main-layout-incl content [:base-css :jquery :base-js])))

(defpartial assign-employees-row [[n ids]]
  (let [employees employee/employee-list
        arts (map (fn [id] (article/get-by-id-only id [:nom_art :codigo]))
                  ids)]
    (form-to [:post "/listas/imprimir/"]
        [:table.table.table-condensed
         [:tr
          [:th "Código"]
          [:th "Nombre"]]
         (map (fn [art] [:tr
                         [:td (:codigo art)]
                         [:td (:nom_art art)]]) arts)
         [:div.form-actions
          [:p (str "Grupo " n)]
          (label {:class "control-label"} "employee" "Nombre de empleado")
          (text-field {:autocomplete "off" :class "inline"} "employee")
          [:label.checkbox.inline {:class "checkbox inline" :style "position:relative;left:10px;"}
           (check-box :remove false)
           "Quitar también los artículos"]
          (hidden-field :ids (seq (map :_id arts)))
          [:br]
          (submit-button {:class "btn btn-primary"} "Imprimir")]])))

(defpage [:post "/listas/nueva/"] {:as pst}
  (let [grouped (reduce
                 (fn [acc [id n]]
                   (update-in acc [n]
                              (fn [old new] (if (seq old) (conj old new) [new]))
                              id))
                 {}
                 pst)
        content {:title "Crear una lista"
                 :content [:div.container-fluid
                           (map assign-employees-row grouped)
                           [:script "$($('input[type=\"text\"]')[0]).focus();"]
                           [:div.form-actions
                            (link-to {:class "btn btn-danger"} "/listas/" "Cancelar")]]
                 :nav-bar true
                 :active "Listas"}]
    (main-layout-incl content [:base-css :jquery :base-js])))

(defpage "/logs/clear/" []
  (logs/remove-logs)
  "done!")

(defpartial list-as-printed [employee date barcodes names prices]
  [:pre.prettyprint.linenums {:style "max-width:235px;"}
   [:ol.linenums {:style "list-style-type:none;"}
    [:p
     [:li {:style "text-align:center;"} "\"L A N I Ñ A\""]
     [:li {:style "text-align:center;"} (str "LISTA PARA EMPLEADO: " (clojure.string/upper-case employee))]
     [:li {:style "text-align:center;"} (str "FECHA: " date)]
     (map (fn [bc price name]
            [:p [:li bc " " price]
             [:li name]])
          barcodes prices names)]]])

(defpage [:post "/listas/imprimir/"] {:as post}
  (let [remove (:remove post)
        date (now)
        employee (:employee post)
        ids (read-string (:ids post))
        arts (sort-by :nom_art (map article/get-by-id ids))
        barcodes (map :codigo arts)
        names    (map :nom_art arts)
        prices (map :precio_venta arts)
        content {:title "Lista Impresa"
                 :nav-bar true
                 :active "Listas"
                 :content [:div.container-fluid (list-as-printed employee date barcodes names prices)]}]
    (printing/print-employee-list arts date employee)
    (when remove
      (doseq [id ids]
        (logs/remove-log! id))
      (session/flash-put! :messages '({:type "alert-success" :text "Los artículos han sido quitados del registro."})))
    (home-layout content)))

;;; Shopping list
(defpartial barcode-form []
  (form-to {:id "barcode-form" :class "form-inline"} [:get ""]
    [:div.subnav
     [:ul.nav.nav-pills
      [:li [:h2#total "Total: 0.00"]]
      [:li
       (text-field {:class "input-small" :style "position:relative;top:14px;text-align:right;width:40px;" :id "quantity-field" :onkeypress "return quantity_listener(this, event)" :autocomplete "off" :placeholder "F10"} "quantity")]
      [:li
       (text-field {:class "input-small" :style "position:relative;top:14px;left:2px;text-align:right" :id "barcode-field" :onkeypress "return barcode_listener(this, event)" :autocomplete "off" :placeholder "F3 - Código"} "barcode")]
      [:li
       [:a [:p {:style "position:relative;top:7px;"} "F4 - Agregar por nombre de artículo"]]]]]))

(defpartial item-list []
  [:table {:id "articles-table" :class "table table-condensed"}
   [:tr
    [:th#name-header "Artículo"]
    [:th#quantity-header "Cantidad"]
    [:th#price-header "Costo caja"]
    [:th#total-header "Total"]
    [:th "Aumentar/Disminuir"]
    [:th "Quitar"]]])

(defpartial save-restore-links []
  [:div
   [:a {:onclick "restore_progress();" :class "btn btn-primary"} "Usar la última versión guardada"]
   [:a {:onclick "save_progress();" :class "btn btn-success"} "Guardar la lista de compras"]])

(defpage "/listas/compras/" []
  (let [content {:title (str "Lista para compras")
                 :content [:div
                           (save-restore-links)
                           [:div#main.container-fluid (barcode-form) (item-list)]]
                 :footer [:p "Gracias por su compra."]
                 :nav-bar true
                 :active "Listas"}]
    (main-layout-incl content [:base-css :search-css :switch-css :jquery :jquery-ui :base-js :shortcut :scroll-js :list-js :custom-css :subnav-js :switch-js])))

(defn get-article [denom]
  (article/get-by-id denom))

(defn sanitize-ticket [items]
  (reduce (fn [acc [id quant]]
            (try (Integer/valueOf quant)
                 (conj acc [id (Integer/valueOf quant)])
                 (catch Exception e
                   acc)))
          []
          items))

(defn fetch-prods
  [pairs]
  (let [boxes (atom 0)
        n-arts (atom 0)
        grand-total (atom 0)
        prods
        (reduce (fn [acc [bc times]]
                  (let [article (get-article (name bc))
                        name (:nom_art article)
                        type (if (settings/valid-iva? (:iva article))
                               (if (< 0 (:iva article))
                                 "gvdo"
                                 "exto")
                               "exto")
                        price (:costo_caja article)
                        total (if (number? price) (* price times) 0.0)
                        art {:type type :iva (:iva article) :quantity times :_id bc :codigo (:codigo article)
                             :nom_art name :costo_caja price :costo_unitario (:costo_unitario article)
                             :total total :pres (:pres article) :lin (:lin article) :prov (:prov article)
                             :ramo (:ramo article)}]
                    (swap! boxes + (:quantity art))
                    (swap! n-arts inc)
                    (swap! grand-total + (:total art))
                    (conj acc art)))
                [] pairs)]
    {:boxes @boxes :n-arts @n-arts :prods prods :total @grand-total}))

(defpage "/listas/compras/nuevo/" {:as items}
  (let [pairs (sanitize-ticket items)
        {:keys [prods boxes n-arts total]} (fetch-prods pairs)
        prods (sort-by :nom_art prods)
        date (now)
        time (now-hour)]
    (shop/insert-purchase! prods date time)
    (printing/print-purchase prods boxes total n-arts date)
    (resp/redirect "/listas/compras/")))
