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

;; TODO: There's a bug that turns :item/id into :id when
;; it's (de)seralized into local storage. Bug in one of three below
;; functions.

(defn keywordify [m]
  (cond
   (map? m) (into {} (for [[k v] m] [(keyword k) (keywordify v)]))
   (coll? m) (vec (map keywordify m))
   :else m))

(defn fetch [k default]
  (let [item (.getItem js/localStorage k)]
    (if (and item (not= item "null"))
      (-> (.getItem js/localStorage k)
          (or (js-obj))
          (js/JSON.parse)
          (js->clj)
          (keywordify))
      default)))

(defn store [k obj]
  (.setItem js/localStorage k (js/JSON.stringify (clj->js obj))))

(def default-init-state
  (atom {:route [:view-item]
         :items []
         :saved-items []
         :current-item :none}))

(defonce app-state
  (atom (fetch "unfolds" default-init-state)))

;; =============================================================================
;; Helpers

(defn add-item [item owner event-chan]
  (let [new-text (-> (om/get-node owner "new-item-text")
                     .-value)
        new-title (-> (om/get-node owner "new-item-title")
                      .-value)]
    (when (and new-title new-text)
      (put! event-chan {:op :create
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

(defn item-view [item owner]
  (reify
    om/IRenderState
    (render-state [_ _]
      (let [event-chan (om/get-state owner [:event-chan])]
        (dom/div #js {:id "item-view"}
                 (dom/h2 nil (:item/title item))
                 (dom/br nil "")
                 (link (str "#/" (:item/id item)) (:item/id item))
                 (str " ")
                 (dom/button
                #js {:id "save-item"
                     :className "button"
                     :onClick
                     (fn [e]
                       (put! event-chan
                             {:op :save
                              ;; XXX or just id?
                              :data @item}))}
                "Save")
               (dom/br nil "")
               (dom/br nil "")
               (apply dom/div nil
                      (prepare-item (:item/text item))))))))

(defn item-add-view [item owner]
  (reify
    om/IRenderState
    (render-state [_ state]
      (let [event-chan (om/get-state owner [:event-chan])]
        (dom/div #js {:id "item-add-view"}
        (dom/div #js {:id "item-info"}
                 (dom/div #js {:className "item-name editable"}
                          (dom/p nil "If you want to link to, say, the
                          start page,
                          write [[ea72343d-89dc-4dfc-85af-25e1113b0948|Unfolds]].
                          Unfortunately the link text can only be one
                          word for now (no spaces).")
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
            
            (dom/button #js {:onClick #(add-item item owner event-chan)}
                        "Add item"))))))))

(defn handle-search-change [e owner {:keys [text]}]
  (let [value (.. e -target -value)]
    (om/set-state! owner :text text)))

(defn search [owner event-chan]
  (let [search (-> (om/get-node owner "search")
                   .-value)]
    (when (and search (not= search ""))
      (put! event-chan {:op :search :data {:subs search}}))))

(defn search-view [items owner]
  (reify
    om/IRenderState
    (render-state [_ state]
      (let [event-chan (om/get-state owner [:event-chan])]
        (dom/div #js {:id "search-view"}
                 (dom/p nil "Currently only titles are searchable. It
                 might take a few seconds before entries are
                 indexed.")
          (str "Search: ")
          (dom/input #js {:value (:search state)
                          :type "text"
                          :ref "search"
                          :onChange
                          #(handle-search-change % owner state)})
          (dom/button #js {:onClick #(search owner event-chan)} "Search")

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

(defn saved-view [items owner]
  (reify
    om/IRenderState
    (render-state [_ _]
      (let [event-chan (om/get-state owner [:event-chan])]
        (prn "items " items)
        (dom/div nil
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
      {:chans {:event-chan (chan (sliding-buffer 1))}})

    om/IWillMount
    (will-mount [_]
      (prn "Saved items" (:saved-items @app-state))
      (let [event-chan (om/get-state owner [:chans :event-chan])]
        ;; reset saved-items for dev debug
        ;;(om/transact! app :saved-items (fn [] []))
        ;; event loop
        (go
          (while true
            (let [{:keys [op data]} (<! event-chan)]
              (condp = op
                :view-new
                (do
                  (.setToken history "/new")
                  (om/update! app :current-item
                              {:item/title "New item"
                               :item/text ""}))

                :view-search
                (do
                  (.setToken history "/search"))

                :view-saved
                (do
                  (.setToken history "/saved"))

                :create
                (let [data (<! (util/edn-chan
                                {:method :post :url "/items"
                                 :data data}))]
                  (.setToken history (str "/" (:item/id data)))
                  (om/transact! app :current-item #(merge % data)))

                :get
                (let [item (<! (util/edn-chan {:url (str "/items/" data)}))]
                  (.setToken history (str "/" data))
                  (om/update! app :current-item item))

                :save
                (do
                  (prn "Saved " data) ;; :item/text etc
                  ;; but in saved-items  just :text etc?
                  (om/transact! app :saved-items #(conj % data))
                  (prn "SUP " (:saved-items @app-state))
                  ;; Now what do with this? +1 etc
                  )


                :search
                (let [data (<! (util/edn-chan
                                {:url (str "/search/" (:subs data))}))]
                  ;;(.setToken history (str "/search/" (:subs data)))
                  (om/update! app [:items] data)))))))

      (defroute "/" []
        (om/update! app :route
                    [:view-item "ea72343d-89dc-4dfc-85af-25e1113b0948"]))

      (defroute "/new" []
        (om/update! app :route [:add-item]))

      (defroute "/search" []
        (om/update! app :route [:search-item]))

      (defroute "/saved" []
        (om/update! app :route [:saved-items]))


      (defroute "/:id" {id :id}
        (om/update! app :route [:view-item id])
        (put! (om/get-state owner [:chans :event-chan]) {:op :get :data id}))

      (.setEnabled history true))
    
    om/IRenderState
    (render-state [_ {:keys [chans]}]
      (let [route (:route app)
            opts {:init-state chans}
            event-chan (om/get-state owner [:chans :event-chan]) ;; XXX
            ]
        (store "unfolds" app) ;; too much?

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
                  :onClick (fn [e] (put! event-chan {:op :view-new :data nil}))}
             "Add")

            (dom/button
             #js {:id "search-item"
                  :className "button"
                  :onClick (fn [e] (put! event-chan {:op :view-search :data nil}))}
             "Search"))
          
          (dom/div nil
            (case (first (:route app))
              :add-item (om/build item-add-view (:current-item app) opts)
              :search-item (om/build search-view (:items app) opts)
              :saved-items (om/build saved-view (:saved-items app) opts)
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
