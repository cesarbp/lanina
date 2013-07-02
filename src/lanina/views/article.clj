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
            [lanina.models.adjustments :as globals]
            [lanina.models.cashier :refer [cashier-is-open?]]))

;;; Used by js to get the article in an ajax way
(defpage "/json/article" {:keys [barcode]}
  (let [response (article/get-by-barcode barcode)]
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
    [:div.navbar.navbar-inverse
     [:div.navbar-inner
      [:div.container-fluid
       [:ul#subnav.nav
        [:li [:h3#total {:style "color:white;"} "Total: 0.00"]]
        [:li.divider-vertical]
        [:li [:h3#number {:style "color:white;"} "#arts: 0"]]
        [:li.divider-vertical]
        [:li
         (text-field {:class "input-small" :style "position:relative;top:14px;text-align:right;width:40px;" :id "quantity-field" :onkeypress "return quantity_listener(this, event)" :autocomplete "off" :placeholder "F10 #"} "quantity")]
        [:li
         (text-field {:class "input-small" :style "position:relative;top:14px;left:2px;text-align:right" :id "barcode-field" :onkeypress "return barcode_listener(this, event)" :autocomplete "off" :placeholder "F3 - Código"} "barcode")]
        [:li
         [:a {:style "color:white;position:relative;top:8px;"} "F4 - por nombre"]]]]]]))

(defpartial add-unregistered-form []
  [:div#free-articles.navbar.navbar-fixed-bottom
   [:div.navbar-inner
    [:div.container-fluid
     [:ul.nav
      [:li [:a "Artículos libres"]]
      [:li
       (text-field {:class "input-small" :style "position:relative;top:10px;text-align:right;width:40px;" :id "unregistered-quantity" :onkeypress "return unregistered_listener(this, event)" :autocomplete "off" :placeholder "F5 #"} "unregistered-quantity" "")]
      [:li
       (text-field {:class "input-small" :style "position:relative;top:10px;left:4px;text-align:right" :id "unregistered-price" :onkeypress "return unregistered_listener(this, event)" :autocomplete "off" :placeholder "F6 - Precio"} "unregistered-price")]
      [:li
       (text-field {:class "input-small" :style "position:relative;top:10px;left:4px;text-align:right" :id "unregistered-name" :onkeypress "return unregistered_listener(this, event)" :autocomplete "off" :placeholder "F7 - Nombre"} "unregistered-price")]
      [:li
       [:a [:span {:style "position:relative;bottom:8px;"} "F8"] [:div.switch.switch-danger {:data-toggle "switch" :data-checkbox "gravado" :data-on "GVDO" :data-off "EXTO"}]]]
      [:li
       [:button.btn.btn-primary {:style "position:relative;top:5px;" :onclick "return add_unregistered()"} "Agregar"]]
]]]])

(defpartial item-list []
  [:table {:id "articles-table" :class "table table-condensed table-hover"}
   [:thead
    [:tr
     [:th#name-header "Artículo"]
     [:th#quantity-header "Cantidad"]
     [:th#price-header "Precio"]
     [:th#total-header "Total"]
     [:th "Aumentar/Disminuir"]
     [:th "Quitar"]]]
   [:tbody]])

(pre-route "/ventas/" []
  (when-not (users/logged-in?)
    (session/flash-put! :messages '({:type "error" :text "Necesita estar firmado para accesar esta página"}))
    (resp/redirect "/entrar/")))

(defpage "/ventas/" []
  (if (cashier-is-open?)
    (let [content {:title (str "Ventas, Ticket: <span id=\"ticketn\">"  (ticket/get-next-ticket-number) "</span> Folio: " (ticket/get-next-folio))
                   :content [:div#main.container-fluid (barcode-form) (item-list) (add-unregistered-form)]
                   :footer [:p "Gracias por su compra."]
                   :nav-bar true
                   :active "Ventas"}]
      (main-layout-incl content [:base-css :search-css :switch-css :jquery :jquery-ui :base-js :shortcut :scroll-js :barcode-js :custom-css :switch-js]))
    (let [content {:title "Ventas"
                   :content [:div.container-fluid
                             [:div.alert.alert-error
                              [:h2 "La caja no está abierta"]
                              (link-to {:class "btn"} "/caja/" "Ir a la caja")]]
                   :nav-bar true
                   :active "Ventas"}]
      (home-layout content))))

;;; Modify/Add an article
(defpartial iva-select [current]
  (let [ivas (set (globals/get-valid-ivas))
        fst (if (globals/valid-iva? current) current (first ivas))
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

;;; TODO - find a better way to use the prev stuff
;;; modifiable must be a set of keys
(defpartial modify-article-row-partial [[k v] modifiable & [prev]]
  (letfn [(std-text [k v] (text-field {:class "article-new-value" :autocomplete "off" :id (name k)}
                                      k
                                      (or (k prev) v)))
          (dis-text [k v] (text-field {:class "disabled" :disabled true :placeholder v} k v))]
    [:tr.article-row
     [:td.prop-name (article/verbose-names-new k)]
     [:td.new-value
      (cond
       (not (modifiable k)) (dis-text k v)
       (= :unidad k) (unit-select (or (k prev) v))
       (= :lin k) (lin-select (or (k prev) v))
       (= :iva k) (iva-select (or (k prev) v))
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

(defpartial modify-article-partial [art type-mod modifiable & [prev]]
  [:div
   (when-not (empty? (:errors (article/errors-warnings art)))
     (let [next-url (str "/articulos/modificar/"
                         (case type-mod
                           :total "total/"
                           :precios "precios/"
                           :codigo "codigo/"
                           :nombre "nombre/")
                         (:_id art)
                         "/")]
       [:div.alert.alert-error
        [:p "Este artículo contiene errores, se recomienda corregirlos antes de intentar modificar el artículo en este módulo"]
        (link-to {:class "btn"} (str "/ajustes/errores/" (:_id art) "/?next=" next-url) "Corregir errores")]))
   (form-to {:class "form-horizontal" :id "modify-article-form" :name "modify-article"}
            [:post (str "/articulos/modificando/" (:_id art) "/")]
            (hidden-field :type-mod type-mod)
            [:table.table.table-condensed.table-hover
             [:tr
              [:th "Nombre"]
              [:th "Nuevo valor"]
              [:th "Ayudas"]]
             [:fieldset
              (map modify-article-row-partial (article/sort-by-vec (dissoc art :prev) [:codigo :nom_art :iva :pres :gan :costo_unitario :costo_caja :precio_venta]) (repeat modifiable) (repeat prev))]]
            [:fieldset
             [:div.form-actions
              (submit-button {:class "btn btn-warning" :name "submit"} "Modificar")
              (link-to {:class "btn btn-danger"} "/articulos/" "Cancelar")]])])

;;; TODO
(defpartial confirm-changes-row [[k old new]]
  [:tr.article-row
   [:td.prop-name (article/verbose-names-new k)]
   [:td.orig-value old]
   [:td.new-value new]
   (hidden-field (name k) new)])

(defpartial confirm-changes-table [id changes type-mod]
  [:div.article-dialog
   (form-to {:class "form-horizontal" :id "modify-article-form" :name "modify-article"} [:post (str "/articulos/modificando/" id "/")]
            (hidden-field :type-mod type-mod)
            [:table.table.table-condensed.table-hover
             [:tr.table-header
              [:th "Nombre"]
              [:th "Valor Actual"]
              [:th "Nuevo Valor"]]
             (map confirm-changes-row changes)]
            [:fieldset
             [:div.form-actions
              (submit-button {:class "btn btn-success" :name "submit" :value "Confirmar"} "Confirmar")
              (link-to {:class "btn btn-danger"} "/articulos/" "Cancelar")]])])

(def submit-on-enter-js
  (javascript-tag
   "$('body').keypress(function(event) {
  var kc = event.keyCode || event.which;
  if ( kc == 13 )
    $('input[type=submit]').click();
});"))

(defpage "/articulos/modificar/total/:id/" {id :id :as prev}
  (if (valid-id? id)
    (let [article (article/get-by-id id)
          content {:title "Modificando Artículo"
                   :content [:div.container-fluid
                             (if (seq article)
                               (modify-article-partial article :total
                                                       #{:img :unidad :stk :lin :ramo :iva :pres :gan
                                                         :costo_unitario :costo_caja :precio_venta
                                                         :ubica :prov :exis :codigo :nom_art :tam}
                                                       prev)
                               [:p.error-notice "No existe tal artículo"])
                             [:script "$('#codigo').focus();"]
                             submit-on-enter-js]
                   :active "Artículos"
                   :nav-bar true}]
      (main-layout-incl content [:jquery :base-css :base-js :custom-css :subnav-js :modify-js]))))

(defpage "/articulos/modificar/precios/:id/" {id :id :as prev}
  (let [article (article/get-by-id id)
        modifiable #{:pres :gan :iva :precio_venta :costo_caja :costo_unitario}
        content {:title "Modificando precios"
                 :nav-bar true
                 :active "Artículos"
                 :content [:div.container-fluid
                           (if (seq article)
                             (modify-article-partial article :precios modifiable prev)
                             [:p.error-notice "No existe tal artículo"])
                           [:script "$('#pres').focus();"]
                           submit-on-enter-js]}]
    (main-layout-incl content [:base-css :jquery :base-js :verify-js :modify-js])))

(defpage "/articulos/modificar/codigo/:id/" {id :id :as prev}
  (let [article (article/get-by-id id)
        modifiable #{:codigo}
        content {:title "Modificando código"
                 :nav-bar true
                 :active "Artículos"
                 :content [:div.container-fluid
                           (if (seq article)
                             (modify-article-partial article :codigo modifiable prev)
                             [:p.error-notice "No existe tal artículo"])
                           [:script "$('#codigo').focus();"]]}]
    (main-layout-incl content [:base-css :jquery :base-js :verify-js :modify-js])))

(defpage "/articulos/modificar/nombre/:id/" {id :id :as prev}
  (let [article (article/get-by-id id)
        modifiable #{:nom_art}
        content {:title "Modificando nombre"
                 :nav-bar true
                 :active "Artículos"
                 :content [:div.container (modify-article-partial article :nombre modifiable prev)
                           [:script "$('#nom_art').focus();"]
                           submit-on-enter-js]}]
    (main-layout-incl content [:base-css :jquery :base-js])))

(defpage [:post "/articulos/modificando/:id/"] {:as pst}
  (let [content {:title "Confirmar Cambios"
                 :active "Artículos"}
        article (article/get-by-id (:id pst))
        id (:id pst)
        type-mod (:type-mod pst)
        date (utils/now)
        redirect-url (str "/articulos/modificar/" (case (keyword type-mod)
                                                    :precios "precios/"
                                                    :codigo "codigo/"
                                                    :nombre "nombre/"
                                                    :total "total/"))]

    (cond
     (= "Modificar" (:submit pst))
     (let [changes (article/find-changes article (dissoc pst :submit))]
       (if (seq changes)
         (home-layout (assoc content :content
                             [:div.container-fluid (confirm-changes-table id changes type-mod)
                              submit-on-enter-js]))
         (home-layout (assoc content :content
                             [:div.container
                              [:p.alert.alert-warning "No hay cambios para realizar"]
                              [:div.form-actions
                               (link-to {:class "btn btn-success"
                                         :id "return"}
                                        redirect-url
                                        "Regresar")]
                              (javascript-tag
                               "$('body').keypress(function(event) {
  var kc = event.keyCode || event.which;
  if ( kc == 13 )
    window.location = $('#return').attr('href');
});")]))))
     (= "Confirmar" (:submit pst))
     (let [modified (article/update-article! id (dissoc pst :submit))]
       (if (= :success modified)
         (do (logs/add-logs! (:_id pst) :updated (dissoc pst :submit) date)
             (session/flash-put! :messages '({:type "alert-success" :text "El artículo ha sido modificado"}))
             (resp/redirect redirect-url))
         (do (session/flash-put! :messages (for [[k es] modified e es]
                                             {:type "alert-error" :text e}))
             (render (str "/articulos/modificar/" (name type-mod) "/" id "/") pst))))
     :else "Invalid")))

;;; Search for an article
(defpartial search-article-provider-js []
  [:script
   ";$('#provider-name').focus();"])

(defpartial search-article-provider-form []
  [:div.dialog
   (form-to {:class "form-horizontal" :id "search-form" :name "search-article"} [:get "/articulos/buscar/"]
     [:fieldset
      (when (users/admin?)
        [:div.control-group
         (label {:class "control-label"} "provider-name" "Buscar por nombre de proveedor")
         [:div.controls
          (text-field {:id "provider-name" :autocomplete "off"} "provider-name")]])]
     [:div.form-actions
      (submit-button {:class "btn btn-primary" :name "search"} (if (users/admin?) "Consultas, modificaciones y altas parciales" "Consultas"))])])

(defpage "/articulos/buscar/proveedor/" []
  (let [content {:title "Búsqueda de artículos por proveedor"
                 :content [:div.container (search-article-provider-form) (search-article-provider-js)]
                 :active "Artículos"
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

;;; No longer used
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

;;; No longer used
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

(defpartial article-instant-search-form
  [url]
  (form-to {:class "form form-horizontal"} [:post url]
           [:div.control-group
            (label {:class "control-label"}
                   :search "Buscar por código o nombre")
            [:div.controls
             (text-field {:id "search" :autocomplete "off"} :search)]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Buscar")]))

(defpage "/articulos/global/" []
  (let [url "/articulos/global/"
        content {:content (article-instant-search-form url)
                 :title "Agregar por código nombre"
                 :active "Artículos"
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :base-js :search-js])))
(defpage "/articulos/ventas/" []
  (let [url "/articulos/ventas/"
        content {:content (article-instant-search-form url)
                 :title "Agregar por código nombre"
                 :active "Artículos"
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :base-js :search-js])))
(defpage "/articulos/proveedor/" []
  (let [url "/articulos/proveedor/"
        content {:content (article-instant-search-form url)
                 :title "Agregar por código nombre"
                 :active "Artículos"
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :base-js :search-js])))
(defpage "/articulos/agregar/codnom/" []
  (let [url "/articulos/agregar/codnom/"
        content {:content (article-instant-search-form url)
                 :title "Agregar por código nombre"
                 :active "Artículos"
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :base-js :search-js])))
(defpage "/articulos/modificar/precios/" []
  (let [url "/articulos/modificar/precios/"
        content {:content (article-instant-search-form url)
                 :title "Modificando precios"
                 :active "Artículos"
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :base-js :search-js])))
(defpage "/articulos/modificar/codigo/" []
  (let [url "/articulos/modificar/codigo/"
        content {:content (article-instant-search-form url)
                 :title "Modificando código"
                 :active "Artículos"
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :base-js :search-js])))
(defpage "/articulos/modificar/nombre/" []
  (let [url "/articulos/modificar/nombre/"
        content {:content (article-instant-search-form url)
                 :title "Modificando nombre"
                 :active "Artículos"
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :base-js :search-js])))
(defpage "/articulos/modificar/total/" []
  (let [url "/articulos/modificar/total/"
        content {:content (article-instant-search-form url)
                 :title "Modificando artículo"
                 :active "Artículos"
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :base-js :search-js])))
(defpage "/articulos/eliminar/" []
  (let [url "/articulos/eliminar/"
        content {:content (article-instant-search-form url)
                 :title "Eliminando artículos"
                 :active "Artículos"
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :base-js :search-js])))
(defpage "/articulos/corregir/" []
  (let [url "/articulos/corregir/"
        content {:content (article-instant-search-form url)
                 :title "Corrigiendo errores"
                 :active "Artículos"
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :base-js :search-js])))

(defn search-article-result
  [q url]
  (let [art (if (article/valid-barcode? q)
              (article/get-by-barcode q)
              (article/get-by-name q))
        id (str (:_id art))
        url (if (= \/ (last url))
              url
              (str url "/"))]
    (when (seq id)
      (str url id "/"))))

;;; POSTS
(defpage [:post "/articulos/global/"] {:keys [search]}
  (let [url "/articulos/global/"
        r (when (seq search) (search-article-result search url))]
    (if r
      (resp/redirect r)
      (do
        (utils/flash-message "No se ha encontrado este artículo" "error")
        (resp/redirect url)))))
(defpage [:post "/articulos/ventas/"] {:keys [search]}
  (let [url "/articulos/ventas/"
        r (when (seq search) (search-article-result search url))]
    (if r
      (resp/redirect r)
      (do
        (utils/flash-message "No se ha encontrado este artículo" "error")
        (resp/redirect url)))))
(defpage [:post "/articulos/proveedor/"] {:keys [search]}
  (let [url "/articulos/proveedor/"
        r (when (seq search) (search-article-result search url))]
    (if r
      (resp/redirect r)
      (do
        (utils/flash-message "No se ha encontrado este artículo" "error")
        (resp/redirect url)))))
(defpage [:post "/articulos/agregar/codnom/"] {:keys [search]}
  (let [url "/articulos/agregar/codnom/"
        r (when (seq search) (search-article-result search url))]
    (if r
      (resp/redirect r)
      (do
        (utils/flash-message "No se ha encontrado este artículo" "error")
        (resp/redirect url)))))
(defpage [:post "/articulos/modificar/precios/"] {:keys [search]}
  (let [url "/articulos/modificar/precios/"
        r (when (seq search) (search-article-result search url))]
    (if r
      (resp/redirect r)
      (do
        (utils/flash-message "No se ha encontrado este artículo" "error")
        (resp/redirect url)))))
(defpage [:post "/articulos/modificar/codigo/"] {:keys [search]}
  (let [url "/articulos/modificar/codigo/"
        r (when (seq search) (search-article-result search url))]
    (if r
      (resp/redirect r)
      (do
        (utils/flash-message "No se ha encontrado este artículo" "error")
        (resp/redirect url)))))
(defpage [:post "/articulos/modificar/nombre/"] {:keys [search]}
  (let [url "/articulos/modificar/nombre/"
        r (when (seq search) (search-article-result search url))]
    (if r
      (resp/redirect r)
      (do
        (utils/flash-message "No se ha encontrado este artículo" "error")
        (resp/redirect url)))))
(defpage [:post "/articulos/modificar/total/"] {:keys [search]}
  (let [url "/articulos/modificar/total/"
        r (when (seq search) (search-article-result search url))]
    (if r
      (resp/redirect r)
      (do
        (utils/flash-message "No se ha encontrado este artículo" "error")
        (resp/redirect url)))))
(defpage [:post "/articulos/eliminar/"] {:keys [search]}
  (let [url "/articulos/eliminar/"
        r (when (seq search) (search-article-result search url))]
    (if r
      (resp/redirect r)
      (do
        (utils/flash-message "No se ha encontrado este artículo" "error")
        (resp/redirect url)))))
(defpage [:post "/articulos/corregir/"] {:keys [search]}
  (let [url "/ajustes/errores/"
        r (when (seq search) (search-article-result search url))]
    (if r
      (resp/redirect r)
      (do
        (utils/flash-message "No se ha encontrado este artículo" "error")
        (resp/redirect "/articulos/corregir/")))))

;;; No longer used
(defpartial search-article-results [query]
  (let [data (or (article/get-by-barcode query)
                 (article/get-by-search query))]
    (if (users/admin?)
      (search-results-table-admin data)
      (search-results-table-employee data))))

(defpartial search-art-by-providers [prov]
  (let [data (article/get-by-provider prov)]
    (search-results-table-admin data)))

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
     (for [k article/new-art-props-sorted]
       [:div.control-group
        (label {:class "control-label"} k (article/verbose-names-new k))
        [:div.controls
         (text-field {:class "disabled" :disabled true
                      :placeholder (k article)} k (k article))]])]
    [:div.form-actions
     (submit-button {:class "btn btn-danger"} "Borrar artículo")
     (link-to {:class "btn btn-success"} "/articulos/eliminar/" "Cancelar")]))

(defpage "/articulos/eliminar/:id/" {id :id}
  (let [article (article/get-by-id id)
        content {:title "Borrar un artículo"
                 :content (if (seq article)
                            [:div.container
                             [:h2 "¿Está seguro de que quiere borrar el siguiente artículo?"]
                             (show-article-delete article)
                             submit-on-enter-js]
                            [:div.container
                             [:p.alert.alert-error "No existe un artículo con dicha id"]
                             [:div.form-actions
                              (link-to {:class "btn btn-success"} "/articulos/eliminar/" "Regresar")]])
                 :nav-bar true
                 :active "Artículos"}]
    (main-layout-incl content [:base-css :jquery :base-js])))

(defpage [:post "/articulos/eliminar/:id/"] {:as post}
  (let [art-name (:nom_art (article/get-by-id (:id post)))
        date (utils/now)]
    (article/delete-article (:id post))
    (logs/add-logs! (:id post) :deleted {} date)
    (session/flash-put! :messages [{:type "alert-success" :text (str "El artículo " art-name " ha sido borrado.")}])
    (resp/redirect "/articulos/eliminar/")))

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
        iva (if (and (number? (:iva article)) (== 0 (:iva article)))
              false true)
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
                                                       "/articulos/global/"
                                                       "Regresar a buscar artículos")]
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
                                                       "/articulos/ventas/"
                                                       "Regresar a buscar artículos")]
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
        content {:title "Consulta para proveedor"
                 :active "Artículos"
                 :content [:div.container-fluid
                           (show-different-versions-form article (str "/articulos/proveedor/" id "/"))
                           (show-article-tables art-sorted)

                           [:div.form-actions (link-to {:class "btn btn-success"}
                                                       "/articulos/proveedor/"
                                                       "Regresar a buscar artículos")]
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

(defpartial add-article-row-partial [[k v] modifiable & [prev]]
  (letfn [(std-text [k v] (text-field {:class "article-new-value" :autocomplete "off" :id (name k)}
                                      k
                                      (or (k prev) v)))
          (dis-text [k v] (text-field {:class "disabled" :disabled true :placeholder v} k v))]
    [:tr.article-row
     [:td.prop-name (article/verbose-names-new k)]
     [:td.new-value
      (cond
       (not (modifiable k)) [:div (dis-text k v) (hidden-field k v)]
       (= :unidad k) (unit-select (or (k prev) v))
       (= :lin k) (lin-select (or (k prev) v))
       (= :iva k) (iva-select (or (k prev) v))
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

(defpartial add-article-form [article modifiable & [prev]]
  (let [verbose article/verbose-names-new
        date (utils/now)]
    (form-to {:class "form form-horizontal"} [:post "/articulos/agregar/"]
      [:table.table.table-condensed
       [:tr
        [:th "Nombre de campo"]
        [:th "Valor"]
        [:th "Ayudas"]]
       [:fieldset
        (map add-article-row-partial
             (article/sort-by-vec (dissoc article :prev) article/new-art-props-sorted)
             (repeat modifiable)
             (repeat prev))]]
      [:div.form-actions
       (submit-button {:class "btn btn-primary"} "Agregar este artículo")
       (link-to {:class "btn btn-danger"} "/" "Cancelar y regresar")])))

(def focus-on-first-input-js
  (javascript-tag
   "$('form:first *:input[type!=hidden]:first').focus();"))

(defpage "/articulos/agregar/codnom/:_id/" {id :_id :as pst}

  (let [article (article/get-by-id id)
        modifiable #{:codigo :nom_art}
        title     "Alta por código y nombre"
        content {:title title
                 :active "Artículos"
                 :content [:div.container-fluid
                           (when-not (empty? (:errors (article/errors-warnings article)))
                             (let [next-url (str "/articulos/agregar/codnom/"
                                                 id
                                                 "/")]
                               [:div.alert.alert-error
                                [:p "Este artículo contiene errores, se recomienda corregirlos antes de intentar modificar el artículo en este módulo"]
                                (link-to {:class "btn"} (str "/ajustes/errores/" id "/?next=" next-url) "Corregir errores")]))
                           (add-article-form article modifiable pst)
                           focus-on-first-input-js]
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :base-js :jquery-ui :verify-js :modify-js])))

(defpage "/articulos/agregar/" {:as pst}
  (let [modifiable #{:img :unidad :stk :lin :ramo :iva :pres :gan :costo_caja :precio_venta :ubica :prov :exis :codigo :nom_art :tam}
        content {:title "Alta total de un artículo"
                 :active "Artículos"
                 :nav-bar true
                 :content [:div.container-fluid
                           (add-article-form
                            (dissoc (article/map-to-article {}) :prev)
                            modifiable
                            pst)
                           focus-on-first-input-js]}]
    (main-layout-incl content [:base-css :jquery :base-js :verify-js :modify-js])))

(defpage [:post "/articulos/agregar/"] {:as post}
  (let [to-add (dissoc post :_id :prev :id)
        date (utils/now)
        added (article/add-article! to-add)]
    (if (= :success added)
      (let [new-id (:_id (article/get-by-match to-add))]
        (logs/add-logs! (str new-id) :added to-add date)
        (session/flash-put! :messages '({:type "alert-success" :text "El artículo ha sido agregado."}))
        (resp/redirect (if (:_id post)
                         "/articulos/agregar/codnom/"
                         "/articulos/agregar/")))
      (do
        (session/flash-put! :messages (for [[k es] added e es]
                                        {:type "alert-error" :text e}))
        (if-not (:_id post)
          (render "/articulos/agregar/" post)
          (GET--articulos--agregar--codnom-->_id-- post))))))

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
