(ns lanina.views.reports
  (:use noir.core
        hiccup.form
        lanina.views.common)
  (:require [lanina.views.utils :as t]
            [lanina.models.article :as article]
            [lanina.models.ticket :as ticket]))

(defpage "/reportes/" {:keys [periodo fecha_desde fecha_hasta]})
