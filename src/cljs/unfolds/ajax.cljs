(ns unfolds.ajax
  (:require goog.net.XhrIo
            goog.net.EventType
            goog.events))

;; based on
;; https://github.com/swannodette/om-sync/blob/master/src/om_sync/util.cljs
;; https://github.com/hackerschool/community/blob/master/client/src/community/util/ajax.cljs

;; or this
;; (defn edn-xhr [{:keys [method url data on-complete on-error]}]
;; what is data here? more custom I guess, pr-str data
;; json makes more sense as default, even if drop-in to transit out whatever
;; web standard, clearer (un)marshalling

;; let's try comm hs

(defn error-response [e]
  {:status (.getStatus (.-target e))})

(defn request [url {:keys [method on-success on-error headers params]
                    :or {method "GET"
                         on-success identity ;; implies what?
                         on-error identity
                         headers {}}}]
  (let [xhr (goog.net.XhrIo.)
        stringified-params (if params
                            (.stringify js/JSON (clj->js params))
                            nil)
        default-headers {"Content-Type" "application/json"}]
    (goog.events/listen xhr
                        goog.net.EventType/SUCCESS
                        (fn [e] (on-success (js->clj (.getResponseJson (.-target e))))))
    (goog.events/listen xhr
                        goog.net.EventType/ERROR
                        (fn [e] (on-error (error-response e))))
    (.send xhr url method stringified-params (clj->js (merge headers default-headers)))))

;; let's try it
(defn GET [url opts] (request url (assoc opts :method "GET")))
(defn POST [url opts] (request url (assoc opts :method "POST")))

;;they do this 9n separate api ns and have some kind of make-api-fn that transforms
(def GET (partial request ajax/GET))
(def POST (partial request ajax/POST))

;; too complex for now


