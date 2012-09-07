(ns lanina.views.home
  (:use noir.core
        lanina.views.common
        [hiccup.element :only [link-to]]
        hiccup.form)
  (:require [noir.response :as resp]
            [noir.session :as session]
            [lanina.models.user :as users]))

(defpage "/" []
  (if (users/admin?)
    (resp/redirect "/inicio/")
    (resp/redirect "/entrar/")))

(defpartial login-form []
  (form-to {:class "form-horizontal"} [:post "/entrar/"]
    [:fieldset
     [:div.control-group
      (label {:class "control-label"} "password" "Contraseña")
      [:div.controls
       (password-field {:id "password"} "password")]]
     [:div.form-actions
      (submit-button {:class "btn btn-primary" :name "submit"} "Entrar")
      (link-to {:class "btn btn-warning"} "/auth/reset_pass/" "Reiniciar contraseña")]]))

(defpartial reset-pass-form []
  (form-to {:class "form-horizontal" :name "reset-pass"} [:post "/auth/reset_pass/"]
    [:fieldset
     [:div.control-group
      (label {:class "control-label"} "old-password" "Vieja contraseña")
      [:div.controls
       (password-field {:id "old-password"} "old-password")]]
     [:div.control-group
      (label {:class "control-label"} "new-password" "Nueva contraseña")
      [:div.controls
       (password-field {:id "new-password"} "new-password")]]]
    [:div.form-actions
     (submit-button {:class "btn btn-warning" :name "submit"} "Cambiar")]))


(defpage "/entrar/" []
  (let [content {:title "Ingresar"
                 :content [:div.container
                           (login-form)
                           [:script "$('#password').focus();"]]}]
    (main-layout content)))

(defpage [:post "/entrar/"] {:as user}
  (if (users/login-init! (:password user))
    (resp/redirect "/inicio/")
    (do (session/flash-put! :messages '({:type "alert-error" :text "Contraseña inválida"}))
        (render "/entrar/"))))

(defpage "/salir/" []
  (session/clear!)
  (resp/redirect "/entrar/"))

(defpage "/auth/reset_pass/" []
  (let [content {:title "Reiniciar contraseña"
                 :content [:div.container (reset-pass-form)]}]
    (main-layout content)))

(defpage [:post "/auth/reset_pass/"] {:as pass}
  (if (users/verify-pass (:old-password pass))
    (if (users/reset-pass! (:new-password pass))
      (do (session/flash-put! :messages '({:type "alert-success" :text "Su contraseña ha sido cambiada"}))
          (resp/redirect "/entrar/"))
      (do (session/flash-put! :messages '({:type "alert-error" :text "Contraseña demasiado corta, mínimo 6 caracteres"}))
          (render "/auth/reset_pass/")))
    (do (session/flash-put! :messages '({:type "alert-error" :text "Su vieja contraseña no es correcta"}))
        (render "/auth/reset_pass/"))))

