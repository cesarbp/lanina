(defproject lanina "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojars.hozumi/clj-commons-exec "1.0.6"]
                 [noir "1.3.0-beta8"]
                 [clj-http "0.4.0"]
                 [congomongo "0.1.9"]
                 ;; [local/javadbf "0.4.4"]
                 [clojure-csv "2.0.0-alpha2"]]
  ;; :repositories {"project" "file:repo"
  ;;                "local" ~(str (.toURI (java.io.File. "maven_repository")))}
  :main lanina.server
  :aot [lanina.server])
