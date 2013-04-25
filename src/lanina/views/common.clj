(ns lanina.views.common
  (:use [noir.core :only [defpartial]]
        [hiccup.page :only [include-css include-js html5]]
        [hiccup.element :only [link-to image]])
  (:require [noir.session :as session]
            [lanina.models.user :as users]))

;;; Head includes here
(def includes
  {
   :subnav-js     (include-js "/js/subnav.js")
   :custom-css    (include-css "/css/custom.css")
   :base-css      (include-css "/css/bootstrap.min.css")
   :base-js       (include-js "/js/bootstrap.js")
   :base-resp-css (include-css "/css/bootstrap-responsive.css")
   :search-css    (include-css "/css/search.css")
   :less          (include-js "/js/less.js")
   :jquery        (include-js "/js/jquery.js")
   :barcode-js    (include-js "/js/barcode.js")
   :trie-js       (include-js "/js/trie.js")
   :search-js     (include-js "/js/search.js")
   :jquery-ui     (include-js "/js/jquery-ui.js")
   :scroll-js     (include-js "/js/scroll-to.js")
   :shortcut      (include-js "/js/shortcut.js")
   :switch-js     (include-js "/js/switch.js")
   :switch-css    (include-css "/css/switch.css")
   :verify-js     (include-js "/js/verify.js")
   :modify-js     (include-js "/js/modify.js")
   :art-res-js    (include-js "/js/article-results.js")
   :list-js       (include-js "/js/list.js")
   :tablesorter-js (include-js "/js/jquery.tablesorter.min.js")
   })

;;; Links on the nav
(def nav-links-admin
  [["Ventas"    "/ventas/"]
   ["Artículos" "/articulos/"]
   ["Tickets"   "/tickets/"]
   ["Reportes" "/reportes/"]
   {:title "Listas"
    :links
    [["Para empleados"   "/listas/"]
     ["Para compras" "/listas/compras/"]]}
   {:title "Herramientas"
    :links
    [["Ajustes de la BD" "/ajustes/"]
     ["Respaldos de la BD" "/respaldos/"]]}
   ["Salir"     "/salir/"]])

(def nav-links-empl
  [["Ventas"    "/ventas/"]
   ["Artículos" "/articulos/"]
   ["Tickets"   "/tickets/"]
   ["Salir"     "/salir/"]])

(defpartial head [incls title]
  [:head
   [:meta {:charset "UTF-8"}]
   [:title (if (seq title) (str title " | Lonja Mercantil La Niña")
               "Lonja Mercantil La Niña")]
   (map #(get includes %) incls)])

(defpartial nav-bar [active]
  (let [links (if (users/admin?) nav-links-admin nav-links-empl)]
    [:div.navbar
     [:div.navbar-inner
      [:div.container
       [:a {:class "btn btn-navbar" :data-toggle "collapse" :data-target ".nav-collapse"}
        [:span.icon-bar]
        [:span.icon-bar]
        [:span.icon-bar]]
       (link-to {:class "brand"} "/" "Lonja Mercantil La Niña")
       [:ul.nav
        (map (fn [nav-item]
               (if (vector? nav-item)
                 (let [[title lnk] nav-item]
                   [:li {:class (if (= title active) "active" "")}
                    (link-to lnk title)])
                 [:li {:class (str "dropdown" (if (= (:title nav-item) active) " active" ""))}
                  (link-to {:class "dropdown-toggle" :data-toggle "dropdown"} "#" (str (:title nav-item) "<b class=\"caret\"></b>"))
                  [:ul.dropdown-menu
                   (map (fn [[title lnk]]
                          [:li (link-to lnk title)])
                        (:links nav-item))]]))
             links)]]]]))

(defpartial nav-bar-no-links []
  [:div.navbar
   [:div.navbar-inner
    [:div.container
     [:a {:class "btn btn-navbar" :data-toggle "collapse" :data-target ".nav-collapse"}
      [:span.icon-bar]
      [:span.icon-bar]
      [:span.icon-bar]]
     (link-to {:class "brand"} "/inicio/" "Lonja Mercantil La Niña")]]])

(defpartial disp-message [msg]
  [:div {:class (str "alert " (:type msg))} (:text msg)])

;;; Content needs to include a :content and an optional :title
(defpartial main-layout-incl [content includes]
  (html5 {:lang "es-MX"}
   (head includes (get content :title ""))
   [:body {:style "background-image: url(\"/img/cream_dust.png\")"}
    (if (:nav-bar content) (nav-bar (:active content)) (nav-bar-no-links))
    (when (session/flash-get :messages)
      [:div.container-fluid
      (map disp-message (session/flash-get :messages))])
    [:div.page-header
     [:div.container-fluid
      [:h1 (get content :title "Sitio de administración")]]]
    (:content content)
    [:div.footer
     [:div.container-fluid
      [:footer
       [:hr]
       (get content :footer
            [:p "Gracias por visitar"])]]]]))

(defpartial main-layout [content]
  (main-layout-incl content [:base-css :jquery :base-js]))

(defpartial home-layout [content]
  (main-layout (into content {:nav-bar true})))
