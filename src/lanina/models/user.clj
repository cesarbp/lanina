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

(defn verify-pass [pass]
  "Requires that the admin user is already created or it explodes"
  (db/maybe-init)
  (crypt/compare pass (:pass (fetch-one :users :where {:name "admin"} :only [:pass]))))

(defn reset-pass! [pass]
  (when (<= 6 (count pass))
    (let [usr (fetch-one :users :where {:name "admin" }) ]
      (update! :users usr (merge usr {:pass (crypt/encrypt pass)})))))