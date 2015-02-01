(ns unfolds.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

;;(defonce app-state (atom {:text "Hello!"}))
(def app-state (atom {:text "Cool!"}))

(defn main []
  (om/root
    (fn [app owner]
      (reify
        om/IRender
        (render [_]
          (dom/h1 nil (:text app)))))
    app-state
    {:target (. js/document (getElementById "app"))}))
