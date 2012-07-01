(ns lanina.models.user
  (:use
   somnium.congomongo
   [somnium.congomongo.config :only [*mongo-config*]])
  (:require
   [noir.util.crypt     :as crypt]
   [noir.validation     :as vali]
   [noir.session        :as session]
   [lanina.models.utils :as db]))

(defn admin? []
  (session/get :admin))

;;; Only user is the admin
(defn login-init! [pass]
  "Authenticates and creates a session or creates the admin user and the session
if the admin doesn't exist."
  (db/maybe-init)
  (if-let [admin-pass (:pass (fetch-one :users :where {:name "admin"} :only [:pass]))]
    (when (crypt/compare pass admin-pass)
      (session/put! :admin true)
      (session/put! :name "admin"))
    (when (<= 6 (count pass))
      (insert! :users {:name "admin" :pass (crypt/encrypt pass)})
      (session/put! :admin true)
      (session/put! :name "admin"))))
