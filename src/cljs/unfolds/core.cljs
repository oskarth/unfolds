(ns unfolds.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.set :refer [union]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]))

;; TODO: Move this else where, just data.
(def basic-list
  "come get give go keep let make put seem take be do have say see
  send may will about across after against among at before between by
  down from in off on over through to under up with as for of till
  than a the all any every little much no other some such that this i
  he you who and because but or if though while how when where why
  again ever far forward here near now out still then there together
  well almost enough even not only quite so very tomorrow yesterday
  north south east west please yes account act addition adjustment
  advertisement agreement air amount amusement animal answer apparatus
  approval argument art attack attempt attention attraction authority
  back balance base behaviour belief birth bit bite blood blow body
  brass bread breath brother building burn burst business butter
  canvas care cause chalk chance change cloth coal colour comfort
  committee company comparison competition condition connection
  control cook copper copy cork cotton cough country cover crack
  credit crime crush cry current curve damage danger daughter day
  death debt decision degree design desire destruction detail
  development digestion direction discovery discussion disease disgust
  distance distribution division doubt drink driving dust earth edge
  education effect end error event example exchange existence
  expansion experience expert fact fall family father fear feeling
  fiction field fight fire flame flight flower fold food force form
  friend front fruit glass gold government grain grass grip group
  growth guide harbour harmony hate hearing heat help history hole
  hope hour humour ice idea impulse increase industry ink insect
  instrument insurance interest invention iron jelly join journey
  judge jump kick kiss knowledge land language laugh law lead learning
  leather letter level lift light limit linen liquid list look loss
  love machine man manager mark market mass meal measure meat meeting
  memory metal middle milk mind mine minute mist money month morning
  mother motion mountain move music name nation need news night noise
  note number observation offer oil operation opinion order
  organization ornament owner page pain paint paper part paste payment
  peace person place plant play pleasure point poison polish porter
  position powder power price print process produce profit property
  prose protest pull punishment purpose push quality question rain
  range rate ray reaction reading reason record regret relation
  religion representative request respect rest reward rhythm rice
  river road roll room rub rule run salt sand scale science sea seat
  secretary selection self sense servant sex shade shake shame shock
  side sign silk silver sister size sky sleep slip slope smash smell
  smile smoke sneeze snow soap society son song sort sound soup space
  stage start statement steam steel step stitch stone stop story
  stretch structure substance sugar suggestion summer support surprise
  swim system talk taste tax teaching tendency test theory thing
  thought thunder time tin top touch trade transport trick trouble
  turn twist unit use value verse vessel view voice walk war wash
  waste water wave wax way weather week weight wind wine winter woman
  wood wool word work wound writing year angle ant apple arch arm army
  baby bag ball band basin basket bath bed bee bell berry bird blade
  board boat bone book boot bottle box boy brain brake branch brick
  bridge brush bucket bulb button cake camera card cart carriage cat
  chain cheese chest chin church circle clock cloud coat collar comb
  cord cow cup curtain cushion dog door drain drawer dress drop ear
  egg engine eye face farm feather finger fish flag floor fly foot
  fork fowl frame garden girl glove goat gun hair hammer hand hat head
  heart hook horn horse hospital house island jewel kettle key knee
  knife knot leaf leg library line lip lock map match monkey moon
  mouth muscle nail neck needle nerve net nose nut office orange oven
  parcel pen pencil picture pig pin pipe plane plate plough pocket pot
  potato prison pump rail rat receipt ring rod roof root sail school
  scissors screw seed sheep shelf ship shirt shoe skin skirt snake
  sock spade sponge spoon spring square stamp star station stem stick
  stocking stomach store street sun table tail thread throat thumb
  ticket toe tongue tooth town train tray tree trousers umbrella wall
  watch wheel whip whistle window wing wire worm able acid angry
  automatic beautiful black boiling bright broken brown cheap chemical
  chief clean clear common complex conscious cut deep dependent early
  elastic electric equal fat fertile first fixed flat free frequent
  full general good great grey hanging happy hard healthy high hollow
  important kind like living long male married material medical
  military natural necessary new normal open parallel past physical
  political poor possible present private probable quick quiet ready
  red regular responsible right round same second separate serious
  sharp smooth sticky stiff straight strong sudden sweet tall thick
  tight tired true violent waiting warm wet wide wise yellow young
  awake bad bent bitter blue certain cold complete cruel dark dead
  dear delicate different dirty dry false feeble female foolish future
  green ill last late left loose loud low mixed narrow old opposite
  public rough sad safe secret short shut simple slow small soft solid
  special strange thin white wrong")

;; TODO: Move this too.
;; Old with link "Foobar [[foo|blitz]] lolophone hoptigoff."

(defonce app-state (atom {:search ""
                          :word-map {"Ogden" [0]
                                     "Foo" [1]}
                          :visible []
                          :items [[0 "Basic English is an English-based controlled language created by linguist and philosopher Charles Kay Ogden as an international auxiliary language, and as an aid for teaching English as a second language. Basic English is, in essence, a simplified subset of regular English. It was presented in Ogden's book Basic English: A General Introduction with Rules and Grammar (1930).

Ogden's Basic, and the concept of a simplified English, gained its greatest publicity just after the Allied victory in World War II as a means for world peace. Although Basic English was not built into a program, similar simplifications have been devised for various international uses. Ogden's associate I. A. Richards promoted its use in schools in China. More recently, it has influenced the creation of Voice of America's Special English for news broadcasting, and Simplified English, another English-based controlled language designed to write technical manuals."]
                                  [1 "Foo"]
                                  [2 "Hello there"]]}))

;; before items looked like this
;; :items [{:id "foo" :text "bar"}] ;; dammit!

;; util fn
(defn in?
  "true if seq contains elm"
  [seq elm]
  (some #(= elm %) seq))

(def id-atom (atom -1))
(swap! id-atom inc) ;; first item, 0
(swap! id-atom inc) ;; second item, 1
(swap! id-atom inc) ;; third item, 2

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

;; TODO: when adding note, add/merge words-item to word-map

(defn handle-item-change [e owner {:keys [text]}]
  (let [value (.. e -target -value)
        count (count value)]
    (om/set-state! owner :count count)
    (if (> count 999)
      (om/set-state! owner :text text)
      (om/set-state! owner :text value))))

;; This doesn't change the actual thing
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

;;(defn prepare-item [s] (vec (map str-or-link (split-words s))))

;; td-idf, 1-gram
;; now with basic-list! (or not, does that help here - more like remove those?)
;; and go to lower-case?
;; should also have id in it

;; This is a problem: right now it measures rareness based on its own
;; note, rather than compared to the universe. You filter out lame
;; words, but also words like 'English' in an article about Basic
;; English.
#_(defn naive-key-extractor [str]
    (vec (map first
              (filter #(= (second %) 1)
                      (frequencies (split-words (:text str)))))))

#_(defn get-words [item]
  (vec (map first
            (frequencies (split-words (second item))))))


;;(merge-with union wm1 wm2)

#_(defn item-view [item owner]
  (reify
    om/IRender
    (render [this]
      (dom/li nil
              (apply dom/div nil
                     (str (:text item))
                     (str (naive-key-extractor item) ", ")
                     #_(prepare-item item))))))

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
                                    ;; into vec?
                                    (filter (fn [[i _]] (in? (:visible app) i)) (:items app))))))))

(defn main []
  (om/root app-view
           app-state
           {:target (. js/document (getElementById "app"))}))
