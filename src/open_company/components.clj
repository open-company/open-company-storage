(ns open-company.components
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [open-company.db.pool :as pool]
            [org.httpkit.server :as httpkit]))

(defrecord HttpKit [options handler server]
  component/Lifecycle
  (start [component]
    (let [handler (get-in component [:handler :handler] handler)
          server  (httpkit/run-server handler options)]
      (assoc component :server server)))
  (stop [component]
    ;; (prn component)
    (if-not server
      component
      (do
        (server)
        (dissoc component :server)))))

(defrecord RethinkPool [size regenerate-interval pool]
  component/Lifecycle
  (start [component]
    (let [pool (pool/fixed-pool pool/init-conn pool/close-conn
                                {:size size :regenerate-interval 15})]
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
    (assoc component :handler (handler-fn component)))
  (stop [component]
    (dissoc component :handler)))

;; (def pool
;;   (map->RethinkPool {:size 5 :regenerate-interval 10}))

(defn oc-system [opts]
  (let [{:keys [host port handler-fn]} opts]
    (component/system-map
     :db-pool (map->RethinkPool {:size 5 :regenerate-interval 5})
     :handler (component/using
               (map->Handler {:handler-fn handler-fn})
               [:db-pool])
     :server  (component/using
               (map->HttpKit {:options {:port port}})
               [:handler]))))