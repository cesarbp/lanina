(ns lanina.views.catalogs
  (:use lanina.views.common
        noir.core)
  (:require [lanina.models.catalogs :as catalog]))

(defpartial catalog-header
  [headers]
  (when (seq headers)
    [:tr
     (for [h headers]
       [:th (h catalog/verbose)])]))

(defpartial show-catalog
  [type]
  (let [type (clojure.string/lower-case type)
        ms (catalog/get-all type)
        hs (condp = type
             "lamina" catalog/lamina-props-ordered
             nil)]
    (if (seq ms)
      [:table.table.table-condensed.table-hover
       [:thead
        (catalog-header hs)]
       [:tbody
        (for [m ms]
          [:tr
           (for [h hs]
             [:td (h m)])])]]
      [:div.alert.alert-error
       [:p "Este tipo de catálogo no existe."]])))

(defpage "/catalogos/"
  {:keys [tipo]}
  (let [tipo (if (seq tipo) tipo "lamina")
        content {:title "Catálogos"
                 :content [:div.container-fluid (show-catalog tipo)]
                 :nav-bar true
                 :active "Catálogos"}]
    (home-layout content)))
