(ns lanina.views.common
  (:use [noir.core :only [defpartial]]
        [hiccup.page :only [include-css include-js html5]]
        [hiccup.element :only [link-to image]])
  (:require [noir.session :as session]))

;;; Head includes here
(def includes
  {
   :base-css (include-css "/css/base.css")
   :less (include-js "/js/less.js")
   :jquery (include-js "/js/jquery.js")
   :barcode-js (include-js "/js/barcode.js")
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
  [:nav
   [:ul
    (map link lnks)]])

(defpartial disp-message [msg]
  [:li {:class (:type msg)} (:text msg)])

;;; Content needs to include a :content and an optional :title
(defpartial main-layout-incl [content includes]
  (html5 {:lang "es-MX"}
   (head includes (get content :title ""))
   [:body
    [:header
     (link-to {:id "logo"} "/" (image {:class "logo"} "/img/lisp.gif" [:h1#title "Lonja Mercantil La Niña"]))
     (when (:nav-bar content)
       (nav-bar nav-items))]
    (when (session/flash-get :messages)
      [:div#messages
       [:ul
        (map disp-message (session/flash-get :messages))]])
    [:section#home
     [:h1 (get content :title "Sitio de administración")]
     (:content content)]
    [:footer
     (get content :footer
          [:p "Gracias por visitar"])]]))

(defpartial main-layout [content]
  (main-layout-incl content [:base-css]))

(defpartial home-layout [content]
  (main-layout (into content {:nav-bar true})))
