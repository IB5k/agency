(ns ib5k.components.om
  (:require
   [cljs.core.async :as async :refer [put! chan pub]]
   [goog.dom :as gdom]
   [om.core :as om :include-macros true]
   [om.dom :as dom]
   [om-tools.core :refer-macros [defcomponentk]]
   [sablono.core :as html :refer-macros [html]]
   [plumbing.core :refer-macros [defnk fnk <-]]
   [schema.core :as s]
   [quile.component :as component]))

(defrecord OmRoot [target view state shared-keys tx-notify-chan tx-pub-chan]
  component/Lifecycle
  (start [this]
    (let [shared (select-keys this shared-keys)]
      (om/root (fn [data owner]
                 (reify
                   om/IDisplayName
                   (display-name [_] "root")
                   om/IRender
                   (render [_]
                     (view data))))
               state
               {:target target
                :shared (merge shared
                               {:tx-pub-chan tx-pub-chan})
                :tx-listen (fn [tx-data root-cursor]
                             (put! tx-notify-chan tx-data))})
      this))
  (stop [this]
    (om/detach-root target)
    this))

(def new-om-root-schema
  {:target s/Str
   :state {s/Any s/Any}
   :view (s/make-fn-schema s/Any s/Any)
   :shared-keys [s/Keyword]})

(defn new-om-root
  [& {:as opts}]
  (-> opts
      (->> (merge {:shared-keys []})
           (s/validate new-om-root-schema))
      (update :target gdom/getElement)
      (map->OmRoot)
      (component/using [:tx-notify-chan :tx-pub-chan])))
