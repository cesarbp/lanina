(ns lanina.views.home
  (:use noir.core
        lanina.views.common
        [hiccup.element :only [link-to]]
        [hiccup.form])
  (:require [noir.response :as resp]
            [noir.session :as session]
            [lanina.models.user :as users]))

(defpage "/" []
  (if (users/admin?)
    (resp/redirect "/inicio/")
    (resp/redirect "/entrar/")))

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
      [:p (link-to {:id "reset-password"} "/auth/reset_pass/" "Reiniciar contraseña")]
      (submit-button {:class "submit" :name "submit"} "Entrar")])])

(defpartial reset-pass-form []
  [:div {:class "dialog"}
   (form-to {:id "login-form" :name "reset-pass"} [:post "/auth/reset_pass/"]
     [:fieldset
      [:div.field
       (label {:id "old-password-label"} "old-password" "Vieja contraseña")
       (password-field {:id "old-password"} "old-password")]]
     [:fieldset
      [:div.field
       (label {:id "new-password-label"} "new-password" "Nueva contraseña")
       (password-field {:id "new-password"} "new-password")]]
     [:fieldset.submit
      (submit-button {:class "submit" :name "submit"} "Cambiar")])])

(pre-route "/inicio/" {}
           (when-not (users/admin?)
             (session/flash-put! :messages '({:type "error" :text "Necesita estar firmado para accesar esta página"}))
             (resp/redirect "/entrar/")))

(defpage "/inicio/" []
  (let [logs [{:date "26/06/2012" :content "Se agregó un nuevo artículo" :link "/logs/26062012"}
              {:date "27/06/2012" :content "Se eliminó un artículo" :link "/logs/27062012"}]
        content {:title "Inicio"
                 :content (home-content logs)}]
    (home-layout content)))

(defpage "/entrar/" []
  (let [content {:title "Ingresar"
                 :content (login-form)}]
    (main-layout content)))

(defpage [:post "/entrar/"] {:as user}
  (if (users/login-init! (:password user))
    (resp/redirect "/inicio/")
    (do (session/flash-put! :messages '({:type "error" :text "Contraseña inválida"}))
        (render "/entrar/"))))

(defpage "/salir/" []
  (session/clear!)
  (resp/redirect "/entrar/"))

(defpage "/auth/reset_pass/" []
  (let [content {:title "Reiniciar contraseña"
                 :content (reset-pass-form)}]
    (main-layout content)))

(defpage [:post "/auth/reset_pass/"] {:as pass}
  (if (users/verify-pass (:old-password pass))
    (if (users/reset-pass! (:new-password pass))
      (do (session/flash-put! :messages '({:type "success" :text "Su contraseña ha sido cambiada"}))
          (resp/redirect "/entrar/"))
      (do (session/flash-put! :messages '({:type "error" :text "Contraseña demasiado corta, mínimo 6 caracteres"}))
          (render "/auth/reset_pass/")))
    (do (session/flash-put! :messages '({:type "error" :text "Su vieja contraseña no es correcta"}))
        (render "/auth/reset_pass/"))))

