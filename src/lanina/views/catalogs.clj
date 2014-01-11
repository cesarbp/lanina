(ns lanina.views.catalogs
  (:use lanina.views.common
        noir.core
        hiccup.form)
  (:require [lanina.models.catalogs :as catalog]
            [clojure.string :as s]
            [lanina.utils :refer [coerce-to]]
            [lanina.views.utils :refer [flash-message url-encode]]
            [hiccup.element :refer [link-to]]
            [noir.response :as resp]))

(defpartial type-select
  [name]
  (let [types catalog/valid-types]
    [:select {:name name}
     (for [t types]
       [:option {:value t} t])]))

(defpartial choose-number-fields-form
  []
  (form-to {:class "form form-horizontal"} [:get "/catalogos/categorias/nuevo/"]
           [:legend "Creando una nueva categoría"]
           [:div.control-group
            (label {:class "control-label"} :n "Número de campos (extra) en la categoría")
            [:div.controls
             (text-field :n)]]
           [:div.form-actions
            (submit-button {:class "btn btn-primary"} "Continuar")
            (link-to {:class "btn btn-success"} "/catalogos/categorias/" "Regresar")]))

(defpartial create-type-form
  "Define the fields of a new category with their labels and data types"
  [nfields]
  (let [label-start "Campo"
        name-start "f"
        type-start "t"]

    (form-to {:class "form"} [:post "/catalogos/categorias/nuevo/"]
             [:legend "Crear una nueva categoría de catálogos"]
             ;; category name
             [:div.control-group
              (label {:class "control-label"} :category "Nombre de la categoría")
              [:div.controls
               (text-field :category)]]
             [:h4 "Campos de cada entrada de la categoría"]
             [:table.table.table-condensed
              [:tr
               [:th "Campo"]
               [:th "Nombre"]
               [:th "Categoría"]]
              ;; System defined fields (just for show here)
              ;; catalog number
              [:tr
               [:td
                "Campo predefinido"]
               [:td
                (text-field {:disabled "disabled" :placeholder "NUMERO DE CATALOGO"}
                            "NUMERO DE CATALOGO")]
               [:td [:select {:disabled "disabled"} [:option "entero"]]]]
              ;; catalog name
              [:tr
               [:td
                "Campo predefinido"]
               [:td
                (text-field {:disabled "disabled" :placeholder "NOMBRE"} "NOMBRE")]
               [:td [:select {:disabled "disabled"} [:option "cadena"]]]]
              ;; catalog type
              [:tr
               [:td
                "Campo predefinido"]
               [:td
                (text-field {:disabled "disabled" :placeholder "TIPO"} "TIPO")]
               [:td [:select {:disabled "disabled"} [:option "cadena"]]]]
              [:tr [:td {:colspan "3"}
                    [:p.alert.alert-warning
                     "Nota: evite usar acentos en los nombres de los campos"]]]
              ;; User defined fields
              (for [n (range nfields) :let [i (inc n)
                                            labeln (str label-start " " i)
                                            name (keyword (str name-start i))
                                            type-name (keyword (str type-start i))]]

                [:tr
                 [:td
                  labeln]
                 [:td
                  (text-field name)]
                 [:td (type-select type-name)]])]

             [:div.form-actions
              (submit-button {:class "btn btn-primary"} "Crear Categoría")
              (link-to {:class "btn btn-success"} "/catalogos/categorias/nuevo/" "Regresar")])))

(defpage "/catalogos/restart/"
  []
  (catalog/setup!)
  "done")

(defpage "/catalogos/categorias/nuevo/"
  {:keys [n]}
  (let [n ((coerce-to Long) n)
        content {:title "Nueva categoría"
                 :content [:div.container-fluid
                           (if n
                             (create-type-form n)
                             (choose-number-fields-form))]
                 :active "Extras"
                 :nav-bar :true}]
    (home-layout content)))

