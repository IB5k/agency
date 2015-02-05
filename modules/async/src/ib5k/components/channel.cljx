(ns ib5k.components.channel
  (:require
   #+clj [com.stuartsierra.component :as component]
   #+cljs [quile.component :as component]
   #+clj [plumbing.core :refer :all :exclude [update]]
   #+cljs [plumbing.core :refer [] :refer-macros [fnk]]
   #+clj [clojure.core.async :as async :refer (chan close!)]
   #+cljs [cljs.core.async :as async :refer (chan close!)]
   [schema.core :as s]))

(extend-type #+clj  clojure.core.async.impl.channels.ManyToManyChannel
             #+cljs cljs.core.async.impl.channels.ManyToManyChannel
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    (close! this)
    this))

(defn new-channel []
  (chan))
