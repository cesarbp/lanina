(ns lanina.views.printing
  (:use noir.core
        hiccup.form
        lanina.views.common)
  (:require [lanina.models.printing :as printing]
            [noir.response :as resp]))

(defpartial modify-stuff-form
  []
  (form-to {:class "form-horizontal"} [:post "/impresiones/modificar/"]
           [:div.control-group
            (label {:class "control-label"} :type "Tipo de Impresion")
            [:div.controls
             (text-field :type)]]
           [:div.control-group
            (label {:class "control-label" :colsize "Tamaño de columna"})
            [:div.controls
             (text-field :colsize)]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Cambiar")]))

(defpage "/impresiones/" []
  (let [content {:nav-bar true
                 :title "Ajuste de Impresiones"
                 :content (modify-stuff-form)
                 :active "Ajustes"}]))

(defpage [:post "/impresiones/modificar/"] {:keys [type colsize]}
  (cond (seq type)
        (printing/update-print-type! (Long/valueOf type))
        (seq colsize)
        (printing/update-col-size! (Long/valueOf colsize)))
  (resp/redirect "/"))
