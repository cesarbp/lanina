(ns lanina.views.ticket
  (:use noir.core
        lanina.views.common)
  (:require [lanina.models.article :as article]
            [lanina.views.utils :as utils]))

(defpage "/tickets/nuevo/" {:as items}
  (let [prods (zipmap (keys items) (map #(Integer/parseInt %) (vals items)))]))