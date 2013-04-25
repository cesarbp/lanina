(ns lanina.views.article
  (:use noir.core
        lanina.views.common
        hiccup.form
        [hiccup.element :only [link-to javascript-tag image]]
        [lanina.models.utils :only [valid-id?]]
        lanina.utils)
  (:require [lanina.models.article  :as article]
            [lanina.views.utils     :as utils]
            [lanina.models.user     :as users]
            [noir.session           :as session]
            [noir.response          :as resp]
            [lanina.models.logs     :as logs]
            [lanina.models.ticket   :as ticket]
            [lanina.models.adjustments :as globals]))

;;; Used by js to get the article in an ajax way
(defpage "/json/article" {:keys [barcode]}
  (let [response (article/get-article barcode)]
    (if (not= "null" response)
      (resp/json response)
      (resp/json {}))))

(defpage "/json/article/id" {:keys [id]}
  (let [response (article/get-by-id id)]
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
  (let [response (article/get-all-only [:codigo :nom_art :date :precio_venta :iva])]
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
  [:table {:id "articles-table" :class "table table-condensed table-hover"}
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
  (let [content {:title (str "Ventas, número de ticket: " "<span id=\"ticketn\">"  (ticket/get-next-ticket-number) "</span>")
                 :content [:div#main.container-fluid (barcode-form) (item-list) (add-unregistered-form)]
                 :footer [:p "Gracias por su compra."]
                 :nav-bar true
                 :active "Ventas"}]
    (main-layout-incl content [:base-css :search-css :switch-css :jquery :jquery-ui :base-js :shortcut :scroll-js :barcode-js :custom-css :subnav-js :switch-js])))

;;; Modify/Add an article
(defpartial iva-select [current]
  (let [ivas (set (globals/get-valid-ivas))
        fst (when (globals/valid-iva? current) current (first ivas))
        rst (disj ivas fst)
        lst (cons fst rst)]
    [:select {:name :iva}
     (map (fn [v]
            [:option {:value v} v])
          lst)]))

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

;;; modifiable must be a set of keys
(defpartial modify-article-row-partial [[k v] modifiable]
  (letfn [(std-text [k v] (text-field {:class "article-new-value" :autocomplete "off" :id (name k)} k v))
          (dis-text [k v] (text-field {:class "disabled" :disabled true :placeholder v} k v))]
    [:tr.article-row
     [:td.prop-name (article/verbose-names-new k)]
     [:td.new-value
      (cond
       (not (modifiable k)) (dis-text k v)
       (= :unidad k) (unit-select v)
       (= :lin k) (lin-select v)
       (= :ramo k) (std-text k v)
       (= :iva k) (iva-select v)
       :else (std-text k v))]
     [:td.helper
      (cond
       (and (modifiable k) (= :gan k)) (str "Ganancia previa: " v)
       (and (modifiable k) (= :precio_venta k))
       [:div
        [:a.btn {:onclick "return prev_up();"}
         [:i.icon-chevron-up]]
        [:a.btn {:onclick "return prev_down();"}
         [:i.icon-chevron-down]]
        (str "Precio de venta previo: " v)])]]))

(defpartial modify-article-partial [art type-mod modifiable]
  (form-to {:class "form-horizontal" :id "modify-article-form" :name "modify-article"}
           [:post (str "/articulos/modificar/id/" (:_id art) "/")]
           (hidden-field :type-mod type-mod)
           [:table.table.table-condensed.table-hover
            [:tr
             [:th "Nombre"]
             [:th "Nuevo valor"]
             [:th "Ayudas"]]
            [:fieldset
             (map modify-article-row-partial (article/sort-by-vec (dissoc art :prev) [:codigo :nom_art :iva :pres :gan :costo_unitario :costo_caja :precio_venta]) (repeat modifiable))]]
           [:fieldset
            [:div.form-actions
             (submit-button {:class "btn btn-warning" :name "submit"} "Modificar")
             (link-to {:class "btn btn-danger"} "/articulos/" "Cancelar")]]))

;;; TODO
(defpartial confirm-changes-row [[k old new]]
  [:tr.article-row
   [:td.prop-name (article/verbose-names k)]
   [:td.orig-value old]
   [:td.new-value new]
   (hidden-field (name k) new)])

(defpartial confirm-changes-table [id changes type-mod]
  [:div.article-dialog
   (form-to {:class "form-horizontal" :id "modify-article-form" :name "modify-article"} [:post (str "/articulos/modificar/" id "/")]
            (hidden-field :type-mod type-mod)
            [:table.table.table-condensed.table-hover
             [:tr.table-header
              [:th "Nombre"]
              [:th "Valor Actual"]
              [:th "Nuevo Valor"]]
             (map confirm-changes-row changes)]
            [:fieldset
             [:div.form-actions
              (submit-button {:class "btn btn-success" :name "submit"} "Confirmar")
              (link-to {:class "btn btn-danger"} "/articulos/" "Cancelar")]])])

(defpage "/articulos/modificar/total/:_id/" {id :_id}
  (if (valid-id? id)
    (let [article (article/get-by-id id)
          content {:title "Modificando Artículo"
                   :content [:div.container-fluid
                             [:div.subnav [:ul.nav.nav-pills [:li [:a [:h2 (:nom_art article)]]]]]
                             (if (seq article)
                               (modify-article-partial article :total
                                                       #{:img :unidad :stk :lin :ramo :iva :pres :gan
                                                         :costo_unitario :costo_caja :precio_venta
                                                         :ubica :prov :exis :codigo :nom_art :tam})
                               [:p.error-notice "No existe tal artículo"])
                             [:script "$('#codigo').focus();"]]
                   :active "Artículos"
                   :nav-bar true}]
      (main-layout-incl content [:jquery :base-css :base-js :custom-css :subnav-js :modify-js]))))

(defpage "/articulos/modificar/precios/:id/" {id :id}
  (let [article (article/get-by-id id)
        modifiable #{:pres :gan :iva :precio_venta :costo_caja :costo_unitario}
        content {:title "Modificando precios"
                 :nav-bar true
                 :active "Artículos"
                 :content [:div.container-fluid
                           (if (seq article)
                             (modify-article-partial article :precios modifiable)
                             [:p.error-notice "No existe tal artículo"])
                           [:script "$('#pres').focus();"]]}]
    (main-layout-incl content [:base-css :jquery :base-js :verify-js :modify-js])))

(defpage "/articulos/modificar/codigo/:id/" {id :id}
  (let [article (article/get-by-id id)
        modifiable #{:codigo}
        content {:title "Modificando código"
                 :nav-bar true
                 :active "Artículos"
                 :content [:div.container-fluid
                           (if (seq article)
                             (modify-article-partial article :codigo modifiable)
                             [:p.error-notice "No existe tal artículo"])
                           [:script "$('#codigo').focus();"]]}]
    (main-layout-incl content [:base-css :jquery :base-js :verify-js :modify-js])))

(defpage "/articulos/modificar/nombre/:id/" {id :id}
  (let [article (article/get-by-id id)
        modifiable #{:nom_art}
        content {:title "Modificando nombre"
                 :nav-bar true
                 :active "Artículos"
                 :content [:div.container (modify-article-partial article :nombre modifiable)
                           [:script "$('#nom_art').focus();"]]}]
    (main-layout-incl content [:base-css :jquery :base-js])))

(defpage [:post "/articulos/modificar/:id/"] {:as pst}
  (let [content {:title "Confirmar Cambios"
                 :active "Artículos"}
        article (article/get-by-id (:_id pst))
        id (:id pst)
        type-mod (:type-mod pst)
        date (utils/now)]
    (cond
      (= "Modificar" (:submit pst))
      (let [ks (article/get-keys)
            common-keys (clojure.set/intersection (set (keys article))
                                                  (set (keys pst)))
            usable-orig (map #(vector % (% article)) common-keys)
            usable-new  (map #(vector % (% pst)) common-keys)
            changes (article/find-changes article pst)]
        (if (seq changes)
          (home-layout (assoc content :content
                              [:div.container-fluid (confirm-changes-table id changes type-mod)]))
          (home-layout (assoc content :content
                              [:div.container
                               [:p.alert.alert-warning "No hay cambios para realizar"]
                               [:div.form-actions
                                (link-to {:class "btn btn-success"} "/articulos/" "Regresar")]]))))
      (= "Confirmar" (:submit pst))
      (let [modified (article/update-article (dissoc pst :submit))]
        (if (= :success modified)
          (do (logs/add-logs! (:_id pst) :updated (dissoc pst :submit) date)
              (session/flash-put! :messages '({:type "alert-success" :text "El artículo ha sido modificado"}))
              (resp/redirect "/articulos/"))
          (do (session/flash-put! :messages (reduce (fn [acc error]
                                                (conj acc {:type "alert-error" :text error}))
                                              [] (map first (vals modified))))
              (resp/redirect (str "/articulos/modificar/" id "/" (name type-mod) "/")))))
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

(defpartial search-results-row-employee [result]
  (when (seq result)
    (let [{:keys [_id codigo nom_art precio_venta costo_caja]} result]
      [:tr.result
       [:td.codigo codigo]
       [:td.nom_art (link-to {:class "search-result-link"} (str "/articulos/ventas/" _id "/") nom_art)]
       [:td.prev_con (if (number? costo_caja) (utils/format-decimal costo_caja) costo_caja)]
       [:td.prev_sin (if (number? precio_venta) (utils/format-decimal precio_venta) precio_venta)]
       [:td.consultar (link-to {:class "btn btn-primary"} (str "/articulos/ventas/" _id "/") "Ventas")]])))

(defpartial search-results-table-employee [results]
  (if (seq results)
    [:div.container-fluid
     [:table.table.table-condensed.table-hover
      [:thead
       [:tr
        [:th#barcode-header "Código"]
        [:th#name-header "Artículo"]
        [:th#p-with-header "Precio de venta"]
        [:th "Consultas"]]]
      [:tbody
       (if (map? results)
         (search-results-row-employee results)
         (map search-results-row-employee results))]]
     [:div.form-actions
      (link-to {:class "btn btn-success"} "/articulos/" "Regresar a buscar otro artículo")]]
     [:p {:class "alert alert-error"} "No se encontraron resultados"]))

(defpartial search-results-row-admin [result]
  (when (seq result)
    (let [{:keys [_id codigo nom_art precio_venta costo_caja]} result]
      [:tr.result
       [:td.codigo codigo]
       [:td.nom_art (link-to {:class "search-result-link"} (str "/articulos/global/" _id "/") nom_art)]
       [:td.prev_con (if (number? costo_caja) (utils/format-decimal costo_caja) costo_caja)]
       [:td.prev_sin (if (number? precio_venta) (utils/format-decimal precio_venta))]
       [:td.consultar (link-to {:class "btn btn-primary"} (str "/articulos/global/" _id "/") "Global")]
       [:td.consultar (link-to {:class "btn btn-primary"} (str "/articulos/ventas/" _id "/") "Ventas")]
       [:td.consultar (link-to {:class "btn btn-primary"} (str "/articulos/proveedor/" _id "/") "Proveedor")]
       [:td.modificar (link-to {:class "btn btn-success"} (str "/articulos/modificar/precios/" _id "/") "Sólo Precios")]
       [:td.modificar (link-to {:class "btn btn-success"} (str "/articulos/modificar/codigo/" _id "/") "Código")]
       [:td.modificar (link-to {:class "btn btn-success"} (str "/articulos/modificar/nombre/" _id "/") "Nombre")]
       [:td.modificar (link-to {:class "btn btn-success"} (str "/articulos/modificar/total/" _id "/") "Total")]
       [:td.eliminar (link-to {:class "btn btn-warning"}  (str "/articulos/agregar/codnom/" _id "/") "Alta por código y nombre")]
       [:td.eliminar (link-to {:class "btn btn-danger"}  (str "/articulos/eliminar/" _id "/") "Eliminar")]])))

(defpartial search-results-table-admin [results]
  (if (seq results)
    [:div.container
     [:table.table.table-condensed.table-hover
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
        (search-results-row-admin results)
        (map search-results-row-admin results))]
     [:div.form-actions
      (link-to {:class "btn btn-success"} "/articulos/" "Regresar a buscar otro artículo")]]
    [:p {:class "alert alert-error"} "No se encontraron resultados"]))

;;; Needs clean data
(defpartial search-article-results [query]
  (let [data (or (article/get-by-barcode query)
                 (article/get-by-search query))]
    (if (users/admin?)
      (search-results-table-admin data)
      (search-results-table-employee data))))

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
     [:base-css :jquery :base-js :shortcut :art-res-js])
    (do (session/flash-put! :messages '({:type "alert-error" :text "No introdujo una búsqueda"}))
        (resp/redirect "/articulos/"))))

;;; Delete an article
(defpartial show-article-delete [article]
  (form-to {:class "form form-horizontal"} [:post (str "/articulos/eliminar/" (:_id article) "/")]
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

(defpage "/articulos/eliminar/:id/" {id :id}
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
    (main-layout-incl content [:base-css :jquery :base-js])))

(defpage [:post "/articulos/eliminar/:id/"] {:as post}
  (let [art-name (:nom_art (article/get-by-id (:id post)))
        date (utils/now)]
    (article/delete-article (:id post))
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
                [:option {:value d}
                 d])
              (reverse dates))]
        (submit-button {:class "btn btn-primary"} "Cambiar")))))

(defpartial show-article-tables [article]
  (let [verbose article/verbose-names-new
        art-split (partition-all (/ (count verbose) 3) article)
        double? (fn [v] (= java.lang.Double (class v)))
        iva (if (and (number? (:iva article)) (< 0 (:iva article)))
              true false)
        row (fn [[k v]]
              [:tr {:id (name k)}
               [:td (if (or (= :cu_extra k) (= :ccj_extra k) (= :prev_extra k))
                      ((article/verbose-extra-fields iva) k)
                      (verbose k))]
               [:td (cond (= :img k) (link-to (str "/imagenes/" v "/") v)
                          (double? v) (format "%.2f" v)
                          :else v)]])]
    [:div.row
     [:table.table
      [:tr
       [:th "Nombre"]
       [:th "Valor"]]
      (map row article)]]))

(defpartial highlight-js [row-id]
  (javascript-tag
   (str
    "$('#" (name row-id) "').addClass('info');")))

(defpage "/articulos/global/:id/" {:as env}
  (let [id (:id env)
        date (:date env)
        article (if (seq date)
                  (article/get-by-id-date id date)
                  (article/get-by-id id))
        article-extras (article/add-fields article)
        art-name (:nom_art article)
        art-no-prevs (article/sort-by-vec (dissoc article-extras :prev)
                                          [:_id :codigo :nom_art :img :pres :iva :gan :costo_unitario :costo_caja :precio_venta :cu_extra :ccj_extra :prev_extra])
        content {:title (str art-name " | Consulta global")
                 :active "Artículos"
                 :nav-bar true
                 :content [:div.container-fluid
                           (show-different-versions-form article (str "/articulos/global/" id "/"))
                           (show-article-tables art-no-prevs)
                           [:div.form-actions (link-to {:class "btn btn-success"}
                                                       (str "/articulos/") "Regresar a buscar artículos")]
                           (highlight-js :precio_venta)]}]
    (main-layout-incl content [:base-css :jquery :base-js])))

(defpage "/articulos/ventas/:id/" {id :id date :date}
  (let [article (if date
                  (article/get-by-id-date id date)
                  (article/get-by-id id))
        article (dissoc article :costo_unitario :costo_caja :gan :_id)
        article (article/add-fields article)
        art-no-prevs (dissoc article :prev)
        art-sorted (article/sort-by-vec art-no-prevs
                                        [:codigo :nom_art :img :pres :iva :gan :precio_venta :prev_extra :tam])
        content {:title "Consulta para ventas"
                 :active "Artículos"
                 :content [:div.container-fluid (show-article-tables art-sorted)
                           (show-different-versions-form article (str "/articulos/ventas/" id "/"))
                           [:div.form-actions (link-to {:class "btn btn-success"}
                                                       (str "/articulos/" ) "Regresar a buscar artículos")]
                           (highlight-js :precio_venta)]}]
    (home-layout content)))

(defpage "/articulos/proveedor/:id/" {id :id date :date}
  (let [article (if date
                  (article/get-by-id-date id date)
                  (article/get-by-id id))
        article (dissoc article :precio_venta :_id :gan)
        article (article/add-fields article)
        art-no-prevs (dissoc article :prev)
        art-sorted (article/sort-by-vec art-no-prevs
                                        [:codigo :nom_art :pres :prov :iva :costo_unitario :costo_caja :cu_extra :ccj_extra :date])
        content {:title "Consulta para ventas"
                 :active "Artículos"
                 :content [:div.container-fluid
                           (show-different-versions-form article (str "/articulos/proveedor/" id "/"))
                           (show-article-tables art-sorted)

                           [:div.form-actions (link-to {:class "btn btn-success"}
                                                       "/articulos/"  "Regresar a buscar artículos")]
                           (highlight-js :costo_caja)]}]
    (home-layout content)))

;;; Add an article
(defpartial search-add-results-row [result]
  (when (seq result)
    (let [{:keys [_id codigo nom_art iva precio_venta]} result]
      [:tr.result
       [:td.codigo codigo]
       [:td.nom_art  nom_art]
       [:td.iva iva]
       [:td.precio_venta precio_venta]
       [:td (link-to {:class "btn btn-primary"} (str "/articulos/agregar/codnom/" _id "/") "Agregar por código y nombre")]])))

(defpartial search-add-results-table [results]
  (if (seq results)
    [:div.container
     [:table {:class "table table-condensed"}
      [:tr
       [:th#barcode-header "Código"]
       [:th#name-header "Artículo"]
       [:th#iva "IVA"]
       [:th#price-header "Precio de venta"]
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

(defpartial add-article-form [article modifiable]
  (let [verbose article/verbose-names
        date (utils/now)]
    (form-to {:class "form form-horizontal"} [:post "/articulos/nuevo/"]
      [:table.table.table-condensed
       [:tr
        [:th "Nombre de campo"]
        [:th "Valor"]
        [:th "Ayudas"]]
       [:fieldset
        (map modify-article-row-partial
             (article/sort-by-vec (dissoc article :_id :prev) [:codigo :nom_art :pres :iva :gan :ccj_con :cu_con :prev_con :ccj_sin :cu_sin :prev_sin])
             (repeat modifiable))]]
      [:div.form-actions
       (submit-button {:class "btn btn-primary"} "Agregar este artículo")
       (link-to {:class "btn btn-danger"} "/articulos/" "Cancelar y regresar")])))

(defpage "/articulos/agregar/codnom/:id/" {id :id}
  (let [article (article/get-by-id id)
        modifiable #{:codigo :nom_art}
        title     "Alta por código y nombre"
        content {:title title
                 :active "Artículos"
                 :content [:div.container (add-article-form article modifiable)]
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :base-js :jquery-ui :verify-js :modify-js])))

(defpage "/articulos/agregar/" []
  (let [modifiable #{:img :unidad :stk :lin :ramo :iva :pres :gan :costo_unitario :costo_caja :precio_venta :ubica :prov :exis :codigo :nom_art :tam}
        order [:codigo :nom_art :img :pres :iva :gan :costo_unitario :costo_caja :precio_venta :unidad :lin :ramo :prov]
        content {:title "Alta total de un artículo"
                 :active "Artículos"
                 :nav-bar true
                 :content [:div.container (add-article-form
                                           (article/sort-by-vec (dissoc (article/map-to-article {}) :prev)
                                                                order)
                                                            modifiable)]}]
    (main-layout-incl content [:base-css :jquery :base-js :verify-js :modify-js])))

(defpage [:post "/articulos/nuevo/"] {:as post}
  (let [to-add (dissoc post :_id :prev)
        date (utils/now)
        added (article/add-article to-add)]
    (if (= :success added)
      (let [new-id (:_id (article/get-by-match to-add))]
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


;;; Article images
(defpartial show-image [img-path]
  [:div.container-fluid
   [:a {:href img-path :style "text-align:center;"}]
   (image {:class "media-object"} img-path)])

(defpage "/imagenes/:name/" {:keys [referrer name]}
  (let [full-path (str (globals/full-image-path name))
        title (if referrer
                (str
                 (:nom_art (article/get-by-id-only [:nom_art]))
                 " | " name)
                (str "Imagen " name))
        content {:title title
                 :active "Artículos"
                 :nav-bar true
                 :content (show-image (str (globals/get-image-path) name))}]
    (home-layout content)))

(defpage "/articles/reset/" []
  (article/setup!)
  "done!")
