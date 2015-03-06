(ns unfolds.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [secretary.core :refer [defroute]]
                   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [clojure.set :refer [union]] ;; rm
            [clojure.string :refer [split]]
            [goog.events :as events]
            [cljs.core.async :as async
             :refer (<! >! put! chan sliding-buffer timeout alts!)]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]
            [cljs.reader :as reader]
            [ajax.core :refer (GET PUT POST)]
            [secretary.core :as secretary])
  (:import goog.History))

;; From http://adambard.com/blog/a-simple-clojurescript-app/

(defn keywordify [m]
  (cond
   (map? m) (into {} (for [[k v] m] [(keyword k) (keywordify v)]))
   (coll? m) (vec (map keywordify m))
   :else m))

(defn fetch [k default]
  (let [item (.getItem js/localStorage k)]
    (if  (and item (not= item "null")) ;; "null" is true, fuck you js.
      (-> (.getItem js/localStorage k)
          (or (js-obj))
          (js/JSON.parse)
          (js->clj)
          (keywordify))
      default)))

(defn store [k obj]
  (.setItem js/localStorage k (js/JSON.stringify (clj->js obj))))

(def base-url (atom ""))

(defn set-base-url! []
  (swap! base-url (fn [_] (str (.-origin (.-location js/window))))))

(set-base-url!)

(defn debug [s x]
  (. js/console (log s (pr-str x))))

;; global extra channel for outer-Om comms
 (def comm-alt (chan (sliding-buffer 1)))

(secretary/set-config! :prefix "#")

;; How is it dispatching if URL isn't updated? b0rked.
;; (set! (.-location js/window) "/path")

(let [history (History.)]
  (events/listen history "navigate"
                 (fn [event]
                   (secretary/dispatch! (.-token event))))
  (.setEnabled history true))

(def default-init-state
  {:search ""
   :word-map {}
   :visible [] ;; XXX: Bad name, visible items.
   :hidden {:view true
            :about false
            :add true
            :search true
            :menu false}
   :current-item 0
   :items []})

(defonce app-state
  (atom (fetch "unfolds" default-init-state)))

