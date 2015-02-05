(ns ib5k.components.sync
  #+cljs
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   #+clj [com.stuartsierra.component :as component]
   #+cljs [quile.component :as component]
   #+clj [plumbing.core :refer :all :exclude [update]]
   #+cljs [plumbing.core :refer [] :refer-macros [fnk]]
   #+cljs [cljs.core.async :as async :refer (<! put! sub chan)]
   [schema.core :as s]
   [ib5k.components.chsk :refer (ChannelSocketHandler)]))

#+clj
(def new-chsk-sync-reference-schema
  {:key s/Keyword
   :filter-uids (s/make-fn-schema [s/Str] [[{(s/enum :ws :ajax :any) #{s/Str}}]])
   :reference (s/pred (partial satisfies? IWatchable))
   :create-msg (s/make-fn-schema [s/Any] [[s/Any]])})

#+clj
(defrecord ChskSyncReference [chsk key reference filter-uids create-msg]
  component/Lifecycle
  (start [this]
    (let [{:keys [connected-uids chsk-send!]} chsk
          send-value! (fn [value]
                        (let [msg (create-msg value)]
                          (doseq [uid (filter-uids @connected-uids)]
                            (chsk-send! uid msg))))]
      (add-watch reference key (fn [_key _ref old-value new-value]
                                 (send-value! new-value)))
      (add-watch connected-uids key (fn [_key _ref old-value new-value]
                                      (send-value! @reference)))
      (assoc this
        :reference reference)))
  (stop [this]
    (let [{:keys [connected-uids]} chsk]
      (some-> this :reference (remove-watch key))
      (some-> connected-uids (remove-watch key))
      this)))

#+clj
(defn new-chsk-sync-reference
  [& {:as opts}]
  (->> opts
       (merge {:filter-uids :any})
       (s/validate new-chsk-sync-reference-schema)
       (map->ChskSyncReference)
       (<- (component/using [:chsk :reference]))))
