(ns lanina.views.cashier
  (:use noir.core
        hiccup.form
        lanina.views.common
        [lanina.views.utils :only [format-decimal flash-message]]
        [lanina.models.article :only [is-number?]]
        [lanina.utils :only [coerce-to]])
  (:require [lanina.models.user :as users]
            [lanina.models.cashier :as cashier]
            [noir.response :as resp]))

(defpartial cashier-form
  []
  (let [adm (users/admin?)
        opn (cashier/cashier-is-open?)]
    [:div.container-fluid
     [:div.alert.alert-info
      "La caja está " [:strong (if opn "abierta" "cerrada")]
      (when adm [:div " Dinero en la caja: " [:strong (format-decimal (cashier/get-current-cash))]])]
     (if opn
       (form-to {:class "form-horizontal"} [:post "/caja/modificar/"]
                [:div.control-group
                 (label {:class "control-label"} :add "Agregar dinero")
                 [:div.controls
                  (text-field {:autocomplete "off"} :add)]]
                [:div.control-group
                 (label {:class "control-label"} :withdraw "Retirar dinero")
                 [:div.controls
                  (text-field {:autocomplete "off"} :withdraw)]]
                [:div.form-actions
                 (submit-button {:class "btn btn-primary" :name :submit} "Continuar")
                 (when adm
                   (submit-button {:class "btn btn-danger" :name :close} "Cerrar la caja"))])
       (if adm
         (form-to {:class "form-horizontal"} [:post "/caja/modificar/"]
                  [:div.control-group
                   (label {:class "control-label"} :amt "Cantidad para abrir la caja")
                   [:div.controls
                    (text-field {:autocomplete "off"} :amt)]]
                  [:div.form-actions
                   (submit-button {:name :open :class "btn btn-primary"} "Abrir la caja")])
         [:div.alert.alert-error
          "Contacte a un administrador para abir la caja."]))]))

(defpage "/caja/" []
  (let [content {:title "Caja"
                 :active "Caja"
                 :nav-bar true
                 :content (cashier-form)}]
    (home-layout content)))

(defpage [:post "/caja/modificar/"] {:keys [add withdraw submit close open amt]}
  (let [resp
        (cond submit (cond (and (seq add) (is-number? add))
                           (cashier/add-money ((coerce-to Double) add))
                           (and (seq withdraw) (is-number? withdraw))
                           (cashier/withdraw-money ((coerce-to Double) withdraw)))
              close (cashier/close-cashier)
              open (when (and (seq amt)) (is-number? amt)
                         (cashier/open-cashier ((coerce-to Double) amt))))]
    (if resp
      (do
        (flash-message "Operación exitosa." "success")
        (resp/redirect "/ventas/"))
      (do
        (flash-message "Error. Verifique sus entradas." "error")
        (resp/redirect "/caja/")))))
