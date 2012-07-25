(ns lanina.views.common
  (:use [noir.core :only [defpartial]]
        [hiccup.page :only [include-css include-js html5]]
        [hiccup.element :only [link-to image]])
  (:require [noir.session :as session]))

;;; Head includes here
(def includes
  {
   :base-css (include-css "/css/bootstrap.css")
   :base-resp-css (include-css "/css/bootstrap-responsive.css")
   :search-css (include-css "/css/search.css")
   :less (include-js "/js/less.js")
   :jquery (include-js "/js/jquery.js")
   :barcode-js (include-js "/js/barcode.js")
   :trie-js (include-js "/js/trie.js")
   :search-js (include-js "/js/search.js")
   :jquery-ui (include-js "/js/jquery-ui.js")
   })

;;; Links on the nav
(def nav-items
  [{:name "Ventas"    :url "/ventas/"}
   {:name "Artículos" :url "/articulos/"}
   {:name "Inicio"    :url "/inicio/"}
   {:name "Salir"     :url "/salir/"}])

(defpartial head [incls title]
  [:head
   [:meta {:charset "UTF-8"}]
   [:title (if (seq title) (str title " | Lonja Mercantil La Niña")
               "Lonja Mercantil La Niña")]
   (map #(get includes %) incls)])

(defpartial link [{:keys [name url]}]
  [:li
   (link-to url name)])

(defpartial nav-bar [lnks]
  [:ul.nav
   (map link lnks)])

(defpartial disp-message [msg]
  [:div {:class (str "alert " (:type msg))} (:text msg)])

;;; Content needs to include a :content and an optional :title
(defpartial main-layout-incl [content includes]
  (html5 {:lang "es-MX"}
   (head includes (get content :title ""))
   [:body
    [:div.navbar
     [:div.navbar-inner
      [:div.container
       (link-to {:class "brand"} "/" "Lonja Mercantil La Niña")
       (when (:nav-bar content)
         (nav-bar nav-items))]]]
    (when (session/flash-get :messages)
      (map disp-message (session/flash-get :messages)))
    [:div.page-header
     [:h1 (get content :title "Sitio de administración")]]
    [:div.container
     (:content content)]
    [:div.footer
     [:div.container
      [:footer
       (get content :footer
            [:p "Gracias por visitar"])]]]]))

(defpartial main-layout [content]
  (main-layout-incl content [:base-css]))

(defpartial home-layout [content]
  (main-layout (into content {:nav-bar true})))
