(ns open-company.components
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [open-company.db.pool :as pool]
            [org.httpkit.server :as httpkit]))

(defrecord HttpKit [options handler server]
  component/Lifecycle
  (start [component]
    (let [handler (get-in component [:handler :handler] handler)
          server (httpkit/run-server handler options)]
      (timbre/info "Starting HTTPKit server")
      (assoc component :server server)))
  (stop [component]
    (when server
      (server)
      component)))

(defrecord RethinkPool [size regenerate-interval pool]
  component/Lifecycle
  (start [component]
    (timbre/info "Starting RethinkDB connection pool")
    (let [pool (pool/fixed-pool pool/init-conn pool/close-conn
                                {:size size :regenerate-interval 15})]
      (assoc component :pool pool)))
  (stop [component]
    (when pool
      (do 
        (timbre/info "Stopping RethinkDB connection pool")
        (pool/shutdown-pool! pool)
        (dissoc component :pool))
      component)))

(defrecord Handler [handler-fn]
  (start [component]
    (assoc component :handler (handler-fn component)))
  (stop [component]
    (dissoc component :handler)))

(def pool
  (map->RethinkPool {:size 5 :regenerate-interval 10}))
