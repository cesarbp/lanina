(ns lanina.views.utils
  (:require [clj-time.core :as t]))

(defn now []
  (let [time (t/now)]
    (str (t/day time) "/" (t/month time) "/" (t/year time))))