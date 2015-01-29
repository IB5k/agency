(task-options!
 pom {:url "https://github.com/pleasetrythisathome/agency"
      :scm {:url "https://github.com/pleasetrythisathome/agency"}})

(set-env!
 :source-paths #{}
 :dependencies (->> '[[adzerk/bootlaces "0.1.9"]
                      [adzerk/boot-cljs "0.0-2727-0"]
                      [adzerk/boot-cljs-repl "0.1.8"]
                      [deraen/boot-cljx "0.2.1"]
                      [jeluard/boot-notify "0.1.1"]
                      [prismatic/plumbing "0.3.7"]]
                    (mapv #(conj % :scope "test"))))

(require
 '[adzerk.bootlaces      :refer :all]
 '[adzerk.boot-cljs      :refer :all]
 '[adzerk.boot-cljs-repl :refer :all]
 '[deraen.boot-cljx      :refer :all]
 '[jeluard.boot-notify   :refer :all]
 '[plumbing.core         :refer :all :exclude [update]])

(task-options!
 pom {:license {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}
      :url "https://github.com/https://github.com/IB5k/agency"
      :scm {:url "https://github.com/IB5k/agency"}}
 cljs {:source-map true})

(def deps '{:auth          [[com.cemerick/friend "0.2.1" :exclusions [org.clojure/core.cache]]]
            :async         [[org.clojure/core.async "0.1.346.0-17112a-alpha"]]
            :bidi          [[juxt.modular/bidi "0.5.4"]]
            :clojure       [[org.clojure/clojure "1.7.0-alpha5"]]
            :clojurescript [[org.clojure/clojurescript "0.0-2740"]]
            :component
            {:clj          [[com.stuartsierra/component "0.2.2"]]
             :cljs         [[quile/component-cljs "0.2.2"]]}
            :datascript    [[datascript "0.8.1"]]
            :datomic       [[com.datomic/datomic-pro "0.9.5078"]
                            [juxt.modular/datomic "0.2.1" :exclusions [com.datomic/datomic-free]]
                            [io.rkn/conformity "0.3.3" :exclusions [com.datomic/datomic-free]]]
            :dev
            {:clj
             {:tracing     [[spellhouse/clairvoyant "0.0-48-gf5e59d3"]]
              :console     [[shodan "0.4.1"]]}
             :cljs         [[prone "0.6.1"]
                            [org.clojure/tools.namespace "0.2.7"]]}
            :garden        [[garden "1.2.5"]
                            [trowel "0.1.0-SNAPSHOT"]]
            :http-requests
            {:clj          [[clj-http "1.0.1"]]
             :cljs         [[cljs-http "0.1.20"]]}
            :logging       [[com.taoensso/timbre "3.3.1"]
                            [org.clojure/tools.logging "0.2.6"]]
            :modular       [[juxt.modular/maker "0.5.0"]
                            [juxt.modular/wire-up "0.5.0"]]
            :om            [[om "0.8.0"]
                            [prismatic/om-tools "0.3.10" :exclusions [org.clojure/clojure]]
                            [sablono "0.3.1" :exclusions [com.facebook/react]]]
            :server        [[ring "1.3.2"]
                            [ring/ring-defaults "0.1.2"]
                            [juxt.modular/http-kit "0.5.1"]]
            :rum           [[rum "0.2.1"]]
            :reader        [[org.clojure/tools.reader "0.8.9"]]
            :sente         [[com.taoensso/encore "1.16.2"]
                            [com.taoensso.forks/ring-anti-forgery "0.3.1"]
                            [com.taoensso/sente "1.2.0"]]
            :template      [[juxt.modular/template "0.6.2"]
                            [juxt.modular/web-template "0.5.2"]]
            :time
            {:clj          [[clj-time "0.8.0"]]
             :cljs         [[com.andrewmcveigh/cljs-time "0.2.4"]]}
            :utils         [[me.raynes/fs "1.4.6"]
                            [prismatic/plumbing "0.3.7"]
                            [prismatic/schema "0.3.4"]]})

(defn make-korks [korks]
  (cond-> korks
          (keyword? korks) vector))

(defn flatten-vals
  "takes a hashmap and recursively returns a flattened list of all the values"
  [coll]
  (if ((every-pred coll? sequential?) coll)
    coll
    (mapcat flatten-vals (vals coll))))

(defn add-deps [& korks]
  (->> korks
       (mapv (comp (partial get-in deps) make-korks))
       (mapcat flatten-vals)
       (into [])
       (partial (comp vec concat))))

(defn set-package! [{:keys [project version description] :as package}]
  (task-options!
   pom (select-keys [:project :version] package))
  (bootlaces! version))
(deftask dev
  "watch and compile cljx, cljs, with cljs repl"
  []
  (comp
   (watch)
   (notify)
   (cljx)
   (cljs-repl)
   (cljs :optimizations :none
         :pretty-print true)))
