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


(defn request [url {:keys [method on-success on-error headers params]
                    :or {method "GET"
                         on-success identity ;; implies what?
                         on-error identity
                         headers {}}}]
  (let [xhr (goog.net.XhrIo.)
        stringifed-params (if params
                            (.stringify js/JSON (clj->js params))
                            nil)]
    ))