(defpage [:post "/catalogos/categorias/nuevo/"]
  {:as pst}
  (let [cat (:category pst)
        fnames-keys (filter #(.startsWith (name %) "f") (keys pst))
        fnames (map pst fnames-keys)
        types (map (fn [fname-key]
                     (->> fname-key
                          (name)
                          (re-matches #"f(\d+)")
                          (second)
                          (str "t")
                          (keyword)
                          (pst)))
                   fnames-keys)

        resp (catalog/add-type! cat (zipmap fnames types))]
    (if (not= :success resp)
      (do (flash-message "Error, verifique que haya introducido todos los campos y el nombre de la categoría no esté repetido" "error")
          (resp/redirect "/catalogos/categorias/nuevo/"))
      (do (flash-message "Categoría creada" "success")
          (resp/redirect "/catalogos/categorias/")))))

;====================
;  Adding an entry  ;
;====================
(defpartial choose-entry-type-form
  []
  (form-to {:class "form-inline"} [:get "/catalogos/entradas/nuevo/"]
           (label {:class "control-label"} :cat "Crear un(a) nuevo(a)")
           [:select {:name "cat"}
            (for [c (catalog/get-all-type-names)]
              [:option {:value c} c])]
           (submit-button {:class "btn btn-primary"} "Continuar")))

(defpartial new-entry-form
  [category]
  (let [cat (catalog/get-type category)]
    (if cat
      (form-to {:class "form form-horizontal"} [:post "/catalogos/entradas/nuevo/"]
               [:p.alert.alert-warning
                "Nota: Todos los campos son obligatorios"]
               (hidden-field :cat category)
               [:legend "Agregar un(a) " (:type cat)]
               [:p "NUMERO DE CATALOGO " (catalog/get-next-cat-number)]
               (for [[field type] (dissoc (:fields-types cat) (keyword "NUMERO DE CATALOGO") :TIPO)]
                 [:div.control-group
                  (label {:class "control-label"} field field)
                  [:div.controls
                   (text-field {:placeholder type} field)]])
               [:div.form-actions
                (submit-button {:class "btn btn-primary"} "Agregar")])
      "No encontrado")))

(defpage "/catalogos/entradas/nuevo/"
  {:keys [cat]}
  (let [content {:content [:div.container-fluid
                           (if (seq cat)
                             (new-entry-form cat)
                             (choose-entry-type-form))]
                 :title "Agregar catalogo"
                 :nav-bar true
                 :active "Extras"}]
    (home-layout content)))

(defpage [:post "/catalogos/entradas/nuevo/"]
  {:as pst}
  (let [cat (:cat pst)
        resp (catalog/add-entry! cat (dissoc pst :cat))]
    (if (= :success resp)
      (do (flash-message (str "Se ha agregado el/la " cat) "success")
          (resp/redirect (str "/catalogos/?cat=" cat)))
      (do (flash-message (str "Ocurrio un error, verifique sus entradas") "error")
          (render (str "/catalogos/entradas/nuevo/?cat=" cat))))))

;================
; Show catalogs ;
;================
(defpartial show-catalogs
  [cat]
  (let [categories (if (and cat (not= "TODAS" cat))
                     [cat]
                     (sort (catalog/get-all-type-names)))]
    (for [c categories :let [fields-types (:fields-types (catalog/get-type c))
                             order (into [(keyword "NUMERO DE CATALOGO") :NOMBRE]
                                         (keys (dissoc fields-types
                                                       (keyword "NUMERO DE CATALOGO")
                                                       :NOMBRE :TIPO)))]]
      (list
       [:h3 c]
       [:table.table.table-condensed.table-hover
        [:tr
         (for [k order]
           [:th (name k)])
         [:th]
         [:th]]
        (for [r (catalog/get-all-of-type c)]
          [:tr
           (for [k order]
             [:td (r k)])
           [:td
            (link-to {:class "btn btn-warning"} (str "/catalogos/"
                                                     (r (keyword "NUMERO DE CATALOGO"))
                                                     "/modificar/?nombre="
                                                     (url-encode (r :NOMBRE)))
                     "<i class=\"icon-pencil\"></i>")]
           [:td
            (link-to {:class "btn btn-danger"} (str "/catalogos/"
                                                     (r (keyword "NUMERO DE CATALOGO"))
                                                     "/borrar/?nombre="
                                                     (url-encode (r :NOMBRE)))
                     "<i class=\"icon-remove\"></i>")]])]))))

(defpartial choose-shown-category-form
  []
  (form-to {:class "form-inline"} [:get "/catalogos/"]
           (label :cat "Mostrar &nbsp")
           [:select {:name :cat}
            [:option {:value "TODAS"} "TODAS"]
            (for [c (catalog/get-all-type-names)]
              [:option {:value c} c])]
           (submit-button {:class "btn btn-primary"} "Cambiar")))

(defpage "/catalogos/"
  {:keys [cat]}
  (let [content {:content [:div.container-fluid
                           [:h2 "Catálogos"]
                           [:div.row-fluid
                            [:div.span6
                             (choose-shown-category-form)]
                            [:div.span6
                             (choose-entry-type-form)]]
                           [:h2.alert.alert-warning
                            "Para buscar use CTRL + F"]
                           (show-catalogs cat)]
                 :active "Extras"
                 :nav-bar true
                 :title "Catálogos"}]
    (home-layout content)))

;===================
; Erase and modify ;
;===================
(defpartial edit-entry-form
  [n & [name]]
  (let [entry (catalog/get-entry n name)
        types (:fields-types (catalog/get-type (:TIPO entry)))
        entry (dissoc entry :_id :TIPO)]
    (form-to {:class "form form-horizontal"}
             [:post (str "/catalogos/"
                         (entry (keyword "NUMERO DE CATALOGO"))
                         "/modificar/")]
             (when (seq name)
               (hidden-field :prev-name name))
             (for [[k v] entry]
               [:div.control-group
                (label {:class "control-label"} k k)
                [:div.controls
                 (text-field {:placeholder (types k)} k v)]])
             [:div.form-actions
              (submit-button {:class "btn btn-warning"} "Modificar")
              (link-to {:class "btn btn-success"} "/catalogos/" "Regresar")])))

(defpartial erase-entry-form
  [n & [name]]
  (let [entry (dissoc (catalog/get-entry n name) :_id)]
    (form-to {:class "form form-horizontal"}
             [:post (str "/catalogos/"
                         (entry (keyword "NUMERO DE CATALOGO"))
                         "/borrar/")]
             (when (seq name)
               (hidden-field :prev-name name))
             (for [[k v] entry]
               [:div.control-group
                (label {:class "control-label"} k k)
                [:div.controls
                 (text-field {:placeholder v :disabled "disabled"} k v)]])
             [:div.form-actions
              (submit-button {:class "btn btn-danger"} "Borrar")
              (link-to {:class "btn btn-success"} "/catalogos/" "Regresar")])))

(defpage "/catalogos/:n/modificar/"
  {:keys [nombre n]}
  (let [n ((coerce-to Long) n)
        content {:content [:div.container-fluid
                           (if n
                             (edit-entry-form n nombre)
                             [:p.alert.alert-error "No encontrado"])]
                 :title "Modificando Catalogo"
                 :nav-bar true
                 :active "Extras"}]
    (home-layout content)))

(defpage "/catalogos/:n/borrar/"
  {:keys [nombre n]}
  (let [n ((coerce-to Long) n)
        content {:content [:div.container-fluid
                           (if n
                             (erase-entry-form n nombre)
                             [:p.alert.alert-error "No encontrado"])]
                 :title "Borrando Catalogo"
                 :nav-bar true
                 :active "Extras"}]
    (home-layout content)))

(defpage [:post "/catalogos/:n/modificar/"]
  {:keys [prev-name n] :as pst}
  (let [n ((coerce-to Long) n)
        pst (-> pst
                (dissoc :n :prev-name)
                (assoc (keyword "NUMERO DE CATALOGO") (get pst "NUMERO DE CATALOGO"))
                (dissoc "NUMERO DE CATALOGO"))
        resp (catalog/modify-entry n prev-name pst)]
    (if (= :success resp)
      (do (flash-message "Se ha modificado el catalogo" "success")
          (resp/redirect "/catalogos/"))
      (do (flash-message (str "Ocurrio un error, verifique sus entradas") "error")
          (render (str "/catalogos/" n "/modificar/" (when prev-name (str "?nombre="
                                                                          (url-encode prev-name)))))))))

(defpage [:post "/catalogos/:n/borrar/"]
  {:keys [prev-name n] :as pst}
  (let [n ((coerce-to Long) n)
        resp (catalog/delete-entry n prev-name)]
    (if (= :success resp)
      (do (flash-message "Se ha borrado el catalogo" "success")
          (resp/redirect "/catalogos/"))
      (do (flash-message (str "Ocurrio un error, verifique sus entradas") "error")
          (render (str "/catalogos/" n "/modificar/" (when prev-name (str "?nombre="
                                                                          (url-encode prev-name)))))))))

;==================
; Show categories ;
;==================
(defpartial show-cat
  [cat]
  (let [cats (if (and cat (not= "TODAS" cat))
               [cat]
               (catalog/get-all-type-names))]
    (for [c cats :let [m (catalog/get-type c)]]
      (list [:h3 {:style "display:inline;"} (:type m)]
            "&nbsp&nbsp"
            (link-to {:class "btn btn-danger" :style "position:relative;bottom:6px;"}
                     (str "/catalogos/categorias/borrar/?cat="
                          (url-encode c))
                         "Borrar")
            [:table.table.table-condensed.table-hover
             [:tr
              [:th "Campo"]
              [:th "Tipo de dato"]]
             (for [[f t] (:fields-types m)]
               [:tr
                [:td f]
                [:td t]])]))))

(defpartial choose-category-form
  []
  (form-to {:class "form-inline"} [:get "/catalogos/categorias/"]
           (label :cat "Mostrar &nbsp")
           [:select {:name :cat}
            [:option {:value "TODAS"} "TODAS"]
            (for [c (catalog/get-all-type-names)]
              [:option {:value c} c])]
           (submit-button {:class "btn btn-primary"} "Cambiar")))

(defpage "/catalogos/categorias/"
  {:keys [cat]}
  (let [content {:content [:div.container-fluid
                           [:h2 "Categorías"]
                           [:div.row-fluid
                            [:div.span6
                             (choose-category-form)]
                            [:div.span6
                             (link-to {:class "btn btn-primary"}
                                      "/catalogos/categorias/nuevo/"
                                      "Crear una nueva categoria")]
                            ]
                           (show-cat cat)]
                 :title "Categorías de catalogos"
                 :active "Extras"
                 :nav-bar true}]
    (home-layout content)))

;=================
; Erase category ;
;=================
(defpartial erase-category-form
  [cat]
  (let [m (catalog/get-type cat)]
    (list
     [:p.alert.alert-error "*** ESTO BORRARA TODOS LOS REGISTROS EN ESTA CATEGORIA ***"]
     (form-to {:class "form form-horizontal"} [:post "/catalogos/categorias/borrar/"]
              (hidden-field :cat cat)
              [:h2 "Borrando " (:type m)]
              [:p.alert.alert-info
               "Registros: " (count (catalog/get-all-of-type cat))]
              [:table.table.table-condensed.table-hover
               [:tr
                [:th "Campo"]
                [:th "Tipo de dato"]]
               (for [[f t] (:fields-types m)]
                 [:tr
                  [:td (label f f)]
                  [:td (text-field {:disabled "disabled" :placeholder t} t)]])]
              [:div.form-actions
               (submit-button {:class "btn btn-danger"} "Borrar")
               "&nbsp&nbsp&nbsp&nbsp"
               (link-to {:class "btn btn-success"} "/catalogos/categorias/" "Cancelar")]))))

(defpage [:post "/catalogos/categorias/borrar/"]
  {:keys [cat]}
  (let [resp (catalog/delete-category cat)]
    (if (= :success resp)
      (do (flash-message "Se ha borrado la categoria" "success")
          (resp/redirect "/catalogos/categorias/"))
      (do (flash-message (str "Ocurrio un error, verifique que la categoria exista") "error")
          (render (str "/catalogos/categorias/" ))))))

(defpage "/catalogos/categorias/borrar/"
  {:keys [cat]}
  (let [content {:content
                 [:div.container-fluid
                  (if cat
                    (erase-category-form cat)
                    (link-to {:class "btn btn-primary"} "/catalogos/categorias/" "Regresar"))]
                 :title "Borrar una categoria"
                 :active "Extras"
                 :nav-bar true}]
    (home-layout content)))
