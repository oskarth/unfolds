(ns unfolds.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]))

(defonce app-state (atom {:text "Hello!"
                          :items ["foo"
                                  "Foobar [[foo|blitz]] lolophone hoptigoff."]}))

(defn add-item [app owner]
  (let [new-item (-> (om/get-node owner "new-item")
                     .-value)]
    (when new-item
      (om/transact! app :items #(conj % new-item)))))

(defn handle-change [e owner {:keys [text]}]
  (let [value (.. e -target -value)
        count (count value)]
    (om/set-state! owner :count count)
    (if (> count 999)
      (om/set-state! owner :text text)
      (om/set-state! owner :text value))))

(def link-re #"\[\[([a-z]+)\|([a-z]+)\]\]")
(defn link [href str] (dom/a #js {:href href} str))
(defn link? [s] (if (re-find link-re s) true false))
(defn get-href [s] (str (nth (re-find link-re s) 1)))
(defn get-title [s] (str (nth (re-find link-re s) 2)))
(defn split-words [s] (clojure.string/split s #"\s+"))

(defn str-or-link [x]
  (if (link? x)
    (link (get-href x) (get-title x))
    (str " " x " ")))

(defn prepare-item [s]
  (vec (map str-or-link (split-words s))))

(defn item-view [item owner]
  (reify
    om/IRender
    (render [this]
      (dom/li nil
              (apply dom/div nil
                     (prepare-item item))))))

(defn app-view [app owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (dom/div nil
               (dom/h1 nil (str (:text app) " " (:count state)))
               (dom/textarea #js
                              {:value (:text state)
                               :ref "new-item"
                               :rows "12" :cols "80"
                               :onChange #(handle-change % owner state)})
               (dom/button #js
                            {:onClick #(add-item app owner)} "Add item")
               (apply dom/ul nil
                      (om/build-all item-view (:items app)))))))

(defn main []
  (om/root app-view
           app-state
           {:target (. js/document (getElementById "app"))}))
