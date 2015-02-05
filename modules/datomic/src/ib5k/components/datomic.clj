(ns ib5k.components.datomic
  (:require
   [clojure.tools.logging :as log]
   [plumbing.core :refer :all :exclude [update]]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]
   [clojure.tools.reader.reader-types :refer (indexing-push-back-reader)]
   [datomic.api :as d]
   [io.rkn.conformity :as c]
   [schema.core :as s]
   clojure.tools.reader))

(defrecord DatomicNorms [res norms]
  component/Lifecycle
  (start [this]
    (with-open [rdr (java.io.PushbackReader. (io/reader res))]
      (let [norms-map (binding [clojure.tools.reader/*data-readers*
                                {'db/id datomic.db/id-literal
                                 'db/fn datomic.function/construct
                                 'base64 datomic.codec/base-64-literal}]
                        (clojure.tools.reader/read (indexing-push-back-reader rdr)))]
        (c/ensure-conforms (:connection this) norms-map norms)))
    this)
  (stop [this] this))

(def new-datomic-norms-schema
  {:res java.net.URL
   :norms [s/Keyword]})

(defn new-datomic-norms [& {:as opts}]
  (->> opts
       (merge {:norms []})
       (s/validate new-datomic-norms-schema)
       map->DatomicNorms
       (<- (component/using [:connection]))))
