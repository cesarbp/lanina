(ns lanina.views.article
  (:use noir.core
        lanina.views.common
        hiccup.form
        [hiccup.element :only [link-to]]
        [lanina.models.utils :only [valid-id?]])
  (:require [lanina.models.article  :as article]
            [lanina.views.utils     :as utils]
            [lanina.models.user     :as users]
            [noir.session           :as session]
            [noir.response          :as resp]
            [lanina.models.logs     :as logs]
            [clj-time.core          :as time]))

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

(defpartial barcode-form []
  (form-to {:id "barcode-form" :class "form-inline"} [:get ""]
    [:div.subnav
     [:ul.nav.nav-pills
      [:li [:a [:h2#total "Total: 0.00"]]]
      [:li
       (text-field {:class "input-small" :style "position:relative;top:14px;text-align:right;width:40px;" :id "quantity-field" :onkeypress "return quantity_listener(this, event)" :autocomplete "off" :placeholder "F2"} "quantity")]
      [:li
       (text-field {:class "input-small" :style "position:relative;top:14px;left:2px;text-align:right" :id "barcode-field" :onkeypress "return barcode_listener(this, event)" :autocomplete "off" :placeholder "F3 - Código"} "barcode")]
      [:li
       (text-field {:style "position:relative;top:14px;left:4px;text-align:right" :id "article-field" :onkeypress "return article_listener(this, event)" :autocomplete "off" :placeholder "F4 - Nombre de artículo"} "article")]
      ]]))

(defpartial add-unregistered-form []
  [:div.navbar.navbar-fixed-bottom
   [:div.navbar-inner
    [:div.container-fluid
     [:ul.nav
      [:li [:a "Artículos libres"]]
      [:li
       (form-to {:id "unregistered-form" :class "form-inline"} [:get ""]
         (text-field {:class "input-small" :style "position:relative;top:10px;text-align:right;width:40px;" :id "unregistered-quantity" :onkeypress "return unregistered_listener(this,event)" :autocomplete "off" :placeholder "F6"} "unregistered-quantity" "")
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
  (when-not (users/admin?)
    (session/flash-put! :messages '({:type "error" :text "Necesita estar firmado para accesar esta página"}))
    (resp/redirect "/entrar/")))

(defpage "/ventas/" []
  (let [content {:title "Ventas"
                 :content [:div#main.container-fluid (barcode-form) (item-list) (add-unregistered-form)]
                 :footer [:p "Gracias por su compra."]
                 :nav-bar true
                 :active "Ventas"}]
    (main-layout-incl content [:base-css :search-css :switch-css :jquery :jquery-ui :base-js :shortcut :scroll-js :barcode-js :custom-css :subnav-js :switch-js])))

;;; View an article
(defpartial view-article-form [[k v]]
  [:tr.article-row
   [:td.prop-name (article/verbose-names k)]])

(defpartial modify-article-row [[k v]]
  [:tr.article-row
   [:td.prop-name (article/verbose-names k)]
   (if (= :codigo k)
     [:td.new-value (text-field {:class "article-new-value disabled" :disabled true :placeholder (str v)} (name k))]
     [:td.new-value
      (if (= :iva k)
        [:select {:name k}
         [:option {:value (if (= (str v) "0") "0" "16")} (if (= (str v) "0") "0" "16")]
         [:option {:value (if (= (str v) "0") "16" "0")} (if (= (str v) "0") "16" "0")]]
        (if (= :gan k)
          [:div.control-group {:id (str (name k) "-control")}
           (text-field {:class "article-new-value"} (name k) (str v))]
          (text-field {:class "article-new-value"} (name k) (str v))))])
   (if (or (= k :prev_sin) (= k :prev_con))
     [:td
      [:a.btn {:onclick "return prev_up()"}
       [:i.icon-chevron-up]]
      [:a.btn {:onclick "return prev_down()"}
       [:i.icon-chevron-down]]]
     [:td])])

(defpartial modify-article-table [article]
  (when (seq article)
    (form-to {:class "form-horizontal" :id "modify-article-form" :name "modify-article"} [:post (str "/articulos/id/" (str (:_id article)) "/modificar/")]
      [:table {:class "table table-condensed"}
       [:tr.table-header
        [:th "Nombre"]
        [:th "Nuevo Valor"]
        [:th]]
       (map modify-article-row (article/sort-by-vec (dissoc article :_id) [:codigo :nom_art :pres :ccj_con :cu_con :prev_con :ccj_sin :cu_sin :prev_sin :gan]))]
      [:fieldset
       [:div.form-actions
        (submit-button {:class "btn btn-warning" :name "submit"} "Modificar")
        (link-to {:class "btn btn-danger"} "/articulos/" "Cancelar")]])))

(defpage "/articulos/id/:_id/modificar/" {id :_id}
  (if (valid-id? id)
    (let [article (article/get-by-id id)
          content {:title "Modificando Artículo"
                   :content [:div.container-fluid
                             [:div.subnav [:ul.nav.nav-pills [:li [:a [:h2 (:nom_art article)]]]]]
                             (if article
                               (modify-article-table article)
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

(defpartial confirm-changes-table [orig-vals changes]
  (let [rows (reduce (fn [acc nxt]
                       (into acc [[nxt (nxt orig-vals) (nxt changes)]]))
                     []
                     (keys (dissoc orig-vals :_id)))]
    [:div.article-dialog
     (form-to {:class "form-horizontal" :id "modify-article-form" :name "modify-article"} [:post (str "/articulos/id/" (str (:_id orig-vals)) "/modificar/")]
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
        now (time/now)
        date (str (time/day now) "/" (time/month now) "/" (time/year now))]
    (cond 
      (= "Modificar" (:submit pst))
      (let [ks (article/get-keys)
            changes          
            (reduce (fn [acc k]
                      (if (and (k pst) (not= (article k) (k pst))
                               (try (not= (article k) (Double. (k pst)))
                                    (catch Exception e false))
                               (try (not= (article k) (Integer. (k pst)))
                                    (catch Exception e false)))
                        (into acc {k (.toUpperCase (k pst))})
                        acc))
                    {}
                    ks)
            changed-keys (keys changes)
            original-vals (article/get-by-id-only (:_id pst) (vec changed-keys))]
        (if (seq changes)
          (home-layout (assoc content :content
                              [:div.container-fluid (confirm-changes-table original-vals changes)]))
          (home-layout (assoc content :content
                              [:div.container
                               [:p.alert.alert-warning "No hay cambios para realizar"]
                               [:div.form-actions
                                (link-to {:class "btn btn-success"} "/articulos/" "Regresar")]]))))
      (= "Confirmar" (:submit pst))
      (do (article/update-article (dissoc pst :submit))
          (logs/setup!)
          (logs/add-logs! (:_id pst) :updated (dissoc pst :submit) date)
          (session/flash-put! :messages '({:type "alert-success" :text "El artículo ha sido modificado"}))
          (resp/redirect "/articulos/"))
      :else "Invalid")))

(defpartial search-article-form-js []
  [:script
   "function redirect_to_add_art() {
    var search = $('#search').val();
    if (search.length > 0) {
	var url = '/articulos/agregar/?busqueda=' + search;
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
        (text-field {:id "search" :autocomplete "off"} "busqueda")]]]
     [:div.form-actions
      (submit-button {:class "btn btn-primary" :name "search"} "Buscar")
      (link-to {:class "btn btn-warning" :onclick "return redirect_to_add_art();"} "/articulos/agregar/" "Agregar otro artículo")])])

(defpage "/articulos/" []
  (let [content {:title "Búsqueda de Artículos"
                 :content [:div.container (search-article-form-js) (search-article-form)]
                 :active "Artículos"
                 :footer [:p "Gracias por visitar."]
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :jquery-ui :trie-js :search-js])))

(defpartial search-results-row [result]
  (when (seq result)
    (let [{:keys [_id codigo nom_art prev_con prev_sin]} result]
      [:tr.result
       [:td.codigo codigo]
       [:td.nom_art (link-to {:class "search-result-link"} (str "/articulos/id/" _id "/") nom_art)]
       [:td.prev_con prev_con]
       [:td.prev_sin prev_sin]
       [:td.consultar (link-to {:class "btn btn-success"} (str "/articulos/id/" _id "/global/") "Global")]
       [:td.consultar (link-to {:class "btn btn-success"} (str "/articulos/id/" _id "/ventas/") "Ventas")]
       [:td.consultar (link-to {:class "btn btn-success"} (str "/articulos/id/" _id "/proveedor/") "Proveedor")]
       [:td.modificar (link-to {:class "btn btn-warning"} (str "/articulos/id/" _id "/modificar/") "Modificar")]
       [:td.eliminar (link-to {:class "btn btn-danger"}  (str "/articulos/id/" _id "/eliminar/") "Eliminar")]])))

(defpartial search-results-table [results]
  (if (seq results)
    [:div.container
     [:table {:class "table table-condensed"}
      [:tr
       [:th#barcode-header "Código"]
       [:th#name-header "Artículo"]
       [:th#p-without-header "Precio sin IVA"]
       [:th#p-with-header "Precio con IVA"]
       [:th {:colspan "3"} "Consultas"]
       [:th {:colspan "2"} "Bajas y Modificaciones"]]
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

(defpage "/articulos/buscar/" {:keys [busqueda submit]}
  (let [content {:title "Resultados de la búsqueda"
                 :content [:div.container (search-article-results busqueda)]
                 :nav-bar true
                 :active "Artículos"}]
    (main-layout-incl content [:base-css :jquery :shortcut :art-res-js])))

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
                             (show-article-delete article)]
                            [:div.container
                             [:p.alert.alert-error "No existe un artículo con dicha id"]
                             [:div.form-actions
                              (link-to {:class "btn btn-success"} "/articulos/" "Regresar")]])
                 :nav-bar true
                 :active "Artículos"}]
    (home-layout content)))

(defpage [:post "/articulos/id/:id/eliminar/"] {:as post}
  (let [art-name (:nom_art (article/get-by-id (:id post)))
        now (time/now)
        date (str (time/day now) "/" (time/month now) "/" (time/year now))]
    (article/delete-article (:id post))
    (logs/setup!)                       ;remove this
    (logs/add-logs! (:id post) :deleted {} date)
    (session/flash-put! :messages [{:type "alert-success" :text (str "El artículo " art-name " ha sido borrado.")}])
    (resp/redirect "/articulos/")))

;;; View an article
(defpartial show-article-tables [article]
  (let [verbose article/verbose-names
        art-split (partition-all (/ (count verbose) 3) article)]
    [:div.row
     (map (fn [pairs]
            [:div.span4
             [:table.table.table-condensed
              [:tr
               [:th "Nombre"]
               [:th "Valor"]]
              (map (fn [[k v]]
                     [:tr
                      [:td (verbose k)]
                      [:td v]])
                   pairs)]])
          art-split)]))

(defpage "/articulos/id/:id/global/" {id :id}
  (let [article (dissoc (article/get-by-id id) :_id)
        content {:title "Consulta global"
                 :active "Artículos"
                 :content [:div.container-fluid (show-article-tables article)
                           [:div.form-actions (link-to {:class "btn btn-success"}
                                                       (str "/articulos/" ) "Regresar a buscar artículos")]]}]
    (home-layout content)))

(defpage "/articulos/id/:id/ventas/" {id :id}
  (let [article (dissoc (article/get-by-id-only id [:codigo :nom_art :tam :lin :ramo :pres :unidad :ubica :iva :exis :stk :prev_con :prev_sin :fech_an :fech_ac]) :_id)
        art-sorted (article/sort-by-vec (if (= (:iva article) 0)
                                          (dissoc article :prev_con)
                                          (dissoc article :prev_sin))
                                        [:codigo :nom_art])
        content {:title "Consulta para ventas"
                 :active "Artículos"
                 :content [:div.container-fluid (show-article-tables art-sorted)
                           [:div.form-actions (link-to {:class "btn btn-success"}
                                                       (str "/articulos/" ) "Regresar a buscar artículos")]]}]
    (home-layout content)))

(defpage "/articulos/id/:id/proveedor/" {id :id}
  (let [article (dissoc (article/get-by-id-only id [:codigo :nom_art :tam :lin :ramo :pres :unidad :ubica :iva :prov :ccj_sin :ccj_con :cu_sin :cu_con :exis :stk :fech_an :fech_ac]) :_id)
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
       [:td (link-to {:class "btn btn-primary"} (str "/articulos/agregar/id/" _id "/cod_nom/") "Por código y nombre")]
       [:td (link-to {:class "btn btn-success"} (str "/articulos/agregar/id/" _id "/total/") "Alta total")]])))

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

(defpage "/articulos/agregar/" {:keys [busqueda]}
  (let [title "Resultados para agregar un artículo"
        content {:title "Resultados de la búsqueda"
                 :active "Artículos"
                 :content [:div.container (search-add-article-results busqueda)]}]
    (home-layout content)))

(defpartial add-article-form [article to-modify]
  (let [verbose article/verbose-names]
    (form-to {:class "form form-horizontal"} [:post "/articulos/nuevo/"]
      [:fieldset
       (map (fn [[k v]]
              [:div.control-group {:id (str (name k) "-control")}
               (label {:class "control-label"} k (verbose k))
               [:div.controls
                (cond (not (seq to-modify))
                      (if (= :iva k)
                        [:select {:name k}
                         [:option {:value "16"} "16"]
                         [:option {:value "16"} "0"]]
                        (text-field {:id k} k))
                      (some #{k} to-modify)
                      (text-field {:id k} k (article k))
                      :else
                      [:div (text-field {:class "disabled" :disabled true :placeholder (article k)} k (article k))
                       (hidden-field k (article k))])]])
            (article/sort-by-vec (dissoc article :_id) [:codigo :nom_art :pres :iva :gan :ccj_con :cu_con :prev_con :ccj_sin :cu_sin :prev_sin]))]
      [:div.form-actions
       (submit-button {:class "btn btn-primary"} "Agregar este artículo")
       (link-to {:class "btn btn-danger"} "/articulos/" "Cancelar y regresar")])))

(defpage "/articulos/agregar/id/:id/:method/" {id :id method :method}
  (let [article (article/get-by-id id)
        to-modify (cond (= method "cod_nom") [:codigo :nom_art]
                        (= method "total")   [])
        title     (cond (= method "cod_nom") "Alta por código y nombre"
                        (= method "total")   "Alta total de un artículo")
        content {:title title
                 :active "Artículos"
                 :content [:div.container (add-article-form article to-modify)]
                 :nav-bar true}]
    (main-layout-incl content [:base-css :search-css :jquery :jquery-ui :verify-js :modify-js])))

(defpage [:post "/articulos/nuevo/"] {:as post}
  (let [to-add (dissoc post :_id)
        now (time/now)
        date (str (time/day now) "/" (time/month now) "/" (time/year now))]
    (article/add-article to-add)
    (logs/setup!)                       ;Remove this
    (logs/add-logs! (:_id post) :added to-add date)
    (session/flash-put! :messages '({:type "alert-success" :text "El artículo ha sido agregado."}))
    (resp/redirect "/articulos/")))

;;; Show Logs
(defpartial logrow [{:keys [date content link]}]
  [:li date
   [:ul
    [:li (link-to link content)]]])

(defpartial home-content [logs]
  [:article
   [:h2 "Últimos cambios:"]
   [:div#logs
    [:ol
     (map logrow logs)]]])

(pre-route "/inicio/" {}
           (when-not (users/admin?)
             (session/flash-put! :messages '({:type "alert-error" :text "Necesita estar firmado para accesar esta página"}))
             (resp/redirect "/entrar/")))

(defpage "/inicio/" []
  (let [lgs (logs/retrieve-all)
        lgs-usable
        (when (seq lgs)
          (map (fn [l]
                 {:date (:date l)
                  :content (cond (= "deleted" (:type l)) "Se eliminó un artículo"
                                 (= "updated" (:type l)) "Se modificó un artículo"
                                 (= "added" (:type l)) "Se agregó un nuevo artículo")
                  :link (str "/logs/" (:date l))})
               lgs))
        content {:title "Inicio"
                 :content [:div.container (home-content (or lgs-usable {}))]
                 :active "Inicio"}]
    (home-layout content)))
