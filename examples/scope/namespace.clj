(ns auth
  (:require [clojure.java.io :as io]))

(def users (atom []))

;; ******

(ns website
  (:require [[auth :refer [users]]
             [clojure.core.async :refer [<!! timeout]]]))

(<!! (timeout (rand-int 1000)))
(swap! users conj {:name "Dylan"})
;; =>
;; [{:name "Dylan"}]

;; ******

(ns api
  (:require [auth :refer [users]]))

(println @users)
;; =>
;; [{:name "Dylan"}]
