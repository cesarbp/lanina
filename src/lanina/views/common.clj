(ns lanina.views.common
  (:use [noir.core :only [defpartial]]
        [hiccup.page :only [include-css include-js html5]]
        [hiccup.element :only [link-to]]))

;;; Head includes here
(def includes
  {
   :less (include-js "/js/less.js")
   :jquery (include-js "http://ajax.googleapis.com/ajax/libs/jquery/1.6.1/jquery.min.js")
   })

;;; Links on the nav
(def nav-items
  [{:name "Artículos" :url "/articulos/"}
   {:name "Inicio" :url "/inicio/"}])

(defpartial head [incls]
  [:head
   [:meta {:charset "UTF-8"}]
   [:title "La Niña"]
   (map #(get includes %) incls)])

(defpartial link [{:keys [text url]}]
  [:li
   (link-to url text)])

(defpartial nav-bar [lnks]
  [:nav
   [:ul
    (map link lnks)]])

;;; Content needs to include a :content and an optional :title
(defpartial main-layout [content]
  (html5 {:lang "es-MX"}
   (head [:less])
   [:body
    [:header
     [:h1#title (if (:title content)
                  (str (:title content) " | " "Lonja Mercantil La Nina")
                  "Lonja Mercantil La Nina")]]
    (nav-bar nav-items)
    [:section {:class "body"}
     [:h2 "Bienvenido"]
     [:p "Sitio de prueba"]
     (:content content)]
    [:footer
     [:p "Gracias por visitar"]]]))
