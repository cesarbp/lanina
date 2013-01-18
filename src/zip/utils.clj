(ns zip.utils
  "import-static and some file utils"
  (:use [clojure.set])
  (:require [clojure.java.io :as io]))

(defmacro import-static [class & fields-and-methods]
  (let [only (set (map str fields-and-methods))
        the-class (. Class forName (str class))
        static? (fn [x]
                  (. java.lang.reflect.Modifier
                     (isStatic (. x (getModifiers)))))
        statics (fn [array]
                  (set (map (memfn getName)
                            (filter static? array))))
        all-fields (statics (. the-class (getFields)))
        all-methods (statics (. the-class (getMethods)))
        fields-to-do (intersection all-fields only)
        methods-to-do (intersection all-methods only)
        make-sym (fn [string]
                   (with-meta (symbol string) {:private true}))
        import-field (fn [name]
                       (list 'def (make-sym name)
                             (list '. class (symbol name))))
        import-method (fn [name]
                        (list 'defmacro (make-sym name)
                              '[& args]
                              (list 'list ''. (list 'quote class)
                                    (list 'apply 'list
                                          (list 'quote (symbol name))
                                          'args))))]
    `(do ~@(map import-field fields-to-do)
         ~@(map import-method methods-to-do))))

(defn str-contains? [s key]
  (if (= (type key) java.lang.String)
    (.contains s key)
    (some #(.contains s %) key)))

(defn fix-dir-name [filename]
  (if (and (.isDirectory (io/file filename)) (not= \/ (last filename)))
    (str filename "/")
    filename))

(defn list-files [path]
  (let [f (io/file path)]
    (if (.isDirectory f)
      (apply vector f (map list-files (.listFiles f)))
      [f])))

(defn relative-path [root file]
  (.. (io/file root) toURI (relativize (.toURI (io/file file))) toString))

(defn make-relative-entry [root file]
  (let [f (io/file file)
        filename (relative-path root file)]
    (if (.isDirectory f)
      {:filename filename :directory? true}
      {:filename filename :inputstream (delay (io/input-stream f))})))
