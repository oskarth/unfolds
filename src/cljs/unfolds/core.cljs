(ns unfolds.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
	    [cljs.core.async :refer [put! chan <!]]))

(defonce app-state (atom {:text "Hello!"
                          :items ["foo" "bar"]}))

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

(defn item-view [item owner]
  (reify
    om/IRender
    (render [this]
     (dom/li nil (str item)))))

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
