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

(defn prod-system [config-options]
  (let [{:keys [db-uri]} config-options]
    (component/system-map
      :db (unfolds.datomic/new-database db-uri)
      :webserver
      (component/using
        (unfolds.server/prod-server)
        {:datomic-connection  :db}))))

(comment
  (def s (dev-system {:db-uri   "datomic:mem://localhost:4334/unfolds"
                      :web-port 8081}))
  (def s1 (component/start s))
  )
