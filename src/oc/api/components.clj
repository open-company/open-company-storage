(ns oc.api.components
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [org.httpkit.server :as httpkit]
            [oc.lib.rethinkdb.pool :as pool]
            [oc.api.config :as c]))

(defrecord HttpKit [options handler server]
  component/Lifecycle
  (start [component]
    (let [handler (get-in component [:handler :handler] handler)
          server  (httpkit/run-server handler options)]
      (assoc component :server server)))
  (stop [component]
    (if-not server
      component
      (do
        (server)
        (dissoc component :server)))))

(defrecord RethinkPool [size regenerate-interval pool]
  component/Lifecycle
  (start [component]
    (timbre/info "[rehinkdb-pool] starting")
    (let [pool (pool/fixed-pool (partial pool/init-conn c/db-options) pool/close-conn
                                {:size size :regenerate-interval regenerate-interval})]
      (timbre/info "[rehinkdb-pool] started")
      (assoc component :pool pool)))
  (stop [component]
    (if pool
      (do
        (pool/shutdown-pool! pool)
        (dissoc component :pool))
      component)))

(defrecord Handler [handler-fn]
  component/Lifecycle
  (start [component]
    (timbre/info "[handler] starting")
    (assoc component :handler (handler-fn component)))
  (stop [component]
    (dissoc component :handler)))

(defn oc-system [{:keys [host port handler-fn] :as opts}]
  (component/system-map
   :db-pool (map->RethinkPool {:size c/db-pool-size :regenerate-interval 5})
   :handler (component/using
             (map->Handler {:handler-fn handler-fn})
             [:db-pool])
   :server  (component/using
             (map->HttpKit {:options {:port port}})
             [:handler])))