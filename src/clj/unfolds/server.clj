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
            [ring.util.response :only [response]]
            [ring.middleware.transit :only [wrap-transit-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.edn :refer [wrap-edn-params]]))

(defn gen-uuid [] (str (java.util.UUID/randomUUID)))

(def conn (util/get-conn))

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn get-item-by-id [id]
  (ffirst
   (d/q '[:find (pull ?i [*])
          :in $ ?id
          :where [?i :item/id ?id]]
        (d/db conn)
        id)))

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
(defn add-item [params]
  (let [title (:item/title params)
        text  (:item/text params)]
    (d/transact conn
                [{:item/title title
                  :item/text text
                  :item/id  (gen-uuid)
                  :db/id (d/tempid :db.part/user)}])
    (generate-response {:status :ok})))

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

(defroutes routes
  (resources "/")
  (resources "/react" {:root "react"})
  (POST "/note/" {params :params} (wrap-errors add-note params))
  (GET "/*" req (page)))

(defn api [routes]
  (-> routes
      ;;wrap-keyword-params
      ;;wrap-nested-params
      ;;wrap-params
      wrap-edn-params))

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
