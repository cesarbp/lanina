(ns lanina.views.home
  (:use noir.core
        lanina.views.common
        [hiccup.element :only [link-to]]
        hiccup.form)
  (:require [noir.response :as resp]
            [noir.session :as session]
            [lanina.models.user :as users]
            [lanina.models.logs :as logs]))

(defpage "/" []
  (if (users/logged-in?)
    (if (users/admin?)
      (resp/redirect "/reportes/")
      (resp/redirect "/ventas/"))
    (resp/redirect "/entrar/")))

(defpartial user-select []
  (let [users users/users
        verbose users/verbose]
    [:select {:name :user}
     (map (fn [usr] [:option {:value usr} (verbose usr)])
          users)]))

(defpartial login-form []
  (form-to {:class "form-horizontal"} [:post "/entrar/"]
    [:fieldset
     [:div.control-group
      (label {:class "control-label"} :user "Usuario")
      [:div.controls (user-select)]]
     [:div.control-group
      (label {:class "control-label"} "password" "Contraseña")
      [:div.controls
       (password-field {:id "password"} "password")]]
     [:div.form-actions
      (submit-button {:class "btn btn-primary" :name "submit"} "Entrar")
      ;; (link-to {:class "btn btn-warning"} "/auth/reset_pass/" "Reiniciar contraseña")
      ]]))

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
    (if (users/logged-in?)
      (resp/redirect "/inicio/")
      (main-layout content))))

(defpage [:post "/entrar/"] {:as pst}
  (let [usr (:user pst)
        pass (:password pst)]
    (if (users/login! usr pass)
      (do (session/flash-put! :messages (list {:type "alert-success" :text (str "Se ha firmado como " (users/verbose usr) ".")}))
          (resp/redirect "/inicio/"))
      (do (session/flash-put! :messages '({:type "alert-error" :text "Contraseña inválida"}))
          (render "/entrar/")))))

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

;;; Show Logs
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

(defpage "/inicio/" []
  (let [lgs (logs/retrieve-all)
        lgs-usable
        (when (seq lgs)
          (map (fn [l]
                 {:date (:date l)
                  :content (cond (= "deleted" (:type l)) "Se eliminó un artículo"
                                 (= "updated" (:type l)) "Se modificó un artículo"
                                 (= "added" (:type l)) "Se agregó un nuevo artículo")
                  :link (str "/logs/" (:date l))})
               lgs))
        content {:title "Inicio"
                 :content [:div.container (home-content (or lgs-usable {}))]
                 :active "Inicio"}]
    (home-layout content)))
