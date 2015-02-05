(ns agency.components.chsk
  #+clj
  (:require
   [clojure.core.async :as async :refer (go go-loop <! >! put! chan dropping-buffer alts!)]
   [clojure.test :refer (function?)]
   [com.stuartsierra.component :as component]
   [modular.ring :refer (WebRequestHandler)]
   [modular.bidi :refer (WebService as-request-handler)]
   [plumbing.core :refer :all :exclude [update]]
   [schema.core :as s]
   [taoensso.sente :as sente])
  #+cljs
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  #+cljs
  (:require
   [cljs.core.async :as async :refer (<! >! put! chan dropping-buffer close!)]
   [plumbing.core :refer-macros [defnk fnk <-]]
   [schema.core :as s]
   [taoensso.sente  :as sente]
   [quile.component :as component]
   [agency.utils.async :refer [control-loop]]))

#+clj
(defn uuid [] (str (java.util.UUID/randomUUID)))

(defprotocol ChannelSocketHandler
  (handler [_  chsk]))

#+clj
(defrecord ChannelSockets
    [user-id-fn ring-ajax-post ring-ajax-get-or-ws-handshake ch-chsk chsk-send! connected-uids router]
  component/Lifecycle
  (start [this]
    (let [chsk (sente/make-channel-socket! {:user-id-fn user-id-fn})
          {:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]} chsk
          chsk-handlers (->> (vals this)
                             (filter #(satisfies? ChannelSocketHandler %))
                             (map #(handler % chsk)))
          chsk-handler (fn [& args]
                         (doseq [handler chsk-handlers]
                           (apply handler args)))]
      (assoc this
        :ring-ajax-post ajax-post-fn
        :ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn
        :ch-chsk ch-recv
        :chsk-send! send-fn
        :connected-uids connected-uids
        :router (atom (sente/start-chsk-router! ch-recv chsk-handler)))))
  (stop [this]
    (if-let [stop-f (some-> router deref)]
      (assoc this :router (stop-f))
      this)))

#+cljs
(defrecord ChannelSockets
    [context type chsk ch-chsk chsk-send! chsk-state ip port]
  component/Lifecycle
  (start [this]
    (let [{:keys [chsk ch-recv send-fn state]}
          (sente/make-channel-socket! context
                                      (cond-> {:type type}
                                              ip (assoc :chsk-url-fn
                                                   (fn [path {:as window-location :keys [pathname]} websocket?]
                                                     (str "ws://" ip ":" port (or path pathname))))))
          send-queue (chan)
          control-chan (control-loop send-fn send-queue)
          chsk-handlers (->> (vals this)
                             (filter #(satisfies? ChannelSocketHandler %))
                             (map #(handler % chsk)))
          chsk-handler (fn [& args]
                         (doseq [handler chsk-handlers]
                           (apply handler args)))]
      ;; don't send until chsk is open
      (put! control-chan :pause)
      (add-watch state :conn
                 (fn [_key _ref old-value {:keys [open? destroyed?] :as v}]
                   (put! control-chan (if-not destroyed?
                                        (if open?
                                          :play
                                          :pause)
                                        :kill))))
      (assoc this
        :chsk chsk
        :ch-chsk ch-recv
        :chsk-send! #(put! send-queue %)
        :chsk-state state
        :control-chan control-chan
        :router (atom (sente/start-chsk-router! ch-recv chsk-handler)))))
  (stop [this]
    (some-> this :control-chan close!)
    this))

#+clj
(def new-channel-sockets-schema
  {:user-id-fn (s/either (s/pred function?)
                         s/Keyword)})

#+cljs
(def new-channel-sockets-schema
  {:context s/Str
   :type (s/enum :auto :ajax :ws)
   (s/optional-key :ip) s/Str
   (s/optional-key :port) s/Int})

(defn new-channel-sockets
  [& {:as opts}]
  (->> opts
       (merge #+clj {:user-id-fn (fn [req] (uuid))}
              #+cljs {:context "/chsk"
                      :type :auto
                      :port 80})
       (s/validate new-channel-sockets-schema)
       map->ChannelSockets
       (<- (component/using []))))

#+clj
(defrecord ChskRoutes [chsk]
  WebService
  (request-handlers [this]
    (let [{:keys [ring-ajax-post ring-ajax-get-or-ws-handshake]} chsk]
      {::chsk-get  (fn [req]
                     (ring-ajax-get-or-ws-handshake req))
       ::chsk-post (fn [req]
                     (ring-ajax-post req))}))

  (routes [_] ["/" {"chsk" {:get ::chsk-get
                            :post ::chsk-post}}])

  (uri-context [_] "")

  WebRequestHandler
  (request-handler [this] (as-request-handler this)))

#+clj
(defn new-chsk-routes []
  (-> (map->ChskRoutes {})
      (component/using [:chsk])))
