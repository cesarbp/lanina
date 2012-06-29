(ns lanina.views.home
  (:use noir.core
        somnium.congomongo
        [somnium.congomongo.config :only [*mongo-config*]]
        lanina.views.common)
  (:require [noir.response :as resp]))

(defpage "/" []
  (resp/redirect "/inicio/"))

(defpartial home-content []
  [:article "Este es un sitio de prueba"])

(defpage "/inicio/" []
  (let [content {:title "Inicio"
                 :content (home-content)}]
    (main-layout content)))