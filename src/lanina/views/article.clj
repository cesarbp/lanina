(ns lanina.views.article
  (:use noir.core
        lanina.views.common
        hiccup.form
        [hiccup.element :only [link-to]])
  (:require [lanina.models.article :as article]
            [clojure.data.json :as json]))

;;; Used by js to get the article in an ajax way
(defpage "/json/article" {:keys [barcode]}
  (let [response (json/json-str (article/get-article barcode))]
    (if (not= "null" response)
      response
      "{}")))

(defpartial barcode-form []
  (form-to {:id "barcode-form"} [:get "/test/"]
    [:fieldset
     [:div.field
      (text-field {:id "barcode-field1"} "barcode-field")]]))

(defpartial test-js []
  (link-to {} "#" "test"))

(defpage "/test/" []
  (main-layout-incl {:content (barcode-form)} [:base-css :jquery :test-js]))