(ns oc.storage.components
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [org.httpkit.server :as httpkit]
            [oc.lib.db.pool :as pool]
            [oc.lib.sqs :as sqs]
            [oc.storage.async.notification :as notification]
            [oc.storage.async.auth-notification :as auth-notification]
            [oc.storage.async.storage-notification :as storage-notification]
            [oc.storage.config :as c]))

(defrecord HttpKit [options handler]
  component/Lifecycle

  (start [component]
    (timbre/info "[http] starting...")
    (let [handler (get-in component [:handler :handler] handler)
          server  (httpkit/run-server handler options)]
      (timbre/info "[http] started")
      (assoc component :http-kit server)))

  (stop [{:keys [http-kit] :as component}]
    (if http-kit
      (do
        (timbre/info "[http] stopping...")
        (http-kit)
        (timbre/info "[http] stopped")
        (dissoc component :http-kit))
      component)))

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
        (timbre/info "[rethinkdb-pool] stopping...")
        (pool/shutdown-pool! pool)
        (timbre/info "[rethinkdb-pool] stopped")
        (dissoc component :pool))
      component)))

(defrecord AsyncConsumers []
  component/Lifecycle

  (start [component]
    (timbre/info "[async-consumers] starting")
    (notification/start) ; core.async channel consumer for notification events
    (timbre/info "[async-consumers] started")
    (assoc component :async-consumers true))

  (stop [{:keys [async-consumers] :as component}]
    (if async-consumers
      (do
        (timbre/info "[async-consumers] stopping")
        (notification/stop) ; core.async channel consumer for notification events
        (timbre/info "[async-consumers] stopped")
        (dissoc component :async-consumers))
    component)))

(defrecord Handler [handler-fn]
  component/Lifecycle
  (start [component]
    (timbre/info "[handler] started")
    (assoc component :handler (handler-fn component)))
  (stop [component]
    (timbre/info "[handler] stopped")
    (dissoc component :handler)))

(defrecord AuthNotification [auth-notification-fn]
  component/Lifecycle

  (start [component]
    (timbre/info "[auth-notifcation] starting...")
    (auth-notification/start component)
    (timbre/info "[auth-notification] started")
    (assoc component :auth-notification true))

  (stop [{:keys [auth-notification] :as component}]
    (if auth-notification
      (do
        (timbre/info "[auth-notification] stopping...")
        (auth-notification/stop)
        (timbre/info "[auth-notification] stopped")
        (dissoc component :auth-notification))
      component)))

(defrecord StorageNotification [storage-notification-fn]
  component/Lifecycle

  (start [component]
    (timbre/info "[storage-notification] starting...")
    (storage-notification/start component)
    (timbre/info "[storage-notification] started")
    (assoc component :storage-notification true))

  (stop [{:keys [auth-notification] :as component}]
    (if auth-notification
      (do
        (timbre/info "[storage-notification] stopping...")
        (storage-notification/stop)
        (timbre/info "[storage-notification] stopped")
        (dissoc component :storage-notification))
      component)))

(defn storage-system [{:keys [host port handler-fn sqs-creds auth-sqs-queue storage-sqs-queue
                              auth-sqs-msg-handler storage-sqs-msg-handler] :as opts}]
  (component/system-map
    :db-pool (map->RethinkPool {:size c/db-pool-size :regenerate-interval 5})
    :async-consumers (component/using
                        (map->AsyncConsumers {})
                        [])
    :auth-notification (component/using
                  (map->AuthNotification {:auth-notification-fn auth-sqs-msg-handler})
                  [:db-pool])
    :storage-notification (component/using
                  (map->StorageNotification {:storage-notification-fn storage-sqs-msg-handler})
                  [:db-pool])
    :auth-sqs (sqs/sqs-listener sqs-creds auth-sqs-queue auth-sqs-msg-handler)
    :storage-sqs (sqs/sqs-listener sqs-creds storage-sqs-queue storage-sqs-msg-handler)
    :handler (component/using
                (map->Handler {:handler-fn handler-fn})
                [:db-pool])
    :server  (component/using
                (map->HttpKit {:options {:port port}})
                [:handler])))