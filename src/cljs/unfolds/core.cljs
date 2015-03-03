(ns unfolds.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [secretary.core :refer [defroute]]
                   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [clojure.set :refer [union]]
            [clojure.string :refer [split]]
            [goog.events :as events]
            [cljs.core.async :as async
             :refer (<! >! put! chan sliding-buffer timeout alts!)]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]
            [secretary.core :as secretary])
  (:import goog.History))

;; Link format: [[foo|blitz]]

;; global extra channel for outer-Om comms
 (def comm-alt (chan (sliding-buffer 1)))

(secretary/set-config! :prefix "#")

(let [history (History.)]
  (events/listen history "navigate"
                 (fn [event]
                   (secretary/dispatch! (.-token event))))
  (.setEnabled history true))

(defonce app-state (atom {:search ""
                          :word-map {"Ogden" [0]
                                     "Foo" [1]} ;; populate this, oh
                          :visible [] ;; XXX: Bad name, visible items.
                          :hidden {:view false
                                   :add true
                                   :search true
                                   :menu false}
                          :current-item -1
                          :items [[0 "Welcome to Unfolds. Every entry is limited to 1000 characters. Links are created by writing [[ID|mylink]] where ID is a numeric string, and mylink is a (one) descriptive word."
                                   1 "Basic English is an English-based controlled language created by linguist [[0|and philosopher]] Charles Kay [[1|Ogden]] as an international auxiliary language, and as an aid for teaching English as a second language. Basic English is, in essence, a simplified subset of [[1|regular English]]. It was presented in Ogden's book Basic English: A General Introduction with Rules and Grammar (1930).

Ogden's Basic, and the concept of a simplified English, gained its greatest [[2|publicity]] just after the Allied victory in World War II as a means for world peace. Although Basic English was not built into a program, similar simplifications have been devised for various international uses. Ogden's associate I. A. Richards promoted its use in schools in China. More recently, it has influenced the creation of Voice of America's Special English for news broadcasting, and Simplified English, another English-based controlled language designed to write technical manuals."]
                                  [2 "Foobar [[asdad|krieg]] hello. This is another link [[0|zero]]."]
                                  [3 "Hello there"]]}))

(def id-atom (atom -1))
(swap! id-atom inc) ;; first item, 0
(swap! id-atom inc) ;; second item, 1
(swap! id-atom inc) ;; third item, 2

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

;; Let's just start with all the words. Leaving freqs in.
(defn make-word-map [[id str]]
  (zipmap (map first
               (frequencies (split-words str)))
          (repeat [id])))

(defn prepare-item [s]
  (vec (map str-or-link (split-words s))))

(defn add-item [app owner]
  (let [new-item-text (-> (om/get-node owner "new-item")
                          .-value)
        new-item [(swap! id-atom inc) new-item-text]]
    (when new-item
      (om/transact! app :word-map #(merge-with union % (make-word-map new-item)))
      (om/transact! app :items #(conj % new-item))
      ;; TODO: transact to server
      ;; XXX: Why doesn't it update the url field? Manually?
      (secretary/dispatch! (str "#/notes/" (first new-item)))
      #_(put! comm-alt {:tag :notes
                        :value {:id (first new-item)}}))))
;; show item

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
            item (second (get (:items @app-state) (int current-item)))]
        (dom/div #js {:style (hidden (-> app :hidden :view))}
                 (apply dom/div nil
                        (prepare-item item)))))))

(defn event-loop [app event-chan]
  (go
    (while true
      (let [[{:keys [tag value]} _] (alts! [comm-alt event-chan])]
        (. js/console (log "tag: " (pr-str tag)))
        (. js/console (log "value: " (pr-str value)))

        ;; when tag is add or search, do that
        ;; TODO: Generalize.
        (condp keyword-identical? tag
          ;; TODO: hide-all function
          :notes ;; more like view?
          (do (om/transact! app :hidden #(assoc % :add true))
              (om/transact! app :hidden #(assoc % :search true))
              (om/transact! app :hidden #(assoc % :view false))
              (om/transact! app :current-item (fn [_] (:id value))))
          :add
          (do (om/transact! app :hidden #(assoc % :view true))
              (om/transact! app :hidden #(assoc % :search true))
              (om/transact! app :hidden #(assoc % :add false)))
          :search
          (do (om/transact! app :hidden #(assoc % :view true))
              (om/transact! app :hidden #(assoc % :add true))
              (om/transact! app :hidden #(assoc % :search false)))
          )))))


(defn app-view [app owner]
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
                        (dom/p nil (dom/h1 nil "Unfolds")
                               (dom/span nil "  ")
                               (dom/a #js {:href "#/add"} "add") " "
                               (dom/a #js {:href "#/search"} "search") " "
                               (dom/a #js {:href "#/about"} "about") " "))

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
                        (dom/p nil "Indexed words: "
                               (str (map first (:word-map @app-state))))
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
                                       :onChange #(handle-item-change % owner state)})
                        (dom/button #js {:onClick #(add-item app owner)} "Add item"))
               ))))

(defroute "/notes/:id" {:as params}
  (put! comm-alt {:tag :notes
                  :value {:id (:id params)}}))

(defroute "/add" {}
  (put! comm-alt {:tag :add :value {}}))

(defroute "/search" {}
  (put! comm-alt {:tag :search :value {}}))

(defroute "/about" {}
  (put! comm-alt {:tag :notes :value {:id 0}}))

(defn main []
  (om/root app-view
           app-state
           {:target (. js/document (getElementById "app"))}))
