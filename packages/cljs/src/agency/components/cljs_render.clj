(ns agency.components.cljs-render
  (:require
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]
   [schema.core :as s]
   [plumbing.core :refer :all :exclude [update]])
  (:import
   [javax.script
    Invocable
    ScriptEngineManager]))

(defprotocol CljsRenderer
  (render [this] [this state]))

(defrecord BrowserRenderer []
  CljsRenderer
  (render [this] (render this {}))
  (render [this state] ""))

(def new-browser-cljs-renderer-schema
  {})

(defn new-browser-cljs-renderer
  [& {:as opts}]
  (->> opts
       (merge {})
       (s/validate new-browser-cljs-renderer-schema)
       map->BrowserRenderer))

(defn fn-pool
  "Returns a pool of n fns"
  [f n]
  (let [pool (ref (repeatedly n f))]
    (fn [& args]
      (let [f* (or
                (dosync
                 (let [f (first @pool)]
                    (alter pool rest)
                   f))
                f)
            out (apply f args)]
        (dosync (alter pool conj f*))
        out))))

(defn nashorn-env []
  (doto (.getEngineByName (ScriptEngineManager.) "nashorn")
    ;; React requires either "window" or "global" to be defined.
    (.eval "var global = this;")
    (.eval (-> "polyfills/console.js"
               io/resource
               io/reader))))

(defn bootstrap-goog [nashorn-env goog-path]
  (doto nashorn-env
    ;; parse dependencies
    (.eval (-> (str goog-path "/base.js")
               io/reader))
    (.eval (-> (str goog-path "/deps.js")
               io/reader))))

(defn bootstrap-build [nashorn-env build]
  (doto nashorn-env
    ;; parse the compiled js file
    (.eval (-> build
               io/reader))))

(defn bootstrap-dev [nashorn-env goog-path]
  (doto nashorn-env
    ;; set goog to import javascript using nashorn-env load(path)
    (.eval (str "goog.global.CLOSURE_IMPORT_SCRIPT = function(path) {
                                                   load(\"resources/" goog-path "/\" + path);
                                                   return true;
                                                  };"))
    ;; loop through dependencies and require to trigger injections
    (.eval "for(var namespace in goog.dependencies_.nameToPath)
                                            goog.require(namespace);")))

(defn nashorn-invokable [nashorn-env namespace method]
  (fn [edn]
    (.invokeMethod
     ^Invocable nashorn-env
     namespace
     method
     (-> edn
         pr-str
         list
         object-array))))

(defnk nashorn-renderer
  [{environment :debug}
   target-path
   main]
  (let [goog-path (str target-path "-debug/src/goog")
        env (cond-> (nashorn-env)
                    (= environment :debug) (-> (bootstrap-goog goog-path)
                                               (bootstrap-dev goog-path)
                                               (bootstrap-build (str target-path "-debug/main.js")))
                    (= environment :production) (bootstrap-build (str target-path "/main.js")))
        ;; eval the render namespace
        main-ns (.eval env main)
        ;; pull the invocable render-to-string method out of view
        render-to-string (nashorn-invokable env main-ns "render-to-string")]
    (fn render [state-edn]
      (render-to-string state-edn))))

(defrecord NashornRenderer [environment target-path main render-fn]
  component/Lifecycle
  (start [this]
    (assoc this
      :render-fn (nashorn-renderer {:environment environment
                                    :target-path target-path
                                    :main main})))
  (stop [this]
    (dissoc this :config :render-fn))
  CljsRenderer
  (render [this] (render this {}))
  (render [this state]
    (render-fn state)))

(def new-nashorn-cljs-renderer-schema
  {:target-path s/Str
   :main s/Str
   :environment (s/enum :development :production)})

(defn new-nashorn-cljs-renderer
  [& {:as opts}]
  (->> opts
       (merge {})
       (s/validate new-nashorn-cljs-renderer-schema)
       map->NashornRenderer))
