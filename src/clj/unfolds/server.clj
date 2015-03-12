(ns unfolds.server
  (:require [unfolds.datomic :as datomic]
            [datomic.api :as d]
            [clojure.edn :as edn]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]]
            [com.stuartsierra.component :as component]
            [ring.util.response :refer [file-response resource-response]]
            [bidi.bidi :as bidi]
            [bidi.ring :refer [make-handler]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.edn :refer [wrap-edn-params]]))

;; =============================================================================
;; Routing

(def routes
  ["" {"/"                :index
       "/index.html"      :index
       "/items"
       {:get {[""]        :items
              ["/" :id]   :item-get}
        :post {[""]       :item-create}}
       "/search"
       {:get {["/" :subs] :search}}}])

;; =============================================================================
;; Handlers

(defn index [req]
  (assoc (resource-response "html/index.html" {:root "public"})
    :header {"Content-Type" "text/html"}))

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

;; ITEM HANDLERS

(defn items [req]
  (generate-response
   (vec
    (datomic/display-items
     (d/db (:datomic-connection req))))))

(defn item-get [req id]
  (generate-response
   (datomic/get-item
    (d/db (:datomic-connection req)) id)))

(defn search [req subs]
  (generate-response
   (datomic/search-item-title
    (d/db (:datomic-connection req)) subs)))

(defn item-create [req]
  (generate-response
   (datomic/create-item
    (:datomic-connection req)
    (:edn-params req))))

#_(defn item-update [req id]
  (generate-response
   (datomic/update-item
    (:datomic-connection req)
    (assoc (:edn-params req) :db/id id))))

;; PRIMARY HANDLER

(defn handler [req]
  (let [match (bidi/match-route
               routes
               (:uri req)
               :request-method (:request-method req))]
    ;;(println match)
    (case (:handler match)
      :index (index req)
      :items (items req)
      :item-get (item-get req (:id (:route-params match)))
      :item-create (item-create req)
      :search (search req (:subs (:route-params match)))
      
      req)))

(defn wrap-connection [handler conn]
  (fn [req] (handler (assoc req
                       :datomic-connection conn))))

(defn unfolds-handler [conn]
  (wrap-resource
    (wrap-edn-params (wrap-connection handler conn))
    "public"))

(defn unfolds-handler-dev [conn]
  (fn [req]
    ((unfolds-handler conn) req)))

(defrecord WebServer [port handler container datomic-connection]
  component/Lifecycle
  (start [component]
    ;; NOTE: fix datomic-connection
    (if container
      (let [req-handler (handler (:connection datomic-connection))
           container (run-jetty req-handler {:port port :join? false})]
       (assoc component :container container))
      ;; if no container
      (assoc component :handler (handler (:connection datomic-connection)))))
  (stop [component]
    (.stop container)))

(defn dev-server [web-port] (WebServer. web-port unfolds-handler-dev true nil))
(defn prod-server [] (WebServer. nil unfolds-handler false nil))

;; =============================================================================
;; Route Testing

(comment

  ;; get item
  (handler {:uri "/items/ea72343d-89dc-4dfc-85af-25e1113b0948"
            :request-method :get
            :datomic-connection (:connection (:db @unfolds.core/servlet-system))})

  ;; search item
  (handler {:uri "/search/Structure"
            :request-method :get
            :datomic-connection (:connection (:db @unfolds.core/servlet-system))})
  
  )
