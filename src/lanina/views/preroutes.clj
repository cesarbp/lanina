(ns lanina.views.preroutes
  (:use [noir.core :only [pre-route]]
        [lanina.views.utils :only [flash-message]])
  (:require [noir.response :as resp]
            [lanina.models.user :as users]))

(defn deny-access [redirect-url]
  (when-not (users/admin?)
    (flash-message "No tiene permisos para entrar a esta página" "error")
    (resp/redirect redirect-url)))

(defn deny-when-unsigned []
  (when-not (users/logged-in?)
    (flash-message "Necesita estar firmado para entrar a esta página" "error")
    (resp/redirect "/entrar/")))

(defn deny-and-home []
  (deny-access "/ventas/"))

(pre-route "/ajustes/*" []
           (deny-and-home))
(pre-route "/respaldos/*" []
           (deny-and-home))
(pre-route "/articulos/modificar/*" []
           (deny-and-home))
(pre-route "/articulos/eliminar/*" []
           (deny-and-home))
(pre-route "/articulos/global/*" []
           (deny-and-home))
(pre-route "/articulos/proveedor/*" []
           (deny-and-home))
(pre-route "/articulos/agregar/*" []
           (deny-and-home))
(pre-route "/listas/compras/*" []
           (deny-and-home))
(pre-route "/reportes/*" []
           (deny-and-home))
(pre-route "/impresiones/*" []
           (deny-and-home))
(pre-route "/catalogos/:id/borrar/*" []
           (deny-and-home))
(pre-route "/catalogos/:id/modificar/*" []
           (deny-and-home))
(pre-route "/catalogos/categorias/borrar/" []
           (deny-and-home))

(pre-route "/articulos/*" []
           (deny-when-unsigned))
(pre-route "/ventas/*" []
           (deny-when-unsigned))
(pre-route "/tickets/*" []
           (deny-when-unsigned))
(pre-route "/listas/*" []
           (deny-when-unsigned))
(pre-route "/inicio/*" []
           (deny-when-unsigned))
(pre-route "/catalogos/" []
           (deny-when-unsigned))
(pre-route "/catalogos/categorias/" []
           (deny-when-unsigned))
(pre-route "/creditos/*" []
           (deny-when-unsigned))
