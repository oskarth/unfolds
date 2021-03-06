(ns unfolds.core
  (:require [com.stuartsierra.component :as component]
            [unfolds.system :as system]))

(def servlet-system (atom nil))


;; =============================================================================
;; Development

(defn dev-start []
  (let [sys  (system/dev-system
               {:db-uri   "datomic:mem://localhost:4334/unfolds"
                :web-port 8080})
        sys' (component/start sys)]
    (reset! servlet-system sys')
    sys'))

;; =============================================================================
;; Production

(defn service [req]
  ((:handler (:webserver @servlet-system)) req))

(defn start []
  (let [s (system/prod-system
            {:db-uri   "datomic:free://localhost:4334/unfolds"})]
    (let [started-system (component/start s)]
      (reset! servlet-system started-system))))

(defn stop [])

(comment

   ;; HACKY PROD REPL

  ;; sudo lein repl
  (use 'unfolds.core)
  (use 'ring.adapter.jetty)
  (start)
  (def jetty (run-jetty service {:port 80 :join? false}))

  ;; then to stop it
  (.stop jetty)

)
