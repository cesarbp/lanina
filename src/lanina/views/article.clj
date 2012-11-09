(ns lanina.views.article
  (:use noir.core
        lanina.views.common
        hiccup.form
        [hiccup.element :only [link-to javascript-tag]]
        [lanina.models.utils :only [valid-id?]]
        lanina.utils)
  (:require [lanina.models.article  :as article]
            [lanina.views.utils     :as utils]
            [lanina.models.user     :as users]
            [noir.session           :as session]
            [noir.response          :as resp]
            [lanina.models.logs     :as logs]
            [clj-time.core          :as time]
            [lanina.models.ticket   :as ticket]
            [lanina.models.adjustments :as globals]))

;;; Used by js to get the article in an ajax way
(defpage "/json/article" {:keys [barcode]}
  (let [response (article/get-article barcode)]
    (if (not= "null" response)
      (resp/json response)
      (resp/json {}))))

(defpage "/json/article/starts_with" {:keys [letter]}
  (let [re (re-pattern (str "(?i)^" letter))
        response (article/get-articles-regex re)]
    (if (not= "null" response)
      (resp/json response)
      (resp/json {}))))

(defpage "/json/article-name" {:keys [name]}
  (let [response (article/get-by-name name)]
    (resp/json response)))

(defpage "/json/all-articles" []
  (let [response (article/get-all-only [:codigo :nom_art :date :prev_con :prev_sin])]
    (resp/json response)))

(defpage "/json/all-providers" []
  (let [response (vec (remove #(or (not (string? %)) (empty? %)) (set (map :prov (article/get-all-only [:prov])))))]
    (resp/json response)))

;;; Sales interface
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
       [:a [:p {:style "position:relative;top:7px;"} "F4 - Agregar por nombre de artículo"]]]
      ]]))

(defpartial add-unregistered-form []
  [:div#free-articles.navbar.navbar-fixed-bottom
   [:div.navbar-inner
    [:div.container-fluid
     [:ul.nav
      [:li [:a "Artículos libres"]]
      [:li
       (form-to {:id "unregistered-form" :class "form-inline"} [:get ""]
         (text-field {:class "input-small" :style "position:relative;top:10px;text-align:right;width:40px;" :id "unregistered-quantity" :onkeypress "return unregistered_listener(this,event)" :autocomplete "off" :placeholder "F5"} "unregistered-quantity" "")
         (text-field {:class "input-small" :style "position:relative;top:10px;left:4px;text-align:right" :id "unregistered-price" :onkeypress "return unregistered_listener(this, event)" :autocomplete "off" :placeholder "F7 - Precio"} "unregistered-price")
         )]
      [:li
       [:a [:div.switch.switch-danger {:data-toggle "switch" :data-checkbox "gravado" :data-on "GVDO" :data-off "EXTO"}]]]
      [:li
       [:button.btn.btn-primary {:style "position:relative;top:5px;" :onclick "return add_unregistered()"} "Agregar"]]]]]])

