(ns lanina.utils)

(defn coerce-to
  ([jclass] (coerce-to jclass nil))
  ([jclass default]
     (fn [arg]
       (try (eval `(new ~jclass ~arg))
            (catch Exception e default)))))