(ns lanina.views.article
  (:use noir.core
        lanina.views.common
        hiccup.form
        [hiccup.element :only [link-to]]
        [lanina.models.utils :only [valid-id?]])
  (:require [lanina.models.article :as article]
            [lanina.views.utils :as utils]
            [lanina.models.user :as users]
            [noir.session :as session]
            [noir.response :as resp]))

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

(defpartial barcode-form []
  (form-to {:id "barcode-form" :class "form-horizontal"} [:get ""] 
    [:fieldset
     [:div.control-group
      (label {:class "control-label" :id "barcode-label"} "barcode" "Código de barras")
      [:div.controls
       (text-field {:id "barcode-field" :onkeypress "return barcode_listener(this, event)" :autocomplete "off"} "barcode")]]]
    [:h2#total "Total: 0.00"]))

(defpartial item-list []
  [:table {:id "articles-table" :class "table table-condensed"}
   [:tr
    [:th#barcode-header "Código"]
    [:th#name-header "Artículo"]
    [:th#price-header "Precio"]
    [:th#quantity-header "Cantidad"]
    [:th#total-header "Total"]]])

(pre-route "/ventas/" []
  (when-not (users/admin?)
    (session/flash-put! :messages '({:type "error" :text "Necesita estar firmado para accesar esta página"}))
    (resp/redirect "/entrar/")))

(defpage "/ventas/" []
  (let [content {:title "Ventas"
                 :content [:div#main.container (barcode-form) (item-list)]
                 :footer [:p "Gracias por su compra."]
                 :nav-bar true
                 :active "Ventas"}]
    (main-layout-incl content [:base-css :jquery :barcode-js])))

;;; View an article
(defpartial view-article-form [[k v]]
  [:tr.article-row
   [:td.prop-name (article/verbose-names k)]])

(defpartial modify-article-row [[k v]]
  [:tr.article-row
   [:td.prop-name (article/verbose-names k)]
   [:td.orig-value (str v)]
   [:td.new-value (text-field {:class "article-new-value"} (name k))]])

(defpartial modify-article-table [article]
  (when (seq article)
    (form-to {:class "form-horizontal" :id "modify-article-form" :name "modify-article"} [:post (str "/articulos/id/" (str (:_id article)) "/modificar/")]
      [:table {:class "table table-condensed"}
       [:tr.table-header
        [:th "Nombre"]
        [:th "Valor Actual"]
        [:th "Nuevo Valor"]]
       (map modify-article-row (dissoc article :_id))]
      [:fieldset
       [:div.form-actions
        (submit-button {:class "btn btn-warning" :name "submit"} "Confirmar")
        (link-to {:class "btn btn-danger"} "/articulos/" "Cancelar")]])))

(defpage "/articulos/id/:_id/modificar/" {id :_id}
  (if (valid-id? id)
    (let [article (article/get-by-id id)
          content {:title "Modificar Artículo"
                   :content [:div.container
                             (if article
                               (modify-article-table article)
                               [:p.error-notice "No existe tal artículo"])]
                   :active "Artículos"}]
      (home-layout content))))

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
           (submit-button {:class "btn btn-success" :name "submit"} "Modificar")
           (link-to {:class "btn btn-danger"} "/articulos/" "Cancelar")]])]))

(defpage [:post "/articulos/id/:_id/modificar/"] {:as pst}
  (let [content {:title "Confirmar Cambios"
                 :active "Artículos"}]
    (cond 
      (= "Confirmar" (:submit pst))
      (let [ks (article/get-keys)
            changes          
            (reduce (fn [acc k]
                      (if (seq (k pst))
                        (into acc {k (.toUpperCase (k pst))})
                        acc))
                    {}
                    ks)
            changed-keys (keys changes)
            original-vals (article/get-by-id-only (:_id pst) (vec changed-keys))]
        (home-layout (assoc content :content
                            [:div.container (confirm-changes-table original-vals changes)])))
      (= "Modificar" (:submit pst))
      (do (article/update-article (dissoc pst :submit))
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
      (submit-button {:class "btn btn-primary" :name "submit"} "Buscar")
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
       [:td.nom_art (link-to {:class "search-result-link"} (str "/articulos/id/" _id) nom_art)]
       [:td.prev_con prev_con]
       [:td.prev_sin prev_sin]
       [:td.consultar (link-to {:class "btn btn-success"} (str "/articulos/id/" _id "/") "Consultar")]
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
       [:th {:colspan "3"} "Controles"]]
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
    (main-layout-incl content [:base-css])))

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
  (let [art-name (:nom_art (article/get-by-id (:id post)))]
    (article/delete-article (:id post))
    (session/flash-put! :messages [{:type "alert-success" :text (str "El artículo " art-name " ha sido borrado.")}])
    (resp/redirect "/articulos/")))

;;; View an article
(defpartial show-article-tables [article]
  (let [verbose article/verbose-names
        art-split (partition-all (/ (count verbose) 3) (dissoc article :_id))]
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

(defpage "/articulos/id/:id/" {id :id}
  (let [article (article/get-by-id id)
        content {:title "Mostrar Artículo"
                 :active "Artículos"
                 :content [:div.container (show-article-tables article)
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
              [:div.control-group
               (label {:class "control-label"} k (verbose k))
               [:div.controls
                (cond (or (not (seq to-modify)) (some #{k} to-modify))
                      (text-field k (article k))
                      :else
                      [:div (text-field {:class "disabled" :disabled true :placeholder (article k)} k (article k))
                       (hidden-field k (article k))])]])
            article)]
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
                 :content [:div.container (add-article-form article to-modify)]}]
    (home-layout content)))

(defpage [:post "/articulos/nuevo/"] {:as post}
  (let [to-add (dissoc post :_id)]
    (article/add-article to-add)
    (session/flash-put! :messages '({:type "alert-success" :text "El artículo ha sido agregado."}))
    (resp/redirect "/articulos/")))