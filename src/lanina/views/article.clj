(ns lanina.views.article
  (:use noir.core
        lanina.views.common
        hiccup.form
        [hiccup.element :only [link-to]])
  (:require [lanina.models.article :as article]
            [clojure.data.json :as json]
            [lanina.views.utils :as utils]
            [lanina.models.user :as users]
            [noir.session :as session]
            [noir.response :as resp]))

;;; Used by js to get the article in an ajax way
(defpage "/json/article" {:keys [barcode]}
  (let [response (json/json-str (article/get-article barcode))]
    (if (not= "null" response)
      response
      "{}")))

(defpage "/json/article_starts_with" {:keys [letter]}
  (let [re (re-pattern (str "(?i)^" letter))
        response (json/json-str (article/get-articles-regex re))]
    (if (not= "null" response)
      response
      "{}")))

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

(defpartial search-article []
  (form-to {:id "search-form"}
    [:fieldset
     [:div.fied
      (label {:id "search-field"} "search" "Buscar")
      (text-field {:id "search"} "search")]]
    (submit-button {:class "submit" :name "submit"} "Buscar")))
