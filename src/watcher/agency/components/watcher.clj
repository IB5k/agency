(ns agency.components.watcher
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [clojure.core.async :as async :refer (chan close!)]
   [clojure-watch.core :refer [start-watch]]
   [me.raynes.fs :as fs]
   [com.stuartsierra.component :as component]
   [plumbing.core :refer :all :exclude [update]]
   [schema.core :as s]))

(defn gitignored?
  "returns true if the file would be ignored by git"
  [file]
  (->> file
       .getAbsolutePath
       (sh "git" "check-ignore")
       :out
       str/blank?
       not))

(def watcher-schema
  {:path s/Str
   :event-types [(s/enum :create :modify :delete)]
   :output-chan clojure.core.async.impl.channels.ManyToManyChannel
   :options {(s/enum :recursive) s/Bool}})

(defrecord Watcher [path event-types options output-chan]
  component/Lifecycle
  (start [this]
    (let [file (do
                 (when-not (fs/exists? path)
                   (fs/mkdirs path))
                 (fs/normalized path))
          path (.getAbsolutePath file)
          stop! (try (start-watch [{:path path
                                    :event-types event-types
                                    :bootstrap (fn [path]
                                                 (bootstrap path))
                                    :callback (fn [event filepath]
                                                (log/info event path)
                                                (set-files! path)
                                                (callback event filepath))
                                    :options options}])
                     (catch Exception e
                       (log/error "watcher exception: " (.getMessage e))
                       nil))]
      (assoc this
        :path path
        :files files
        :stop! stop!)))
  (stop [this]
    (some-> this :stop! (apply []))
    this))

(defn new-watcher
  [& {:as opts}]
  (->> opts
       (merge {:event-types [:create :modify :delete]
               :options {:recursive false}
               :bootstrap (constantly nil)
               :callback (constantly nil)})
       (s/validate watcher-schema)
       (map->Watcher)))