(defn in?
  "true if seq contains elm"
  [seq elm]
  (some #(= elm %) seq))

(defn hidden [^boolean bool]
  (if bool
    #js {:display "none"}
    #js {:display "block"}))

(defn split-words [s] (split s #"\s+"))

;; TODO: word-spacing (jsut add " " after 9)
;; Doesn't work because of split-words...
(def link-re #"\[\[([0-9]+)\|([A-Za-z0-9]+)\]\]")
(defn link [href str] (dom/a #js {:href href} str))
(defn link? [s] (if (re-find link-re s) true false))
(defn get-href [s] (str (nth (re-find link-re s) 1)))
(defn get-title [s] (str (nth (re-find link-re s) 2)))

(defn str-or-link [x]
  (if (link? x)
    (link (str "/#/notes/" (get-href x)) (get-title x))
    (str " " x " ")))

(defn prepare-item [s]
  (vec (map str-or-link (split-words s))))

(defn add-item [app owner]
  (let [new-item (-> (om/get-node owner "new-item")
                          .-value)]
    (when new-item
      (put! comm-alt {:tag :add-note! :value {:text new-item}}))))

(defn search [app owner]
  (let [search (-> (om/get-node owner "search")
                   .-value)]
    (when search
      (om/transact! app :search (fn [] search))
      (om/transact! app :visible (fn [] (get (:word-map @app) search))))))

(defn handle-item-change [e owner {:keys [text]}]
  (let [value (.. e -target -value)
        count (count value)]
    (om/set-state! owner :count count)
    (if (> count 999)
      (om/set-state! owner :text text)
      (om/set-state! owner :text value))))

(defn handle-search-change [e owner {:keys [text]}]
  (let [value (.. e -target -value)]
    (om/set-state! owner :text text)))

;; TODO: Implement proper tf-idf with 1-gram and basic-english-list
#_(defn naive-key-extractor [str]
    (vec (map first
              (filter #(= (second %) 1)
                      (frequencies (split-words (:text str)))))))

;; TODO: Link to view item, just do preview
;; TODO: Change name of fn
(defn visible-item-view [item owner]
  (reify
    om/IRender
    (render [this]
      (dom/li nil
              (dom/p nil (second item) " "
                     (link (str "#/notes/" (first item)) "link"))))))

;; TODO: with links
(defn item-view [app owner]
  (reify
    om/IRender
    (render [this]
      (let [current-item  (:current-item @app-state)
             ;; XXX: -1 because of count idx
            item (second (get (:items @app-state) (- (int current-item) 1)))]
        (dom/div #js {:style (hidden (-> app :hidden :view))}
                 (apply dom/div nil
                        (prepare-item item))))))) ;; hm

;; TODO: hide-all!
;; TODO: hide!
;; what is value? takes one
;; more like view note?
(defn view-note [app value]
  ;; this should fetch
  (debug "view-note " value)
  (om/transact! app :hidden #(assoc % :about true))
  (om/transact! app :hidden #(assoc % :add true))
  (om/transact! app :hidden #(assoc % :search true))
  (om/transact! app :hidden #(assoc % :view false))
  (om/transact! app :current-item (fn [_] (:id value))))

(defn view-add [app]
  (debug "view-add" "")
  (om/transact! app :hidden #(assoc % :about true))
  (om/transact! app :hidden #(assoc % :view true))
  (om/transact! app :hidden #(assoc % :search true))
  (om/transact! app :hidden #(assoc % :add false)))

(defn view-search [app]
  (debug "search " "")
  (om/transact! app :hidden #(assoc % :about true))
  (om/transact! app :hidden #(assoc % :view true))
  (om/transact! app :hidden #(assoc % :add true))
  (om/transact! app :hidden #(assoc % :search false)))

(defn view-about [app]
  (debug "about " "")
  (om/transact! app :hidden #(assoc % :about false))
  (om/transact! app :hidden #(assoc % :view true))
  (om/transact! app :hidden #(assoc % :add true))
  (om/transact! app :hidden #(assoc % :search true)))

(defn default-err-fn [app msg]
  (debug "ERROR: " msg))

(defn add-note-ok! [app resp]
  (let [id (count (:items resp))] ;; XXX: This is the last one, not very robust.
    (om/transact! app :word-map #(:word-map resp))
    (om/transact! app :items #(:items resp))
    (set! (.-location js/window) (str "/#notes/" id))
    (secretary/dispatch! (str "#/notes/" id))))

;; TODO: Is this right? What getting back?
(defn get-note-ok! [app resp]
  (debug "RESP: " resp) ;; this right? now just POST left...
  #_(let [id (count (:items resp))] ;; XXX: This is the last one, not very robust.
    (om/transact! app :word-map #(:word-map resp))
    (om/transact! app :items #(:items resp))
    (set! (.-location js/window) (str "/#notes/" id))
    (secretary/dispatch! (str "#/notes/" id))))

;; ! if side-effect, ie only for add I think?
;; but we also already have search and add fn
;; time for namespaces
(def ops-table
  {:view-add    {:type :nav  :method view-add}
   :view-note   {:type :nav  :method view-note}
   :view-search {:type :nav  :method view-search}
   :view-about  {:type :nav  :method view-about}
   :get-note    {:type :ajax
                 :method GET
                 :uri-fn #(str @base-url "/note/")
                 :ok-fn get-note-ok!
                 :err-fn default-err-fn}
   :add-note!   {:type :ajax
                 :method POST
                 :uri-fn #(str @base-url "/note/")
                 :ok-fn add-note-ok!
                 :err-fn default-err-fn}
   })

(defn get-op [tag] (get ops-table tag))

(defn event-loop [app event-chan]
  (go
    (while true
      (let [[{:keys [tag value]} _] (alts! [comm-alt event-chan])
            {:keys [type method uri-fn ok-fn err-fn] :as ops} (get-op tag)]
        (. js/console (log "event tag: " (pr-str tag)))
        (. js/console (log "event val: " (pr-str value)))
        (condp keyword-identical? type
          :ajax
          (method (uri-fn)
                  {:params value
                   ;;:format :edn ;; trying a la controllers
                   :handler
                   (fn [body]
                     (let [{:keys [status message]} (reader/read-string body)]
                       (if (= status :ok) ;; prev "ok"
                         (ok-fn app message)
                         (err-fn app message))))
                   :timeout 20000
                   :error-handler
                   (fn [err] (prn (str "error: " err)))})
          :nav
          (if value (method app value) (method app)))))))

(defn app-view [app owner]
  ;; Can this be here?
  (store "unfolds" app) ;; too much?
  (reify
    om/IInitState
    (init-state [_]
      {:chans {:event-chan (chan (sliding-buffer 1))}})
    om/IWillMount
    (will-mount [_]
      (let [event-chan (om/get-state owner [:chans :event-chan])]
        (event-loop app event-chan)))
    om/IRenderState
    (render-state [this {:keys [chan] :as state}]
      (dom/div nil
               ;; TODO: Make these into four separate components
               
               ;; Menu bar
               (dom/div #js {:style (hidden (-> app :hidden :menu))}
                        (dom/p nil (dom/h1 nil
                                           (dom/a #js {:href "" :className "none"
                                                       :text-decoration "none"} "Unfolds"))
                               (dom/span nil "  ")
                               (dom/a #js {:href "#/add"} "add") " "
                               (dom/a #js {:href "#/search"} "search") " "
                               #_(dom/a #js {:href "#/about"} "about") " "))

               (dom/div #js {:style (hidden (-> app :hidden :about))}
                        (dom/p nil "Welcome. Try adding a note. Links
                        are written as [[ID|TITLE]] where ID is the id
                        of that note, and TITLE is a one word."))

               ;; Search
               (dom/div #js {:style (hidden (-> app :hidden :search))}
                        (dom/p nil
                               (str "Search: ")
                               (dom/input #js {:value (:search state)
                                               :type "text"
                                               :ref "search"
                                               :onChange
                                               #(handle-search-change % owner state)})
                               (dom/button #js {:onClick #(search app owner)} "Search"))
                        (apply dom/ul nil
                               (om/build-all visible-item-view
                                             (filter (fn [[i _]] (in? (:visible app) i))
                                                     (:items app))))
                        (dom/p nil
                               (dom/h2 nil "Indexed terms ")
                               (dom/br nil "")
                               (apply str (interpose ", " (map first (:word-map @app-state))))
                               ".")
                        )

               ;; View
               (om/build item-view app)

               ;; Add
               (dom/div #js {:style (hidden (-> app :hidden :add))}
                        (dom/p nil (str "" (:count state) "/1000"))
                        (dom/textarea #js
                                      {:value (:text state)
                                       :ref "new-item"
                                       :rows "12" :cols "80"
                                       :onChange
                                       #(handle-item-change % owner state)})
                        (dom/button #js {:onClick #(add-item app owner)}
                                    "Add item"))
               ))))

(defroute "/" {:as params}
  (put! comm-alt {:tag :view-about :value {}}))

(defroute "/notes/:id" {:as params}
  (put! comm-alt {:tag :get-note :value {:id (:id params)}}))

(defroute "/add" {} (put! comm-alt {:tag :view-add :value {}}))

(defroute "/search" {} (put! comm-alt {:tag :view-search :value {}}))

(defn main []
  (om/root app-view
           app-state
           {:target (. js/document (getElementById "app"))}))
