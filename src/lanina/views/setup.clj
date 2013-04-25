;;; Initialize the entire database
;;; Deletes existing ones, only for development!!
(ns lanina.views.setup
  (:use [noir.core :only [defpage]])
  (:require [lanina.models.article :as article :only [setup!]]
            [lanina.models.adjustments :as adjustments :only [setup!]]
            [lanina.models.backup :as backup :only [setup!]]
            [lanina.models.ticket :as ticket :only [setup!]]
            [lanina.models.user :as user :only [setup!]]
            [lanina.models.logs :as logs :only [setup!]]))

(defn initialize!  []
  (do
    (article/setup!)
    (adjustments/setup!)
    (backup/setup!)
    (ticket/setup!)
    (user/setup! "password" "empleado")
    (logs/setup!)
    "done!"))
