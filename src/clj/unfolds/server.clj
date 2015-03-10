(ns unfolds.server
  (:require [unfolds.util :as util]
            [clojure.java.io :as io]
            [unfolds.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [clojure.set :refer [union]]
            [clojure.string :refer [split]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [resources]]
            [slingshot.slingshot :refer [try+ throw+]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [ring.middleware.reload :as reload]
            [datomic.api :as d]
            [clojure.edn :as edn]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]]
            ;;            [ring.util.response :only [response]]
            [com.stuartsierra.component :as component]
            [ring.util.response :refer [file-response resource-response]]
            [bidi.bidi :as bidi]
            [bidi.ring :refer [make-handler]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.transit :refer [wrap-transit-params]]
            [ring.middleware.transit :refer [wrap-transit-response]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
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
    (unfolds.datomic/display-items
     (d/db (:datomic-connection req))))))

(defn item-get [req id]
  (generate-response
   (unfolds.datomic/get-item
    (d/db (:datomic-connection req)) id)))

(defn search [req subs]
  (generate-response
   (unfolds.datomic/search-item-title
    (d/db (:datomic-connection req)) subs)))

(defn item-create [req]
  (generate-response
   (unfolds.datomic/create-item
    (:datomic-connection req)
    ;; must have form {:item/title "x" :item/text "y"} (and uuid?)
    (:edn-params req))))

#_(defn item-update [req id]
  (generate-response
   (unfolds.datomic/update-item
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
  (fn [req] (handler (assoc req :datomic-connection conn))))

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



;; =============================================================================
;; Temp

(comment

  ;; TODO: search-title and search-text at the same time?

  ;; remember to do ffirst
  (defn search-title [subs]
    (d/q '[:find (pull ?i [*])
           :in $ ?subs
           :where
           [(fulltext $ :item/title ?subs) [[?i]]]]
         (d/db conn)
         subs))

  (defn search-text [subs]
    (d/q '[:find (pull ?i [*])
           :in $ ?subs
           :where
           [(fulltext $ :item/text ?subs) [[?i]]]]
         (d/db conn)
         subs))

  ;; XXX: No check if params are valid (str, <1k chars).
  ;; oh, and we don't send in as item/title and item/text
  (defn add-item [params]
    (let [title (:item/title params)
          text  (:item/text params)]
      (d/transact conn
                  [{:item/title title
                    :item/text text
                    :item/id  (gen-uuid)
                    :db/id (d/tempid :db.part/user)}])
      (generate-response {:status :ok :message "Added user"})))

  ;; should this return user? or print it?
  ;;(generate-response {:status :ok :message (add-item params)})) ;; weird one


  (defn log [msg arg]
    (println msg ": " arg)
    arg)

  ;; OLD
  ;; database
  ;; when we get an incoming request, shoot off whole load-data
  ;; when we get a post req, save-data

  (def db (atom {:items []
                 :word-map {}}))

  (def counter (atom 0)) ;; when using count anyway

  ;; TODO: periodically do this
  (defn save-data []
    (spit "data" (prn-str @db)))

  (defn load-data []
    (reset! db (read-string (slurp "data"))))

  (deftemplate page
    (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

  (defn wrap-errors [fn params]
    (try+ (fn params)
          (catch [:status 400] {:keys [message]}
            (str {:status "error" :message message}))
          (catch [:status 401] {:keys [message]}
            (str {:status "error" :message message}))
          (catch [:status 404] {:keys [message]}
            (str {:status "error" :message message}))
          (catch [:status 500] {:keys [message]}
            (str {:status "error" :message message}))))

  (defn split-words [s] (split s #"\s+"))

  ;; Let's just start with all the words. Leaving freqs in.
  (defn make-word-map [[id str]]
    (zipmap (map first
                 (frequencies (split-words str)))
            (repeat [id])))

  ;; TODO: Atm return whole db, just items or just item?
  ;; Whole items seems ok for now. Expensive eventually ;)
  (defn add-note [params]
    (swap! counter inc)
    (swap! db update-in [:items] conj [@counter (:text params)])
    (swap! db update-in [:word-map]
           #(merge-with union % (make-word-map (last (:items @db)))))
    (log "saving data" (save-data))
    (log "add-note"
         (str {:status "ok" :message @db})))

  ;; TODO: use bidi
  ;; TODO: Clean up with just a fn for each
  (defroutes routes
    (resources "/")
    (resources "/react" {:root "react"})

    ;;(GET "/hello" req (str "hi req get " req))
    ;;  (POST "/hello" req (str "hipostraw " req))
    (GET "/hello" {params :params} (generate-response {:status :ok :message params}))
    (POST "/hello" {params :params} (generate-response {:status :ok :message params}))

    
    
    #_(POST "/hello" {params :params} (str "hipost " params))
    (GET "/note/" {params :params}
         (do (println "GET NOTE ID: " (:id params))
             (generate-response {:status :ok :message (get-item-by-id (:id params))})))

    (GET "/search/" {params :params}
         (do (println "SEARCH TEXT: " (:text params))
             (generate-response {:status :ok :message (search-title (:text params))})))

    ;; TODO: also messy with content-type etc. just do json-api?
    ;; now we get some silly text/plain, let's do xhrio I think
    (POST "/note/" {params :params} (add-item params))
    
    ;; server error 500? and text/plain? something with println and do
    #_(POST "/note/" {params :params}
            (do (println ("POST NOTE: " (str params)))
                (generate-response {:status :ok :message (add-item params)})))

    (GET "/*" req (page)))

  ;; order must be wrong somehow
  (defn api [routes]
    (-> routes
        wrap-edn-params
        ;; these three are from api
        wrap-keyword-params 
        wrap-nested-params      
        wrap-params


        ;;x      wrap-edn-params

        
        ;;wrap-transit-params
        ;; wrap-transit-response ;; wtf
        ;;      wrap-keyword-params
        ;;      wrap-nested-params
        ;;      wrap-params
        ;;      wrap-edn-params
        ))

  (def http-handler
    (if is-dev?
      (reload/wrap-reload (api #'routes))
      (api routes)))

  (defn run [& [port]]
    (defonce ^:private server
      (do
        (if is-dev? (start-figwheel))
        (let [port (Integer. (or port (env :port) 10555))]
          (print "Starting web server on port" port ".\n")
          (run-jetty http-handler {:port port
                                   :join? false}))))
    server)

  (defn -main [& [port]]
    (run port))


  ) ;; end comment
