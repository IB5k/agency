(ns ib5k.utils.async
  #+cljs
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   #+clj [clojure.core.async :as async :refer (go go-loop <! >! put! chan dropping-buffer alts!)]
   #+cljs [cljs.core.async :as async :refer (<! >! put! chan dropping-buffer alts!)]))

#+clj
(defmacro <!!? [c]
  `(let [v# (<!! ~c)]
     (if (instance? Throwable v#)
       (throw v#)
       v#)))

#+clj
(defmacro go-try [& body]
  `(go (try ~@body
            (catch Throwable ex#
              ex#))))

(defn control-loop
  "calls f on each value read from read-chan
  returns a control channel that can :play, :pause, and :kill the read loop"
  ([f read-chan] (control-loop f read-chan (chan)))
  ([f read-chan control]
     (go-loop [action :play]
       (let [[v c] (alts! (condp = action
                            :play [read-chan control]
                            :pause [control]))]
         (when-not (nil? v)
           (condp = c
             control (when-not (= :kill v)
                       (recur v))
             read-chan (do
                         (f v)
                         (recur action))))))
     control))
