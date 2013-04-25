(ns lanina.models.error
  (:require [lanina.models.article :as article]))

(defn find-errors-warnings []
  (let [articles (article/get-all)
        errors (map article/errors-warnings articles)
        error-arts (atom [])
        warning-arts (atom [])
        error-count (atom 0)
        warning-count (atom 0)]
    (doseq [m errors]
      (when (seq (:errors m))
        (swap! error-count inc)
        (swap! error-arts conj m))
      (when (seq (:warnings m))
        (swap! warning-count inc)
        (swap! warning-arts conj m)))
    {:error-count @error-count
     :warning-count @warning-count
     :error-articles @error-arts
     :warning-articles @warning-arts}))
