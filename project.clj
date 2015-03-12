(defproject unfolds "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :jvm-opts ^:replace ["-Xms512m" "-Xmx512m" "-server"]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2371" :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [ring "1.3.1"]
                 [com.datomic/datomic-free "0.9.5130" :exclusions [joda-time]]
                 [fogus/ring-edn "0.2.0"]
                 [bidi "1.18.7"]
                 [om "0.7.3"]
                 [environ "1.0.0"]
                 [secretary "1.2.1"]
                 ;;[figwheel "0.1.4-SNAPSHOT"]
                 ;;[com.cemerick/piggieback "0.1.3"]
                 ;;[weasel "0.4.0-SNAPSHOT"]
                 [com.stuartsierra/component "0.2.3"]
                 [leiningen "2.5.0"]]

  :source-paths ["src/clj" "src/cljs"]

  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-ring "0.9.2"]
            [lein-cljfmt "0.1.10"]
            [lein-environ "1.0.0"]]

  :ring {:handler unfolds.core/service
         :init unfolds.core/start
         :destroy unfolds.core/stop}

  :cljsbuild {
    :builds [
      {:id "dev"
       :source-paths ["src/cljs"]
       :compiler {
         :output-to "resources/public/js/main.js"
         :output-dir "resources/public/js/out"
         :optimizations :none
         :source-map true
       }}
      {:id "release"
       :source-paths ["src/cljs"]
       :compiler {
         :output-to "resources/public/js/main.js"
         :optimizations :advanced
         :output-wrapper true
         :pretty-print false
         :preamble ["react/react.min.js"]
         :externs ["react/externs/react.js"]
       }}
    ]
  })
