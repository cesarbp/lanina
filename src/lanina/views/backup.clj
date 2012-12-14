(ns lanina.views.backup
  (:use noir.core
        [lanina.models.backup :as model])
  (:require [noir.response :as resp]))

(defpage "/backup/list/" {:keys [html]}
  (let [type :list]
    (model/add-backup html type)
    (resp/json "success")))

(defpage "/backup/list/get/" []
  (let [latest (:html (model/get-latest :list))]
    (if (seq latest)
      (resp/json latest)
      (resp/json ""))))