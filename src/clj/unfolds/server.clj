(ns unfolds.server
  (:require [clojure.java.io :as io]
            [unfolds.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [clojure.set :refer [union]]
            [clojure.string :refer [split]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [resources]]
            [slingshot.slingshot :refer [try+ throw+]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [ring.middleware.reload :as reload]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.edn :refer [wrap-edn-params]]))

;; database
;; when we get an incoming request, shoot off whole load-data
;; when we get a post req, save-data

(defn log [msg arg]
  (println msg ": " arg)
  arg)

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
  (log "add-note"
       (str {:status "ok" :message @db})))

(defroutes routes
  (resources "/")
  (resources "/react" {:root "react"})
  (POST "/note/" {params :params} (wrap-errors add-note params))
  (GET "/*" req (page)))

(defn api [routes]
  (-> routes
      wrap-keyword-params
      wrap-nested-params
      wrap-params
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
