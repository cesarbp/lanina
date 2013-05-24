;;; Initialize the entire database
;;; Deletes existing ones, only for development!!
(ns lanina.views.setup
  (:use [noir.core :only [defpage]])
  (:require [lanina.models.article :as article :only [setup!]]
            [lanina.models.adjustments :as adjustments :only [setup!]]
            [lanina.models.backup :as backup :only [setup!]]
            [lanina.models.ticket :as ticket :only [setup!]]
            [lanina.models.user :as user :only [setup!]]
            [lanina.models.logs :as logs :only [setup!]]
            [lanina.models.printing :as printing :only [setup!]]
            [lanina.models.catalogs :as catalogs :only [setup!]]
            [lanina.models.shopping :as shopping :only [setup!]]
            [lanina.models.cashier :as cashier :only [setup!]]
            [lanina.models.db-backup :as db-backup :only [setup!]]))

(defn initialize!  []
  (do
    (adjustments/setup!)
    (printing/setup!)
    (catalogs/setup!)
    (cashier/setup!)
    (db-backup/setup!)
    (article/setup!)
    (backup/setup!)
    (ticket/setup!)
    (user/setup! "password" "empleado")
    (logs/setup!)
    (shopping/setup!)
    "done!"))
