(ns unfolds.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.set :refer [union]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]))

;; Link format: [[foo|blitz]]

(defonce app-state (atom {:search ""
                          :word-map {"Ogden" [0]
                                     "Foo" [1]}
                          :visible []
                          :items [[0 "Basic English is an English-based controlled language created by linguist and philosopher Charles Kay Ogden as an international auxiliary language, and as an aid for teaching English as a second language. Basic English is, in essence, a simplified subset of regular English. It was presented in Ogden's book Basic English: A General Introduction with Rules and Grammar (1930).

Ogden's Basic, and the concept of a simplified English, gained its greatest publicity just after the Allied victory in World War II as a means for world peace. Although Basic English was not built into a program, similar simplifications have been devised for various international uses. Ogden's associate I. A. Richards promoted its use in schools in China. More recently, it has influenced the creation of Voice of America's Special English for news broadcasting, and Simplified English, another English-based controlled language designed to write technical manuals."]
                                  [1 "Foo"]
                                  [2 "Hello there"]]}))

(def id-atom (atom -1))
(swap! id-atom inc) ;; first item, 0
(swap! id-atom inc) ;; second item, 1
(swap! id-atom inc) ;; third item, 2

(defn in?
  "true if seq contains elm"
  [seq elm]
  (some #(= elm %) seq))

;; Let's just start with all the words. Leaving freqs in.
(defn make-word-map [[id str]]
  (zipmap (map first
               (frequencies (split-words str)))
          (repeat [id])))

(defn add-item [app owner]
  (let [new-item-text (-> (om/get-node owner "new-item")
                          .-value)
        new-item [(swap! id-atom inc) new-item-text]]
    (when new-item
      (om/transact! app :word-map #(merge-with union % (make-word-map new-item)))
      (om/transact! app :items #(conj % new-item)))))

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

;; TODO: Implement proper tf-idf with 1-gram and basic-english-list
#_(defn naive-key-extractor [str]
    (vec (map first
              (filter #(= (second %) 1)
                      (frequencies (split-words (:text str)))))))

(defn visible-item-view [item owner]
  (reify
    om/IRender
    (render [this]
      (dom/li nil
              (apply dom/div nil
                     (str (second item)))))))

(defn app-view [app owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (dom/div nil
               #_(dom/h1 nil (str (:text app) " " (:count state)))
               (dom/p nil
                      (str "Search: ")
                      (dom/input #js {:value (:search state)
                                      :type "text"
                                      :ref "search"
                                      :onChange #(handle-search-change % owner state)})
                      (dom/button #js {:onClick #(search app owner)} "Search"))
               (dom/textarea #js
                              {:value (:text state)
                               :ref "new-item"
                               :rows "12" :cols "80"
                               :onChange #(handle-item-change % owner state)})
               (dom/button #js {:onClick #(add-item app owner)} "Add item")
               (apply dom/ul nil
                      (om/build-all visible-item-view
                                    (filter (fn [[i _]] (in? (:visible app) i)) (:items app))))))))

(defn main []
  (om/root app-view
           app-state
           {:target (. js/document (getElementById "app"))}))
