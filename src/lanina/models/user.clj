(ns lanina.models.user
  (:use
   somnium.congomongo)
  (:require
   [noir.util.crypt     :as crypt]
   [noir.validation     :as vali]
   [noir.session        :as session]
   [lanina.models.utils :as db]))

(defn admin? []
  (session/get :admin))

(defn employee? []
  (session/get :employee))

(defn logged-in? []
  (or (employee?) (admin?)))

(def users-coll :users)

(def users ["employee" "admin"])

(def verbose
  {"employee" "Empleado"
   "admin"    "Administrador"})

(defn setup! [admin-pass employee-pass]
  (db/maybe-init)
  (when-not (and (collection-exists? users-coll)  (seq (fetch-one users-coll :where {:name "empleado"})))
    (db/maybe-init)
    (create-collection! users-coll)
    (insert! users-coll {:name "admin" :pass (crypt/encrypt admin-pass)})
    (insert! users-coll {:name "employee" :pass (crypt/encrypt employee-pass)})))

(defn login! [user pass]
  "Logs in a user, creates the session and returns false if it fails"
  (db/maybe-init)
  (if-let [correct-pass (:pass (fetch-one :users :where {:name (name user)} :only [:pass]))]
    (when (crypt/compare pass correct-pass)
      (session/put! :name (name user))
      (cond (= "admin" (name user))
            (session/put! :admin true)
            (= "employee" (name user))
            (session/put! :employee true)))
    false))

(defn verify-pass [user pass]
  "Requires that the admin user is already created or it explodes"
  (db/maybe-init)
  (crypt/compare pass (:pass (fetch-one :users :where {:name (name user)} :only [:pass]))))

(defn reset-pass! [user pass]
  (when (<= 6 (count pass))
    (let [usr (fetch-one :users :where {:name (name user)}) ]
      (update! :users usr (merge usr {:pass (crypt/encrypt pass)})))))