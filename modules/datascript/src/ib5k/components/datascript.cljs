(ns ib5k.components.datascript
  (:require
   [datascript :as d]
   [plumbing.core :refer-macros [defnk fnk <-]]
   [schema.core :as s]
   [quile.component :as component]))

;; ========== Transactions ==========

(defprotocol IDatascriptTransactions
  (transactions [_]))

(defrecord DatascriptTransactions [txes]
  IDatascriptTransactions
  (transactions [_]
    txes))

(def new-datascript-transactions-schema
  {:txes [[{s/Keyword s/Any}]]})

(defn new-datascript-transactions [& {:as opts}]
  (-> opts
      (->> (s/validate new-datascript-transactions-schema))
      (map->DatascriptTransactions)
      (component/using [])))

;; ========== Schema ==========

(defprotocol IDatascriptSchema
  (schema [_]))

(defrecord DatascriptSchema [props]
  IDatascriptSchema
  (schema [_]
    props))

(def new-datascript-schema-schema
  {s/Keyword {s/Keyword s/Keyword}})

(defn new-datascript-schema [& {:as schema}]
  (-> schema
      (->> (s/validate new-datascript-schema-schema))
      (->DatascriptSchema)
      (component/using [])))

;; ========== DB ==========

(defrecord DatascriptDB [conn]
  component/Lifecycle
  (start [this]
    (let [schema (->> this
                      vals
                      (filter #(satisfies? IDatascriptSchema %))
                      (map schema)
                      (apply merge))
          conn (d/create-conn schema)
          txes (->> this
                    vals
                    (filter #(satisfies? IDatascriptTransactions %))
                    (mapcat transactions))]
      (doseq [tx txes]
        (d/transact! conn tx))
      (assoc this
        :conn conn)))
  (stop [this]
    this))

(def new-datascript-db-schema
  {})

(defn new-datascript-db
  [& {:as opts}]
  (->> opts
       (merge {})
       (s/validate new-datascript-db-schema)
       (map->DatascriptDB)
       (<- (component/using []))))
