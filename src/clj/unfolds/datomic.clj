(ns unfolds.datomic
  (:require [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:import datomic.Util))

;; =============================================================================
;; Helpers

(defn gen-uuid [] (str (java.util.UUID/randomUUID)))

(defn convert-db-id [x]
  (cond
    (instance? datomic.query.EntityMap x)
    (assoc (into {} (map convert-db-id x))
      :db/id (str (:db/id x)))

    (instance? clojure.lang.MapEntry x)
    [(first x) (convert-db-id (second x))]

    (coll? x)
    (into (empty x) (map convert-db-id x))

    :else x))

;; =============================================================================
;; Queries

;; returns a bunch of ids
(defn list-items [db]
  (map
   ;; won't roundtrip to conn because of caching, why?
   #(d/entity db (first %))
   (d/q '[:find ?eid
          :where
          [?eid :item/title]]
        db)))

(defn display-items [db]
  (let [items (list-items db)]
    (map
     #(select-keys % [:db/id :item/id :item/title :item/text])
     (sort-by :item/title (map convert-db-id items)))))


;; Is there a reason we would prefer this version?
#_(defn get-item [db id-string]
    (d/touch (d/entity db (edn/read-string id-string))))

;; convert-db-id?
(defn get-item [db id-string]
  (ffirst
   (d/q '[:find (pull ?i [*])
          :in $ ?id
          :where [?i :item/id ?id]]
        db
        id-string)))

(defn search-item-title [db subs]
    (d/q '[:find (pull ?i [*])
           :in $ ?subs
           :where
           [(fulltext $ :item/title ?subs) [[?i]]]]
         db
         subs))

(defn create-item [conn data]
  (let [tempid (d/tempid :db.part/user)
        r @(d/transact conn [(assoc data :db/id tempid)])]
    (assoc data
      :db/id (str (d/resolve-tempid (:db-after r) (:tempids r) tempid))
      :item/id (gen-uuid))))

#_(defn update-item [conn data]
  @(d/transact conn [(assoc data :db/id (edn/read-string (:db/id data)))])
  true)

(defrecord DatomicDatabase [uri schema initial-data connection]
  component/Lifecycle
  (start [component]
    (d/create-database uri)
    (let [c (d/connect uri)]
      @(d/transact c schema)
      @(d/transact c initial-data)
      (assoc component :connection c)))
  (stop [component]))

(defn new-database [db-uri]
  (DatomicDatabase. db-uri
                    (first (Util/readAll
                            (io/reader (io/resource "data/schema.edn"))))
                    (first (Util/readAll
                            (io/reader (io/resource "data/initial.edn"))))
                    nil))


;; =============================================================================
;; Query testing

(comment
  (create-item (:connection (:db @unfolds.core/servlet-system))
               {:item/title "This is a test title"
                :item/text "This is a test text"})

  (get-item (d/db (:connection (:db @unfolds.core/servlet-system)))
            "ea72343d-89dc-4dfc-85af-25e1113b0948")

  (list-items (d/db (:connection (:db @unfolds.core/servlet-system))))

  (display-items (d/db (:connection (:db @unfolds.core/servlet-system))))
    
  (search-item-title (d/db (:connection (:db @unfolds.core/servlet-system)))
                     "foo")
  )