(defpartial item-list []
  [:table {:id "articles-table" :class "table table-condensed"}
   [:tr
    [:th#name-header "Artículo"]
    [:th#quantity-header "Cantidad"]
    [:th#price-header "Precio"]
    [:th#total-header "Total"]
    [:th "Aumentar/Disminuir"]
    [:th "Quitar"]]])

(pre-route "/ventas/" []
  (when-not (users/logged-in?)
    (session/flash-put! :messages '({:type "error" :text "Necesita estar firmado para accesar esta página"}))
    (resp/redirect "/entrar/")))

(defpage "/ventas/" []
  (let [content {:title (str "Ventas, número de ticket: " (ticket/get-next-ticket-number))
                 :content [:div#main.container-fluid (barcode-form) (item-list) (add-unregistered-form)]
                 :footer [:p "Gracias por su compra."]
                 :nav-bar true
                 :active "Ventas"}]
    (main-layout-incl content [:base-css :search-css :switch-css :jquery :jquery-ui :base-js :shortcut :scroll-js :barcode-js :custom-css :subnav-js :switch-js])))

;;; Modify/Add an article
(defn switch-dates [article]
  (let [now (time/now)
        date (str (format "%02d" (time/day now)) "/" (format "%02d" (time/month now)) "/" (format "%02d" (time/year now)))]
    (if (map? article)
      (assoc (assoc article :fech_an (:fech_ac article))
        :fech_ac date)
      (seq (switch-dates (apply array-map (flatten article)))))))

(defpartial iva-select [iva]
  (let [correct-iva (globals/get-current-iva)]
    (if (and (number? iva) (== iva correct-iva))
      [:select {:name :iva}
       [:option {:value correct-iva} correct-iva]
       [:option {:value "0"}  "0"]]
      [:select {:name :iva}
       [:option {:value "0"} "0"]
       [:option {:value correct-iva}  correct-iva]])))

(defpartial lin-select [orig]
  (let [orig (if (seq orig) orig "ABARROTES")
        remaining (vec (clojure.set/difference (set article/lines) #{orig}))
        fst [:select {:name :lin}
             [:option {:value orig} orig]]]
    (reduce (fn [acc line] (into acc [[:option {:value line} line]])) fst remaining)))

(defpartial unit-select [unit]
  (let [orig (if (seq unit) unit "PIEZA")
        remaining (vec (clojure.set/difference (set article/units) #{orig}))
        fst [:select {:name :unidad}
             [:option {:value orig} orig]]]
    (reduce (fn [acc unit] (into acc [[:option {:value unit} unit]])) fst remaining)))

(defpartial modify-article-row [[k v]]
  [:tr.article-row
   [:td.prop-name (article/verbose-names k)]
   (if (= :codigo k)
     [:td.new-value (text-field {:autocomplete "off"} k (str v))]
     [:td.new-value
      (cond
        (= :iva k)
        (if v
          (iva-select v)
          (iva-select (str (globals/get-current-iva))))
        (= :gan k)
        [:div.control-group {:id (str (name k) "-control")}
         (text-field {:class "article-new-value" :autocomplete "off" } (name k) (str v))]
        (= :lin k)
        (lin-select v)
        (= :unidad k)
        (unit-select v)
        (or (= :fech_ac k) (= :fech_an k) (= :nom_art k))
        [:div
         (text-field {:class "disabled" :disabled true
                    :placeholder v} k v)
         (hidden-field k v)]
        :else (text-field {:class "article-new-value" :autocomplete "off"} (name k) (str v)))])
   (when (or (= k :prev_sin) (= k :prev_con))
     [:td
      [:span.help-inline (str "Precio previo: "
                              (if v v "0.00"))]])
   (when (= k :gan)
     [:td
      [:span.help-inline (str "Ganancia previa: " (if v v "0.00"))]])
   (if (or (= k :prev_sin) (= k :prev_con))
     [:td
      [:a.btn {:onclick "return prev_up()"}
       [:i.icon-chevron-up]]
      [:a.btn {:onclick "return prev_down()"}
       [:i.icon-chevron-down]]]
     [:td])])

(defpartial modify-article-table [article type-mod]
  (when (seq article)
    (form-to {:class "form-horizontal" :id "modify-article-form" :name "modify-article"} [:post (str "/articulos/id/" (str (:_id article)) "/modificar/")]
      (hidden-field :type-mod type-mod)
      [:table {:class "table table-condensed"}
       [:tr.table-header
        [:th "Nombre"]
        [:th "Nuevo Valor"]
        [:th]]
       (map modify-article-row (article/sort-by-vec (dissoc article :_id :prev) [:codigo :nom_art :iva :pres :gan :ccj_con :cu_con :prev_con :ccj_sin :cu_sin :prev_sin]))]
      [:fieldset
       [:div.form-actions
        (submit-button {:class "btn btn-warning" :name "submit"} "Modificar")
        (link-to {:class "btn btn-danger"} "/articulos/" "Cancelar")]])))

(defpage "/articulos/id/:_id/modificar/total/" {id :_id}
  (if (valid-id? id)
    (let [article (article/get-by-id id)
          content {:title "Modificando Artículo"
                   :content [:div.container-fluid
                             [:div.subnav [:ul.nav.nav-pills [:li [:a [:h2 (:nom_art article)]]]]]
                             (if article
                               (modify-article-table article :total)
                               [:p.error-notice "No existe tal artículo"])]
                   :active "Artículos"
                   :nav-bar true}]
      (main-layout-incl content [:jquery :base-css :base-js :custom-css :subnav-js :modify-js]))))

(defpartial confirm-changes-row [[k old new]]
  [:tr.article-row
   [:td.prop-name (article/verbose-names k)]
   [:td.orig-value old]
   [:td.new-value new]
   (hidden-field (name k) new)])

(defpartial confirm-changes-table [orig-vals changes type-mod]
  (let [rows (reduce (fn [acc nxt]
                       (into acc [[nxt (nxt orig-vals) (nxt changes)]]))
                     []
                     (keys (dissoc orig-vals :_id)))]
    [:div.article-dialog
     (form-to {:class "form-horizontal" :id "modify-article-form" :name "modify-article"} [:post (str "/articulos/id/" (str (:_id orig-vals)) "/modificar/")]
       (hidden-field :type-mod type-mod)
         [:table {:class "table table-condensed"}
          [:tr.table-header
           [:th "Nombre"]
           [:th "Valor Actual"]
           [:th "Nuevo Valor"]]
          (map confirm-changes-row rows)]
         [:fieldset
          [:div.form-actions
           (submit-button {:class "btn btn-success" :name "submit"} "Confirmar")
           (link-to {:class "btn btn-danger"} "/articulos/" "Cancelar")]])]))

(defpage [:post "/articulos/id/:_id/modificar/"] {:as pst}
  (let [content {:title "Confirmar Cambios"
                 :active "Artículos"}
        article (article/get-by-id (:_id pst))
        id (:_id pst)
        type-mod (:type-mod pst)
        now (time/now)
        date (str (format "%02d" (time/day now)) "/" (format "%02d" (time/month now)) "/" (format "%02d" (time/year now)))]
    (cond 
      (= "Modificar" (:submit pst))
      (let [ks (article/get-keys)
            common-keys (clojure.set/intersection (set (keys article))
                                                  (set (keys pst)))
            usable-orig (map #(vector % (% article)) common-keys)
            usable-new  (map #(vector % (% pst)) common-keys)
            changes
            (reduce into {}
                    (map (fn [orig new]
                           (when (and (not= (second orig) (second new))
                                      (or (not (number? (second orig)))
                                          (try (not= (double (second orig)) (Double. (second new)))
                                               (catch Exception e false))
                                          (try (not= (int (second orig)) (Integer. (second new)))
                                               (catch Exception e false))))
                             {(first new) (second new)}))
                         usable-orig usable-new))
            orig-vals (into
                       (reduce into {}
                               (map (fn [k]
                                     {k (k article)})
                                    (keys changes)))
                       {:_id (:_id pst)})]
        (if (seq changes)
          (home-layout (assoc content :content
                              [:div.container-fluid (confirm-changes-table orig-vals changes type-mod)]))
          (home-layout (assoc content :content
                              [:div.container
                               [:p.alert.alert-warning "No hay cambios para realizar"]
                               [:div.form-actions
                                (link-to {:class "btn btn-success"} "/articulos/" "Regresar")]]))))
      (= "Confirmar" (:submit pst))
      (let [modified (article/update-article (dissoc pst :submit))]
        (if (= :success modified)
          (do (logs/setup!)
              (logs/add-logs! (:_id pst) :updated (dissoc pst :submit) date)
              (session/flash-put! :messages '({:type "alert-success" :text "El artículo ha sido modificado"}))
              (resp/redirect "/articulos/"))
          (do (session/flash-put! :messages (reduce (fn [acc error]
                                                (conj acc {:type "alert-error" :text error}))
                                              [] (map first (vals modified))))
              (resp/redirect (str "/articulos/id/" id "/modificar/" (name type-mod) "/")))))
      :else "Invalid")))

;;; Search for an article
(defpartial search-article-form-js []
  [:script
   "
function redirect_to_add_codnom() {
    var search = $('#search').val();
    if (search.length > 0) {
	var url = '/articulos/agregar/codnom/?busqueda=' + search;
	window.location = url;
    }
    return false;
}"])

(defpartial search-article-form []
  [:div.dialog
   (form-to {:class "form-horizontal" :id "search-form" :name "search-article"} [:get "/articulos/buscar/"]
     [:fieldset
      [:div.control-group
       (label {:class "control-label" :id "search-field"} "busqueda" "Buscar por nombre o código")
       [:div.controls
        (text-field {:id "search" :autocomplete "off"} "busqueda")]]
      [:div.control-group
       (label {:class "control-label"} "provider-name" "Buscar por nombre de proveedor")
       [:div.controls
        (text-field {:id "provider-name" :autocomplete "off"} "provider-name")]]]
     [:div.form-actions
      (submit-button {:class "btn btn-primary" :name "search"} "Consultas, modificaciones y altas parciales")
      (link-to {:class "btn btn-warning"} "/articulos/agregar/" "Alta total de un artículo")])])

(defpage "/articulos/" []
  (let [content {:title "Búsqueda de Artículos"
                 :content [:div.container (search-article-form-js) (search-article-form)]
                 :active "Artículos"
                 :footer [:p "Gracias por visitar."]
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :base-js :jquery-ui :trie-js :search-js])))

(defpartial search-results-row [result]
  (when (seq result)
    (let [{:keys [_id codigo nom_art prev_con prev_sin ccj_sin ccj_con iva]} result
          price (if (= (globals/get-current-iva) ((coerce-to Double) iva)) prev_con prev_sin)
          ccj   (if (= (globals/get-current-iva) ((coerce-to Double) iva)) ccj_con ccj_sin)]
      [:tr.result
       [:td.codigo codigo]
       [:td.nom_art (link-to {:class "search-result-link"} (str "/articulos/id/" _id "/") nom_art)]
       [:td.prev_con  (if (number? ccj) (format "%.2f" (double ccj)) ccj)]
       [:td.prev_sin (if (number? price) (format "%.2f" (double price)) price)]
       [:td.consultar (link-to {:class "btn btn-primary"} (str "/articulos/id/" _id "/global/") "Global")]
       [:td.consultar (link-to {:class "btn btn-primary"} (str "/articulos/id/" _id "/ventas/") "Ventas")]
       [:td.consultar (link-to {:class "btn btn-primary"} (str "/articulos/id/" _id "/proveedor/") "Proveedor")]
       [:td.modificar (link-to {:class "btn btn-success"} (str "/articulos/id/" _id "/modificar/precios/") "Sólo Precios")]
       [:td.modificar (link-to {:class "btn btn-success"} (str "/articulos/id/" _id "/modificar/codigo/") "Código")]
       [:td.modificar (link-to {:class "btn btn-success"} (str "/articulos/id/" _id "/modificar/nombre/") "Nombre")]
       [:td.modificar (link-to {:class "btn btn-success"} (str "/articulos/id/" _id "/modificar/total/") "Total")]
       [:td.eliminar (link-to {:class "btn btn-warning"}  (str "/articulos/agregar/codnom/" _id "/") "Alta por código y nombre")]
       [:td.eliminar (link-to {:class "btn btn-danger"}  (str "/articulos/id/" _id "/eliminar/") "Eliminar")]])))

(defpartial search-results-table [results]
  (if (seq results)
    [:div.container
     [:table {:class "table table-condensed"}
      [:tr
       [:th#barcode-header "Código"]
       [:th#name-header "Artículo"]
       [:th#p-without-header "Costo por caja"]
       [:th#p-with-header "Precio de venta"]
       [:th {:colspan "3"} "Consultas"]
       [:th {:colspan "4"} "Modificaciones"]
       [:th "Altas"]
       [:th "Bajas"]]
      (if (map? results)
        (search-results-row results)
        (map search-results-row results))]
     [:div.form-actions
      (link-to {:class "btn btn-success"} "/articulos/" "Regresar a buscar otro artículo")]]
    [:p {:class "alert alert-error"} "No se encontraron resultados"]))

;;; Needs clean data
(defpartial search-article-results [query]
  (let [data (or (article/get-by-barcode query)
                 (article/get-by-search query))]
    (search-results-table data)))

(defpartial search-art-by-providers [prov]
  (let [data (article/get-by-provider prov)]
    (search-results-table data)))

(defpage "/articulos/buscar/" {:keys [busqueda provider-name submit]}
  (if-let [s (or (seq busqueda) (seq provider-name))]
    (main-layout-incl
     {:title "Resultados de la búsqueda"
      :content [:div.container (if (seq busqueda) (search-article-results (apply str s))
                                   (search-art-by-providers (apply str s)))]
      :nav-bar true
      :active "Artículos"}
     [:base-css :jquery :shortcut :art-res-js])
    (do (session/flash-put! :messages '({:type "alert-error" :text "No introdujo una búsqueda"}))
        (resp/redirect "/articulos/"))))

;;; Delete an article
(defpartial show-article-delete [article]
  (form-to {:class "form form-horizontal"} [:post (str "/articulos/id/" (:_id article) "/eliminar/")]
    [:fieldset
     [:div.control-group
      (label {:class "control-label"} :codigo "Código de barras")
      [:div.controls
       (text-field {:class "disabled" :disabled true
                    :placeholder (:codigo article)} :codigo (:codigo article))]]
     [:div.control-group
      (label {:class "control-label"} :nom_art "Nombre")
      [:div.controls
       (text-field {:class "disabled" :disabled true
                    :placeholder (:nom_art article)} :nom_art (:nom_art article))]]]
    [:div.form-actions
     (submit-button {:class "btn btn-danger"} "Borrar artículo")
     (link-to {:class "btn btn-success"} "/articulos/" "Cancelar")]))

(defpage "/articulos/id/:id/eliminar/" {id :id}
  (let [article (article/get-by-id id)
        content {:title "Borrar un artículo"
                 :content (if (seq article)
                            [:div.container
                             [:h2 "¿Está seguro de que quiere borrar el siguiente artículo?"]
                             (show-article-delete article)
                             [:script "$('input[type=\"submit\"]').focus();"]]
                            [:div.container
                             [:p.alert.alert-error "No existe un artículo con dicha id"]
                             [:div.form-actions
                              (link-to {:class "btn btn-success"} "/articulos/" "Regresar")]])
                 :nav-bar true
                 :active "Artículos"}]
    (main-layout-incl content [:base-css :jquery])))

(defpage [:post "/articulos/id/:id/eliminar/"] {:as post}
  (let [art-name (:nom_art (article/get-by-id (:id post)))
        now (time/now)
        date (str (format "%02d" (time/day now)) "/" (format "%02d" (time/month now)) "/" (format "%02d" (time/year now)))]
    (article/delete-article (:id post))
    (logs/setup!)                       ;remove this
    (logs/add-logs! (:id post) :deleted {} date)
    (session/flash-put! :messages [{:type "alert-success" :text (str "El artículo " art-name " ha sido borrado.")}])
    (resp/redirect "/articulos/")))

;;; View an article
;;; FIXME - for some weird reason this has 2 empty inputs ccj_sin and prev_sin
(defpartial show-different-versions-form [article url]
  (let [dates (map :date (:prev article))]
    (when (seq dates)
      (form-to {:class "form-inline"} [:get url]
        (label :date "Versiones anteriores:")
        [:select {:name :date}
         (map (fn [d]
                [:option {:value (clojure.string/replace d #"/" "-")}
                 d])
              (reverse dates))]
        (submit-button {:class "btn btn-primary"} "Cambiar")))))

(defpartial show-article-tables [article]
  (let [verbose article/verbose-names
        art-split (partition-all (/ (count verbose) 3) article)]
    [:div.row
     [:table.table
      [:tr
       [:th "Nombre"]
       [:th "Valor"]]
      (map (fn [[k v]]
             [:tr {:id (name k)}
              [:td (verbose k)]
              [:td v]])
           article)]]))

(defpartial blink-js [row-id]
  (javascript-tag
   (str
    "while (true) {
    var row_id = '#' + " (name row-id) ";
    setTimeout(function() {
	if ($(row_id).hasClass('info')) {
	    $(row_id).removeClass('info');
	} else {
	    $(row_id).addClass('info');
	}
    }, 400);
}")))

(defpage "/articulos/id/:id/global/" {:as env}
  (let [id (:id env)
        date (:date env)
        article (if (seq date)
                  (dissoc (article/get-by-id-date id (clojure.string/replace date #"-" "/")) :_id)
                  (dissoc (article/get-by-id id) :_id))
        art-no-prevs (article/sort-by-vec (dissoc article :prev) [:codigo :nom_art])
        art-name (:nom_art article)
        iva (== (globals/get-current-iva) (:iva article))
        content {:title (str art-name " | Consulta global")
                 :active "Artículos"
                 :nav-bar true
                 :content [:div.container-fluid
                           (show-different-versions-form article (str "/articulos/id/" id "/global/"))
                           (show-article-tables art-no-prevs)
                           [:div.form-actions (link-to {:class "btn btn-success"}
                                                       (str "/articulos/") "Regresar a buscar artículos")]]}]
    (main-layout-incl content [:base-css :jquery :base-js :verify-js :modify-js])))

(defpage "/articulos/id/:id/ventas/" {id :id}
  (let [article (dissoc (article/get-by-id-only id [:codigo :nom_art :tam :lin :ramo :pres :unidad :ubica :iva :exis :stk :prev_con :prev_sin :fech_an :fech_ac]) :_id :prev)
        art-sorted (article/sort-by-vec (if (= (:iva article) 0)
                                          (dissoc article :prev_con)
                                          (dissoc article :prev_sin))
                                        [:codigo :nom_art :tam :pres :iva :gan])
        content {:title "Consulta para ventas"
                 :active "Artículos"
                 :content [:div.container-fluid (show-article-tables art-sorted)
                           [:div.form-actions (link-to {:class "btn btn-success"}
                                                       (str "/articulos/" ) "Regresar a buscar artículos")]]}]
    (home-layout content)))

(defpage "/articulos/id/:id/proveedor/" {id :id}
  (let [article (dissoc (article/get-by-id-only id [:codigo :nom_art :tam :lin :ramo :pres :unidad :ubica :iva :prov :ccj_sin :ccj_con :cu_sin :cu_con :exis :stk :fech_an :fech_ac]) :_id :prev)
        art-sorted (article/sort-by-vec (if (= (:iva article) 0)
                                          (dissoc article :ccj_con :cu_con)
                                          (dissoc article :ccj_sin :cu_sin))
                                        [:codigo :nom_art])
        content {:title "Consulta para ventas"
                 :active "Artículos"
                 :content [:div.container-fluid (show-article-tables art-sorted)
                           [:div.form-actions (link-to {:class "btn btn-success"}
                                                       (str "/articulos/" ) "Regresar a buscar artículos")]]}]
    (home-layout content)))

;;; Add an article
(defpartial search-add-results-row [result]
  (when (seq result)
    (let [{:keys [_id codigo nom_art prev_con prev_sin]} result]
      [:tr.result
       [:td.codigo codigo]
       [:td.nom_art  nom_art]
       [:td.prev_con prev_con]
       [:td.prev_sin prev_sin]
       [:td (link-to {:class "btn btn-primary"} (str "/articulos/agregar/codnom/" _id "/") "Agregar por código y nombre")]])))

(defpartial search-add-results-table [results]
  (if (seq results)
    [:div.container
     [:table {:class "table table-condensed"}
      [:tr
       [:th#barcode-header "Código"]
       [:th#name-header "Artículo"]
       [:th#p-without-header "Precio sin IVA"]
       [:th#p-with-header "Precio con IVA"]
       [:th {:colspan "2"} "Agregar por"]]
      (if (map? results)
        (search-add-results-row results)
        (map search-add-results-row results))]
     [:div.form-actions
      (link-to {:class "btn btn-success"} "/articulos/" "Regresar a buscar otro artículo")]]
    [:p {:class "alert alert-error"} "No se encontraron resultados"]))

;;; Needs clean data
(defpartial search-add-article-results [query]
  (let [data (or (article/get-by-barcode query)
            (article/get-by-search query))]
    (search-add-results-table data)))

(defpage "/articulos/agregar/codnom/" {:keys [busqueda]}
  (let [title "Resultados para agregar un artículo"
        content {:title "Resultados de la búsqueda"
                 :active "Artículos"
                 :content [:div.container (search-add-article-results busqueda)]}]
    (home-layout content)))

;;; Fixme - 3 partials do sort of the same thing
(defpartial modify-article-form [article to-modify type-mod]
  (let [id (str (:_id article))
        verbose article/verbose-names
        now (time/now)
        date (str (format "%02d" (time/day now)) "/" (format "%02d" (time/month now)) "/" (format "%02d" (time/year now)))]
    (form-to {:class "form form-horizontal"} [:post (str "/articulos/id/" id "/modificar/")]
      (hidden-field :type-mod type-mod)
      [:table.table.table-condensed
       [:fieldset
        (map (fn [[k v]]
               [:div.control-group {:id (str (name k) "-control")}
                (label {:class "control-label"} k (verbose k))
                [:div.controls
                 (cond (or (not (seq to-modify)) (some #{k} to-modify))
                       (cond (= :iva k)
                             (if v
                               (iva-select v)
                               (iva-select (globals/get-current-iva)))
                             (= :stk k)
                             (text-field {:id k :autocomplete "off"} k (if v v "0"))
                             (= :lin k)
                             (lin-select v)
                             (= :unidad k)
                             (unit-select v)
                             (or (= :fech_ac k) (= :fech_an k))
                             [:div
                              (text-field {:class "disabled" :disabled true
                                           :placeholder date} k
                                           (if (= :fech_ac k)
                                             date
                                             (if (seq (:fech_an article))
                                               (:fech_an article) date)))
                              (hidden-field k date)]
                             (or (= :prev_con k) (= :prev_sin k))
                             [:div (text-field {:id k} k (if v v "0.00"))
                              [:span.help-inline (str "Precio previo: " (if v v "0.00"))
                               [:a.btn {:onclick "return prev_up()"}
                                [:i.icon-chevron-up]]
                               [:a.btn {:onclick "return prev_down()"}
                                [:i.icon-chevron-down]]]]
                             (or (= :cu_con k) (= :cu_sin k))
                             [:div
                              (text-field {:class "disabled" :disabled true :autocomplete "off"
                                           :placeholder (if v v "0.00")} k)
                              (hidden-field k (if v v "0.00"))]
                             :else
                             (text-field {:id k :autocomplete "off"} k v))
                       :else
                       [:div (text-field {:class "disabled" :disabled true :placeholder v :autocomplete "off"} k v)
                        (hidden-field k (article k))])]])
             (article/sort-by-vec (dissoc article :_id :prev) [:codigo :nom_art :pres :iva :gan :ccj_con :cu_con :prev_con :ccj_sin :cu_sin :prev_sin]))]]
      [:div.form-actions
       (submit-button {:class "btn btn-warning" :name "submit"} "Modificar")
       (link-to {:class "btn btn-danger"} "/articulos/" "Cancelar y regresar")])))

(defpartial add-article-form [article to-modify]
  (let [verbose article/verbose-names
        now (time/now)
        date (str (format "%02d" (time/day now)) "/" (format "%02d" (time/month now)) "/" (format "%02d" (time/year now)))]
    (form-to {:class "form form-horizontal"} [:post "/articulos/nuevo/"]
      [:table.table.table-condensed
       [:fieldset
        (map (fn [[k v]]
               [:div.control-group {:id (str (name k) "-control")}
                (label {:class "control-label"} k (verbose k))
                [:div.controls
                 (cond (or (not (seq to-modify)) (some #{k} to-modify))
                       (cond (= :iva k)
                             (if v
                               (iva-select v)
                               (iva-select (globals/get-current-iva)))
                             (= :stk k)
                             (text-field {:id k} k (if v v "0"))
                             (= :lin k)
                             (lin-select v)
                             (= :unidad k)
                             (unit-select v)
                             (or (= :fech_ac k) (= :fech_an k))
                             [:div
                              (text-field {:class "disabled" :disabled true
                                           :placeholder date :autocomplete "off"} k
                                           (if (= :fech_ac k)
                                             date
                                             (if (seq (:fech_an article))
                                               (:fech_an article) date)))
                              (hidden-field k date)]
                             (or (= :prev_con k) (= :prev_sin k))
                             [:div (text-field {:id k :autocomplete "off"} k (if v v "0.00"))
                              [:span.help-inline (str "Precio previo: " (if v v "0.00"))
                               [:a.btn {:onclick "return prev_up()"}
                                [:i.icon-chevron-up]]
                               [:a.btn {:onclick "return prev_down()"}
                                [:i.icon-chevron-down]]]]
                             (= :gan k)
                             [:div (text-field {:id k} k (if v v "0.00"))
                              [:span.help-inline (str "Ganancia previa: " (if v v "0.00"))]]
                             (or (= :cu_con k) (= :cu_sin k))
                             [:div
                              (text-field {:class "disabled" :disabled true :autocomplete "off"
                                           :placeholder (if v v "0.00")} k)
                              (hidden-field k (if v v "0.00"))]
                             :else
                             (text-field {:id k :autocomplete "off"} k v))
                       :else
                       [:div (text-field {:class "disabled" :disabled true :placeholder v :autocomplete "off"} k v)
                        (hidden-field k (article k))])]])
             (article/sort-by-vec (dissoc article :_id :prev) [:codigo :nom_art :pres :iva :gan :ccj_con :cu_con :prev_con :ccj_sin :cu_sin :prev_sin]))]]
      [:div.form-actions
       (submit-button {:class "btn btn-primary"} "Agregar este artículo")
       (link-to {:class "btn btn-danger"} "/articulos/" "Cancelar y regresar")])))

(defpage "/articulos/agregar/codnom/:id/" {id :id}
  (let [article (article/get-by-id id)
        to-modify [:codigo :nom_art]
        title     "Alta por código y nombre"
        content {:title title
                 :active "Artículos"
                 :content [:div.container (add-article-form article to-modify)]
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :base-js :jquery-ui :verify-js :modify-js])))


(defpage "/articulos/id/:id/modificar/precios/" {id :id}
  (let [article (article/get-by-id id)
        to-modify (if (== (:iva article) (globals/get-current-iva))
                    [:pres :gan :prev_con :ccj_con] [:pres :gan :prev_sin :ccj_sin])
        content {:title "Modificando precios"
                 :nav-bar true
                 :active "Artículos"
                 :content [:div.container (modify-article-form article to-modify :precios)]}]
    (main-layout-incl content [:base-css :jquery :base-js :verify-js :modify-js])))

(defpage "/articulos/id/:id/modificar/codigo/" {id :id}
  (let [article (article/get-by-id id)
        to-modify [:codigo]
        content {:title "Modificando código"
                 :nav-bar true
                 :active "Artículos"
                 :content [:div.container (modify-article-form article to-modify :codigo)]}]
    (main-layout-incl content [:base-css :jquery :base-js :verify-js :modify-js])))

(defpage "/articulos/id/:id/modificar/nombre/" {id :id}
  (let [article (article/get-by-id id)
        to-modify [:nom_art]
        content {:title "Modificando nombre"
                 :nav-bar true
                 :active "Artículos"
                 :content [:div.container (modify-article-form article to-modify :nombre)
                           [:script "$('#nom_art').focus();"]]}]
    (main-layout-incl content [:base-css :jquery])))

(defpage "/articulos/agregar/" []
  (let [to-modify []
        content {:title "Alta total de un artículo"
                 :active "Artículos"
                 :nav-bar true
                 :content [:div.container (add-article-form (zipmap article/art-props (repeat "")) to-modify)]}]
    (main-layout-incl content [:base-css :jquery :base-js :verify-js :modify-js])))

(defpage [:post "/articulos/nuevo/"] {:as post}
  (let [to-add (dissoc post :_id :prev)
        now (time/now)
        date (str (format "%02d" (time/day now)) "/" (format "%02d" (time/month now)) "/" (format "%02d" (time/year now)))
        added (article/add-article to-add)]
    (if (= :success added)
      (let [new-id (:_id (article/get-by-match to-add))]
        (logs/setup!)                       ;Remove this
        (logs/add-logs! (str new-id) :added to-add date)
        (session/flash-put! :messages '({:type "alert-success" :text "El artículo ha sido agregado."}))
        (resp/redirect "/articulos/"))
      (do
        (session/flash-put! :messages (reduce (fn [acc error]
                                                (conj acc {:type "alert-error" :text error}))
                                              [] (map first (vals added))))
        (resp/redirect (if (:_id post)
                         (str "/articulos/agregar/codnom/" (:_id post) "/")
                         "/articulos/agregar/"))))))

