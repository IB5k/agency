(ns poller
  (:require
   [clojure.core.async :as async :refer (chan alts! go-loop timeout >! close!)]
   [clojure.core.async.impl.protocols :as impl]
   [com.stuartsierra.component :as component]
   [plumbing.core :refer :all :exclude [update]]
   [schema.core :as s]))

(defn poll!
  "executes f at frequency and puts the result on output-chan, returns a stop functions"
  [frequency f output-chan]
  (let [control (chan)]
    (go-loop [tick (timeout frequency)]
      (let [[_ c] (alts! [tick control])]
        (condp identical? c
          tick (do
                 (>! output-chan (f))
                 (recur (timeout frequency)))
          nil)))
    #(close! control)))

(def poller-using-schema
  {:output-chan (s/pred (partial satisfies? impl/WritePort))})

(defrecord Poller [frequency f output-chan]
  component/Lifecycle
  (start [this]
    (-> this
        (select-keys (keys poller-using-schema))
        (s/validate poller-using-schema))
    (let [stop! (poll! frequency f output-chan)]
      (assoc this
        :stop! stop!)))
  (stop [this]
    ((:stop! this))
    this))

(def new-poller-schema
  {:frequency s/Int
   :f (s/make-fn-schema [[s/Any]] [[]])})

(def new-poller-defaults
  {:frequency 1000})

(defn new-poller
  [& {:as opts}]
  (component/using
   (->> opts
        (merge new-poller-defaults)
        (s/validate new-poller-schema)
        (map->Poller))
   (keys poller-using-schema)))

;; ===== Abstract Contructor ======

(defnk validate-ctr
  [ctr
   {ctr-defaults {}}
   ctr-schema
   {cmp-using-schema {}}]
  (fn [& {:as opts}]
    (component/using
     (->> opts
          (merge ctr-defaults)
          (s/validate ctr-schema)
          (merge {:using-schema cmp-using-schema})
          (ctr))
     (vec (or (keys cmp-using-schema) [])))))

(defn validate-cmp
  [cmp using-schema]
  (->> (select-keys cmp (keys using-schema))
       (s/validate using-schema)))

(defrecord Poller [using-schema frequency f output-chan]
  component/Lifecycle
  (start [this]
    (validate-cmp this using-schema)
    (let [stop! (poll! frequency f output-chan)]
      (assoc this
        :stop! stop!)))
  (stop [this]
    ((:stop! this))
    this))

(def new-poller
  (validate-ctr
   {:ctr map->Poller
    :ctr-defaults {:frequency 1000}
    :ctr-schema   {:frequency s/Int
                   :f (s/make-fn-schema [[s/Any]] [[]])}
    :cmp-using-schema {:output-chan (s/pred (partial satisfies? impl/WritePort))}}))
