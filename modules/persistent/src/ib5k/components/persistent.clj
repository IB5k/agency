(ns ib5k.components.persistent
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [me.raynes.fs :as fs]
   [com.stuartsierra.component :as component]
   [plumbing.core :refer :all :exclude [update]]
   [schema.core :as s]))

(defn get-or-create-file [filename]
  (let [file (fs/normalized filename)]
    (assert (not (fs/directory? file)) "a persistent atom cannot be saved as a directory")
    (when-not (fs/exists? file)
      (fs/mkdirs (fs/parent file))
      (fs/create file))
    file))

(defprotocol IPersist
  (save! [this])
  (init! [this]))

(defn persistent? [atom]
  (:filename (meta atom)))

(extend-type clojure.lang.Atom
  component/Lifecycle
  (start [this]
    (when (persistent? this)
      (let [filename (:filename (meta this))
            file (get-or-create-file filename)
            value (try (-> file slurp edn/read-string)
                       (catch Exception e
                         (log/error "failed to load atom: " filename "error: " e)
                         nil))]
        (when-not (nil? value)
          (reset! this value)))
      (add-watch this :persist (fn [_key _ref old-value new-value]
                                 (when-not (= old-value new-value)
                                   (save! this)))))
    this)
  (stop [this]
    (when (persistent? this)
      (remove-watch this :persist))
    this)
  IPersist
  (save! [this]
    (when (persistent? this)
      (spit (:filename (meta this)) (pr-str (deref this)))))
  (init! [this]
    (when (persistent? this)
      (reset! this (:init (meta this))))))

(def persistent-atom-schema
  {:filename s/Str
   :init s/Any})

(defn new-persistent-atom
  [& {:as opts}]
  (let [{:keys [filename init]} (->> opts
                                     (merge {})
                                     (s/validate persistent-atom-schema))]
    (atom init :meta {:filename filename
                      :init init})))
