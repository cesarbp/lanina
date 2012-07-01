(ns lanina.views.home
  (:use noir.core
        lanina.views.common
        [hiccup.element :only [link-to]]
        [hiccup.form])
  (:require [noir.response :as resp]
            [noir.session :as session]
            [lanina.models.user :as users]))

(defpage "/" []
  (resp/redirect "/inicio/"))

(defpartial logrow [{:keys [date content link]}]
  [:li date
   [:ul
    [:li (link-to link content)]]])

(defpartial home-content [logs]
  [:article
   [:h2 "Últimos cambios:"]
   [:div#logs
    [:ol
     (map logrow logs)]]])

(defpartial login-form []
  [:div {:class "dialog"}
   (form-to {:id "login-form"} [:post "/entrar/"]
     [:fieldset
      [:div.field
       (label {:id "password-label"} "password" "Contraseña")
       (password-field {:id "password"} "password")]]
     [:fieldset.submit
      [:p (link-to {:id "reset-password"} "/auth/reset_password/" "Reiniciar contraseña")]
      (submit-button {:name "submit"} "Entrar")])])

(pre-route "/inicio/" {}
           (when-not (users/admin?)
             (resp/redirect "/entrar/")))

(defpage "/inicio/" []
  (let [logs [{:date "26/06/2012" :content "Se agregó un nuevo artículo" :link "/logs/26062012"}
              {:date "27/06/2012" :content "Se eliminó un artículo" :link "/logs/27062012"}]
        content {:title "Inicio"
                 :content (home-content logs)}]
    (main-layout content)))

(defpage "/entrar/" []
  (let [content {:title "Ingresar"
                 :content (login-form)}]
    (main-layout content)))

(defpage [:post "/entrar/"] {:as user}
  (if (users/login-init! (:password user))
    (resp/redirect "/inicio/")
    (do (session/flash-put! :messages '({:type "error" :text "Password inválido"}))
        (render "/entrar/"))))

(defpage "/salir/" []
  (session/clear!)
  (resp/redirect "/entrar/"))

