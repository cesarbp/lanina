(ns lanina.views.printing
  (:use noir.core
        hiccup.form
        lanina.views.common)
  (:require [lanina.models.printing :as printing]
            [noir.response :as resp]
            [lanina.utils :refer [coerce-to]]
            [lanina.views.utils :refer [flash-message]])
  (:import [java.awt Font]))

(defpartial modify-stuff-form
  []
  (form-to {:class "form-horizontal"} [:post "/impresiones/modificar/"]
           [:div.control-group
            (label {:class "control-label"} :type "Tipo de Impresion")
            [:div.controls
             (text-field :type)]]
           [:div.control-group
            (label {:class "control-label"} :colsize "Tamaño de columna")
            [:div.controls
             (text-field :colsize)]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Cambiar")]))



(defpartial colsize-select
  []
  (form-to {:class "form-inline"} [:post "/impresiones/modificar/"]
           (label :colsize "Tamaño de columna")
           (text-field :colsize)
           (submit-button {:class "btn btn-primary"} "Cambiar")))

(defpartial font-select
  []
  (let [fonts (printing/system-fonts)
        current-font-idx (printing/get-current-font)
        current-font (fonts current-font-idx)]
    (form-to {:class "form-inline"} [:post "/impresiones/modificar/"]
             (label :font "Cambiar la fuente de impresión")
             [:select {:name :font}
              [:option {:value current-font-idx}
               (.getFontName current-font)]
              (map-indexed
               (fn [n f]
                 [:option {:value n} (.getFontName f)])
               fonts)]
             (submit-button {:class "btn btn-primary"} "Cambiar"))))

(defpartial font-size-select
  []
  (let [valid-font-sizes (printing/get-valid-font-sizes)
        current-font-size (printing/get-current-font-size)]
    (form-to {:class "form-inline"} [:post "/impresiones/modificar/"]
             (label :font-size "Cambiar el tamaño de la fuente")
             [:select {:name :font-size}
              [:option {:value current-font-size} current-font-size]
              (for [n valid-font-sizes]
                [:option {:value n} n])]
             (submit-button {:class "btn btn-primary"} "Cambiar"))))

(defpartial print-type-select
  []
  (let [valid-types [1 2 3 4]
        current (printing/get-current-print-type)]
    (form-to {:class "form-inline"} [:post "/impresiones/modificar/"]
             (label :printtype "Cambiar el tipo de impresión")
             [:select {:name :printtype}
              [:option {:value current} current]
              (for [vt valid-types]
                [:option {:value vt} vt])]
             (submit-button {:class "btn btn-primary"} "Cambiar"))))

(defpartial forms-list
  []
  [:list {:class "unstyled"}
   (print-type-select)
   (font-select)
   (font-size-select)
   (colsize-select)])

(defpage "/impresiones/" []
  (let [content {:nav-bar true
                 :title "Ajuste de Impresiones"
                 :content [:div.container-fluid (forms-list)]
                 :active "Ajustes"}]
    (home-layout content)))

(defpage [:post "/impresiones/modificar/"] {:keys [printtype font font-size colsize]}
  (let [resp
        (cond (seq printtype)
              (printing/update-print-type! (Long/valueOf printtype))
              (seq colsize)
              (printing/update-col-size! ((coerce-to Long 30) colsize))
              (seq font)
              (printing/update-font! ((coerce-to Long -1) font))
              (seq font-size)
              (printing/update-font-size! ((coerce-to Long -1) font-size)))]
    (if (= :success resp)
      (flash-message "Cambio exitoso" "success")
      (flash-message "Cambio no realizado" "error")))
  (resp/redirect "/impresiones/"))

(defpage "/impresiones/restart/"
  []
  (printing/setup!)
  (resp/redirect "/impresiones/"))
