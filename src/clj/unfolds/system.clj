(ns unfolds.system
  (:require [com.stuartsierra.component :as component]
            unfolds.server
            unfolds.datomic))

(defn dev-system [config-options]
  (let [{:keys [db-uri web-port]} config-options]
    (component/system-map
      :db (unfolds.datomic/new-database db-uri)
      :webserver
      (component/using
        (unfolds.server/dev-server web-port)
        {:datomic-connection  :db}))))

;; TODO: Don't create a new database all the time
;; What is the alternative?
(defn prod-system [config-options]
  (let [{:keys [db-uri]} config-options]
    (component/system-map
      :db (unfolds.datomic/existing-database db-uri)
      :webserver
      (component/using
        (unfolds.server/prod-server)
        {:datomic-connection :db}))))

(comment
  ;; This component bla is too complicated. Just give me basic ENVs.

  (def s (dev-system {:db-uri   "datomic:mem://localhost:4334/unfolds"
                      :web-port 8080}))
  (def s1 (component/start s))

  ;; why doesn't this work but above does?
  ;; this doesn't take a web-port, because of some container logic in server.clj
  (def sp (prod-system {:db-uri   "datomic:free://localhost:4334/unfolds"}))
  (def s2 (component/start sp))

  )
