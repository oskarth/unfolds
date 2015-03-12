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
            [goog.dom :as gdom]
            [unfolds.util :as util]
            [secretary.core :as secretary])
  (:import [goog History]
           [goog.history EventType]))

;; =============================================================================
;; Setup

(enable-console-print!)

(def history (History.))

(events/listen history EventType.NAVIGATE
  #(secretary/dispatch! (.-token %)))

(def app-state
  (atom {:route [:view-item]
         :items []
         :current-item :none}))

;; =============================================================================
;; Helpers

(defn link [href str] (dom/a #js {:href href} str))

(defn add-item [item owner {:keys [sync current-item]}]
  (let [new-text (-> (om/get-node owner "new-item-text")
                     .-value)
        new-title (-> (om/get-node owner "new-item-title")
                      .-value)]
    (when (and new-title new-text)
      (put! sync {:op :create
                  :data {:item/title new-title :item/text new-text}}))))

(defn handle-item-text-change [e owner {:keys [text]}]
  (let [value (.. e -target -value)
        count (count value)]
    (om/set-state! owner :count count)
    (if (> count 999)
      (om/set-state! owner :text text)
      (om/set-state! owner :text value))))

(defn handle-item-title-change [e owner {:keys [title]}]
  (let [value (.. e -target -value)
        count (count value)]
    (if (> count 100)
      (om/set-state! owner :title title)
      (om/set-state! owner :title value))))

;; TODO: word-spacing (jsut add " " after 9)
;; Doesn't work because of split-words...
(def link-re #"\[\[([A-Za-z0-9\-]+)\|([A-Za-z0-9]+)\]\]")
(defn link [href str] (dom/a #js {:href href} str))
(defn link? [s] (if (re-find link-re s) true false))
(defn get-href [s] (str (nth (re-find link-re s) 1)))
(defn get-title [s] (str (nth (re-find link-re s) 2)))

(defn str-or-link [x]
  (if (link? x)
    (link (str "/#/" (get-href x)) (get-title x))
    (str " " x " ")))

(defn split-words [s] (split s #"\s+"))

(defn prepare-item [s]
  (vec (map str-or-link (split-words s))))

;; =============================================================================
;; Components

(defn item-view [item owner {:keys [current-item sync]}]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:id "item-view"}
               (dom/h2 nil (:item/title item))
               (dom/br nil "")
               (link (str "#/" (:item/id item)) (:item/id item))
               (dom/p nil (:item/text item))))))

(defn item-add-view [item owner {:keys [current-item sync]}]
  (reify
    om/IRenderState
    (render-state [_ state] ;; keys chan as etc?
      (let [opts {:current-item current-item ;;; XXX
                  :sync sync}]
        (dom/div #js {:id "item-add-view"}
        (dom/div #js {:id "item-info"}
          (dom/div #js {:className "item-name editable"}
            (dom/input #js
                       {:value (:title state)
                        :ref "new-item-title"
                        :cols "80"
                        :onChange
                        #(handle-item-title-change % owner state)})
            (dom/p nil (str "" (:count state) "/1000"))
            (dom/textarea #js
                          {:value (:text state)
                           :ref "new-item-text"
                           :rows "12" :cols "80"
                           :onChange
                           #(handle-item-text-change % owner state)})
            
            (dom/button #js {:onClick #(add-item item owner opts)}
                        "Add item"))))))))

(defn handle-search-change [e owner {:keys [text]}]
  (let [value (.. e -target -value)]
    (om/set-state! owner :text text)))

(defn search [owner {:keys [sync current-item]}] ;; XXX: current-item really?
  (let [search (-> (om/get-node owner "search")
                   .-value)]
    (when search
      (put! sync {:op :search :data {:subs search}}))))

(defn search-view [items owner {:keys [current-item sync]}]
  (reify
    om/IRenderState
    (render-state [_ state]
      (let [opts {:current-item current-item ;;; XXX
                  :sync sync}]
        (dom/div #js {:id "search-view"}
          (str "Search: ")
          (dom/input #js {:value (:search state)
                          :type "text"
                          :ref "search"
                          :onChange
                          #(handle-search-change % owner state)})
          (dom/button #js {:onClick #(search owner opts)} "Search")

          (apply dom/ul #js {:id "items-list"}
                 (map (fn [item]
                        (let [i (first item)]
                          (dom/li nil
                            (dom/div nil
                              (dom/b nil (:item/title i))
                              (dom/br nil "")
                              (link (str "#/" (:item/id i))
                                    (:item/id i))
                              (dom/br nil "")
                              (dom/br nil "")))))
                      items)))))))

(defn app-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:current-item (chan)
       :sync (chan)})

    om/IWillMount
    (will-mount [_]

      (defroute "/" []
        (om/update! app :route
                    [:view-item "ea72343d-89dc-4dfc-85af-25e1113b0948"]))

      (defroute "/new" []
        (om/update! app :route [:add-item]))

      (defroute "/search" []
        (om/update! app :route [:search-item]))

      (defroute "/:id" {id :id}
        (om/update! app :route [:view-item id])
        (put! (om/get-state owner :current-item) id))

      (.setEnabled history true)

      ;; routing loop
      (go (loop []
            (let [id (<! (om/get-state owner :current-item))]
              (cond
               (= id :none)
               (do
                 (.setToken history "/")
                 (om/update! app :items
                             (vec (<! (util/edn-chan {:url "/items"})))))

               (= id :new)
               (do
                 (.setToken history "/new")
                 (om/update! app :current-item
                             {:item/title "New item"
                              :item/text ""}))

               (= id :search)
               (do
                 (.setToken history "/search"))

               :else
               (let [item (<! (util/edn-chan {:url (str "/items/" id)}))]
                 (.setToken history (str "/" id))
                 (om/update! app :current-item item))))
            (recur)))

      ;; sync loop
      ;; XXX: Don't know if sync is the best name anymore
      (go (loop []
            (let [{:keys [op data]} (<! (om/get-state owner :sync))]
              (condp = op
                :create
                (let [data (<! (util/edn-chan
                                {:method :post :url "/items"
                                 :data data}))]
                  (.setToken history (str "/" (:item/id data))) ;; XXX
                  (om/transact! app :current-item #(merge % data)))

                ;; TODO: where's get? Why not here?

                :search
                (let [data (<! (util/edn-chan
                                {:url (str "/search/" (:subs data))}))]
                  ;; XX: what if there's no hit?
                  ;;(.setToken history (str "/items")) ;; XXX
                  (om/transact! app :items (fn [_] data)) ;; search results
                  )
                
                (recur))))))
    
    om/IRenderState
    (render-state [_ {:keys [current-item sync]}]
      (let [route (:route app)
            opts {:opts {:current-item current-item
                         :sync sync}}]
        ;; menu bar
        (dom/div nil
          (dom/p nil
            (dom/h1 nil
              (dom/a #js {:href "" :className "none"
                          :text-decoration "none"} "Unfolds"))
            (dom/span nil "  ")
            (dom/button
             #js {:id "add-item"
                  :className "button"
                  :onClick (fn [e] (put! current-item :new))}
             "Add")

            (dom/button
             #js {:id "search-item"
                  :className "button"
                  :onClick (fn [e] (put! current-item :search))}
             "Search"))
          
          (dom/div nil
            (case (first (:route app))
                      ;; what need? XXX
              :add-item (om/build item-add-view (:current-item app) opts)
              ;; why bring current-item here?
              :search-item (om/build search-view (:items app)  opts)
              ;;:list-items (om/build items-view (:items app) opts)
              :view-item (om/build item-view (:current-item app) opts))))))))

(util/edn-xhr
 {:method :get
  ;;  :url "/items"
  :url "/items/ea72343d-89dc-4dfc-85af-25e1113b0948"
  :on-complete
  (fn [res]
    (swap! app-state assoc :current-item res)
    (om/root app-view app-state
             {:target (gdom/getElement "items")}))})
