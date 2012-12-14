(ns lanina.views.list
  (:use noir.core
        hiccup.form
        lanina.views.common
        [hiccup.element :only [link-to]])
  (:require [lanina.models.logs     :as logs]
            [lanina.models.article  :as article]
            [lanina.models.employee :as employee]
            [clj-time.core          :as time]
            [noir.session           :as session]))

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
    (println (seq arts))
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

(defpartial list-as-printed [employee date barcodes names]
  [:pre.prettyprint.linenums {:style "max-width:235px;"}
   [:ol.linenums {:style "list-style-type:none;"}
    [:p
     [:li {:style "text-align:center;"} "\"L A N I Ñ A\""]
     [:li {:style "text-align:center;"} (str "LISTA PARA EMPLEADO: " (clojure.string/upper-case employee))]
     [:li {:style "text-align:center;"} (str "FECHA: " date)]
     (map (fn [bc name]
            [:p [:li  bc]
             [:li name]])
          barcodes names)]]])

(defpage [:post "/listas/imprimir/"] {:as post}
  (let [remove (:remove post)
        now (time/now)
        date (str (format "%02d" (time/day now)) "/" (format "%02d" (time/month now)) "/" (format "%02d" (time/year now)))
        employee (:employee post)
        ids (read-string (:ids post))
        arts (map article/get-by-id ids)
        barcodes (map :codigo arts)
        names    (map :nom_art arts)
        content {:title "Lista Impresa"
                 :nav-bar true
                 :active "Listas"
                 :content [:div.container-fluid (list-as-printed employee date barcodes names)]}]
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
    [:th#price-header "Precio"]
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
  (letfn [(is-bc [d] (every? (set (map str (range 10)))
                             (rest (clojure.string/split (name d) #""))))
          (is-gvdo [d] (= "gvdo" (clojure.string/lower-case (apply str (take 4 (name d))))))
          (is-exto [d] (= "exto" (clojure.string/lower-case (apply str (take 4 (name d))))))]
    (cond (is-bc denom) (article/get-by-barcode denom)
          (is-gvdo denom) {:codigo "0" :nom_art "ARTÍCULO GRAVADO"
                           :prev_con (Double/parseDouble (clojure.string/replace (clojure.string/replace denom #"gvdo\d+_" "")
                                                                                 #"_" "."))}
          (is-exto denom) {:codigo "0" :nom_art "ARTÍCULO EXENTO"
                           :prev_sin (Double/parseDouble (clojure.string/replace (clojure.string/replace denom #"exto\d+_" "")
                                                                                 #"_" "."))}
          :else (article/get-by-name (clojure.string/replace denom #"_" " ")))))

(defpartial printed-ticket [prods total]
  (let [now (time/now)
        date (str (time/day now) "/" (time/month now) "/" (time/year now))
        t (str (format "%02d" (time/hour now)) ":"
               (format "%02d" (time/minute now)) ":" (format "%02d" (time/sec now)))]
    [:pre.prettyprint.linenums {:style "max-width:250px;"}
     [:ol.linenums {:style "list-style-type:none;"}
      [:p 
       [:li {:style "text-align:center;"} "\"L A N I Ñ A\""]
       [:li {:style "text-align:center;"} "R.F.C: ROHE510827-8T7"]
       [:li {:style "text-align:center;"} "GUERRERO No. 45 METEPEC MEX."]
       [:li {:style "text-align:center;"} (str date " " t)]]
      (map (fn [art] [:p
                      [:li (:nom_art art)]
                      [:li {:style "text-align:right;"}
                       (str (:quantity art) " x "
                            (format "%.2f" (double (:precio_unitario art))) " = "
                            (format "%.2f" (double (:total art))))]])
           prods)
      [:br]
      [:p
       [:li {:style "text-align:right;"}
        (format "SUMA ==> $ %8.2f" (double total))]]]]))

(defpage "/listas/compras/nuevo/" {:as items}
  (let [items (dissoc items :pay)
        pairs (zipmap (keys items) (map #(Integer/parseInt %) (vals items)))
        prods (reduce (fn [acc [bc times]]
                        (let [article (get-article (name bc))
                              name (:nom_art article)
                              type (if (and (:prev_con article) (> (:prev_con article) 0.0))
                                      :gvdo :exto)
                              price (if (and (:prev_con article) (> (:prev_con article) 0.0))
                                      (:prev_con article) (:prev_sin article))
                              total (* price times)
                              art {:type type :quantity times :nom_art name :precio_unitario price :total total :codigo bc :cantidad times}]
                          (into acc [art])))
                      [] pairs)
        total (reduce + (map :total prods))
        content {:title "Lista impresa de compras"
                 :content [:div.container-fluid
                           (printed-ticket prods total)]
                 :active "Listas"}]
    (home-layout content)))