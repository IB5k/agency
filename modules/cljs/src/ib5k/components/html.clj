(ns ib5k.components.html
  (:require
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]
   [hiccup.page :refer [html5 include-css include-js]]
   [plumbing.core :refer :all :exclude [update]]
   [schema.core :as s]
   [ib5k.components.cljs-render :refer (CljsRenderer render)]))

(defnk head
  [title
   {stylesheets []}]
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:http-equiv "X-UA-Compatible"
           :content "IE=edge, chrome=1"}]
   [:meta {:name "viewport"
           :content (str "width=device-width, "
                         "height=device-height, "
                         "initial-scale=1.0, "
                         "maximum-scale=1.0, "
                         "user-scalable=no")}]
   [:title title]
   (for [url stylesheets]
     (include-css url))])

(defnk body
  [{cljs-renderer nil}
   {js []}
   state
   environment
   main]
  (let [main (if (= :development environment)
               "dev"
               main)]
    [:body
     [:div#root (if cljs-renderer
                  (render cljs-renderer state)
                  "")]
     [:script#config
      {:type "application/edn"}
      (pr-str (assoc state :environment environment))]
     (apply include-js js)
     (include-js "/public/js/out/goog/base.js")
     (include-js "/public/js/main.js")
     [:script {:type "text/javascript"}
      (str "goog.require(\"" main "\")")]
     [:script {:type "text/javascript"} (str main ".main('config')")]]))

(defnk page
  [{lang "en"}
   :as opts]
  (html5
   {:lang lang}
   (head opts)
   (body opts)))

(defprotocol HtmlTemplate
  (html [this] [this state]))

(def new-cljs-app-schema
  {:title s/Str
   :stylesheets [s/Str]
   :js [s/Str]
   :environment (s/enum :development :production)
   :main s/Str})

(defrecord CljsApp
    [title js stylesheets environment cljs-renderer main]
  HtmlTemplate
  (html [this] (html this {}))
  (html [this state]
    (page {:title title
           :stylesheets stylesheets
           :js js
           :environment environment
           :state state
           :main main
           :cljs-renderer cljs-renderer})))

(defn new-cljs-app
  [& {:as opts}]
  (->> opts
       (merge {:stylesheets []
               :js []})
       (s/validate new-cljs-app-schema)
       map->CljsApp
       (<- (component/using [:cljs-renderer]))))
