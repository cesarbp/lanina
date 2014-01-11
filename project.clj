(defproject lanina "2.0.1"
  :author "César Bolaños Portilla"
  :description "Sitio de Administración de un Super Mercado"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojars.hozumi/clj-commons-exec "1.0.6"]
                 [noir "1.3.0-beta8"]
                 [clj-http "0.4.0"]
                 [congomongo "0.1.9"]
                 ;; [local/javadbf "0.4.4"]
                 [clojure-csv "2.0.0-alpha2"]
                 [clj-pdf "1.11.7"]]
  ;; :repositories {"project" "file:repo"
  ;;                "local" ~(str (.toURI (java.io.File. "maven_repository")))}
  :main lanina.server
  :profiles {:uberjar {:aot :all}})
