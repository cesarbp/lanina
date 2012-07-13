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
  [:div.dialog
   (form-to {:id "barcode-form"} [:get "#"]
     [:fieldset
      [:div.field
       (label {:id "barcode-label"} "barcode" "Código de barras")
       (text-field {:id "barcode-field"} "barcode")]]
     [:h3#total "Total: 0.00"])])

(defpartial item-list []
  [:table#articles-table
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
                 :content [:article (barcode-form) (item-list)]
                 :footer [:p "Gracias por su compra."]
                 :nav-bar true}]
    (main-layout-incl content [:base-css :jquery :barcode-js])))

(defpartial modify-article-row [[k v]]
  [:tr.article-row
   [:td.prop-name (name k)]
   [:td.orig-value (str v)]
   [:td.new-value (text-field {:class "article-new-value"} (name k))]])

(defpartial modify-article-table [article]
  (when (seq article)
    [:div.article-dialog
     (form-to {:id "modify-article-form" :name "modify-article"} [:post (str "/articulos/id/" (str (:_id article)))]
         [:table#articles-table
          [:tr.table-header
           [:th "Nombre"]
           [:th "Valor Actual"]
           [:th "Nuevo Valor"]]
          (map modify-article-row (dissoc article :_id))]
         [:fieldset.submit
          (submit-button {:class "submit" :name "submit"} "Confirmar")])]))

(defpage "/articulos/id/:_id" {id :_id}
  (if (valid-id? id)
    (let [article (article/get-by-id id)
          content {:title "Mostrar Artículo"
                   :content [:article
                             (if article
                               (modify-article-table article)
                               [:p.error-notice "No existe tal artículo"])]}]
      (home-layout content))))

(defpartial confirm-changes-row [[k old new]]
  [:tr.article-row
   [:td.prop-name (name k)]
   [:td.orig-value old]
   [:td.new-value new]
   (hidden-field (name k) new)])

(defpartial confirm-changes-table [orig-vals changes]
  (let [rows (reduce (fn [acc nxt]
                       (into acc [[nxt (nxt orig-vals) (nxt changes)]]))
                     []
                     (keys (dissoc orig-vals :_id)))]
    [:div.article-dialog
     (form-to {:id "modify-article-form" :name "modify-article"} [:post (str "/articulos/id/" (str (:_id orig-vals)))]
         [:table#articles-table
          [:tr.table-header
           [:th "Nombre"]
           [:th "Valor Actual"]
           [:th "Nuevo Valor"]]
          (map confirm-changes-row rows)
          [:fieldset.submit
           (submit-button {:class "submit" :name "submit"} "Modificar")]])]))

(defpage [:post "/articulos/id/:_id"] {:as pst}
  (let [content {:title "Confirmar Cambios"}]
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
                            [:article (confirm-changes-table original-vals changes)])))
      (= "Modificar" (:submit pst))
      (do (article/update-article (dissoc pst :submit))
          (session/flash-put! :messages '({:type "success" :text "El artículo ha sido modificado"}))
          (resp/redirect "/articulos/"))
      :else "Invalid")))

(defpartial search-article-form []
  [:div.dialog
   (form-to {:id "search-form" :name "search-article"} [:get "/articulos/buscar/"]
     [:fieldset
      [:div.field
       (label {:id "search-field"} "busqueda" "Buscar artículos")
       (text-field {:id "search" :autocomplete "off"} "busqueda")]]
     [:fieldset.submit
      (submit-button {:class "submit" :name "submit"} "Buscar")])])

(defpage "/articulos/" []
  (let [content {:title "Búsqueda de Artículos"
                 :content [:article (search-article-form)]
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
       [:td.prev_sin prev_sin]])))

(defpartial search-results-table [results]
  (if (seq results)
    [:table#articles-table
     [:tr
      [:th#barcode-header "Código"]
      [:th#name-header "Artículo"]
      [:th#p-without-header "Precio sin IVA"]
      [:th#p-with-header "Precio con IVA"]]
     (map search-results-row results)]
    [:p.error-notice "No se encontraron resultados"]))

(defpartial search-article-results [query]
  (let [data (if (article/valid-barcode? query)
               (article/get-by-barcode query)
               (article/get-by-search query))]
    (search-results-table data)))

(defpage "/articulos/buscar/" {:keys [busqueda submit]}
  (let [content {:title "Resultados de la Búsqueda"
                 :content [:article (search-article-results busqueda)]
                 :nav-bar true}]
    (main-layout-incl content [:base-css])))

(defpartial add-article-form []
  (form-to {}))