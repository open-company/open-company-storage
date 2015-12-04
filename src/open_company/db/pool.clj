(ns open-company.db.pool
  "RethinkDB database connection pool.

  Simple pool that doesn't account for testing connection for validity
  or connection reconnection.

  Inspired by: https://github.com/robertluo/clj-poolman"
  (:require [rethinkdb.query :as r]
            [taoensso.timbre :as timbre :refer (trace debug info warn error fatal)]
            [open-company.config :as config]))

(timbre/set-config! config/log-config)

(def rethinkdb-pool (atom nil))

;; ----- Grow the Pool with a New Connection -----

(defn init-connection
  []
  (apply r/connect config/db-options))

(defn- next-id
  "Find the next connection id (sequential numbers)"
  [connections]
  (let [ids (set (map :id connections))]
    (first (remove ids (iterate inc 0)))))

(defn- new-connection
  "Make a new connection for the pool"
  [init connections]
  (let [id (next-id connections)]
    {:id id :connection (init)}))

(defn- assoc-new-connection
  [{:keys [connections init] :as pool}]
  (assoc pool :connections (conj connections (new-connection init connections))))

;; ----- Connection Pool Initialization -----

(defn start*
  "Internal function for starting a pool. Use start instead."
  [low high init]
  {:pre [(>= high low) (pos? low) init]} ; high and low must be sane, and the init function can't be nil
  (let [pool {:init init :low low :high high :connections #{}}]
    (reduce (fn [p _] (assoc-new-connection p)) pool (range low))))

;; ----- Get and Release Connections from the Pool -----

(defn get-connection
  "Low level function for getting process. Use with-connection macro instead."
  [{:keys [init high connections] :as pool}]
  ;; Get a connection that's not busy if we can
  (trace "Getting DB pool connection from pool size" (count (:connections pool)) "of" high "." )
  (let [free-connections (remove :busy connections) ; all non-busy connections
        connection (if (seq free-connections) ; if there are some
                    (first free-connections) ; get the first one
                    (when (> high (count connections)) ; if not, can the pool still grow?
                      (new-connection init connections))) ; create a new connection for the pool
        ;; mark the connection as busy
        connection-after (assoc connection :busy true)
        ;; replace the free connection with the busy one
        connections (-> connections (disj connection) (conj connection-after))
        ;; if we got a connection, then update the state of the pool
        pool (if connection
                (assoc pool :connections connections)
                pool)]
    
    ;; handle the case of no connection available
    (if-not connection (let [msg "No connection available from DB pool"]
                        (error msg)
                        (throw (RuntimeException. msg))))
    
    ;; return the new state of the pool, and the connection we got
    (trace "Using connection: " connection)
    [pool connection]))

(defn release-connection
  "Low level function for releasing. Use with-connection macro instead."
  [{:keys [connections] :as pool} {res-id :id :as connection}]
  ;; Get the connection to be released from the pool
  (let [busy-connection (first (filter #(= (:id %) res-id) connections))
        connections (disj connections busy-connection) ; out of the pool
        connections (conj connections connection)] ; back into the pool w/o busy
    (assoc pool :connections connections)))

;; ----- External API -----

(defn start
  "Start a connection pool. Uses pool size from the config or optionally passed in
  `low` and `high` pool size. Returns `:ok` if pool starts, or `:started` if the
  pool was already started."
  ([] (start config/db-pool-size config/db-pool-size))
  ([low high]
    (info "Starting DB pool with low:" low " high:" high)
    (reset! rethinkdb-pool (start* low high init-connection))
    :ok))

(defmacro with-connection
  "Get a connection from a pool, bind it to connection, so you can use it in body,
   after body finish, the connection will be returned to the pool."
  [[connection] & body]
  `(do
    (trace "Starting DB pool connection macro.")
    (when (nil? @rethinkdb-pool) (debug "DB pool not started. Starting.") (start))
    (let [[new-pool# conn#] (get-connection (deref rethinkdb-pool))]
      (reset! rethinkdb-pool new-pool#)
      (try
        (let [~connection (:connection conn#)]
          (do ~@body))
        (finally
          (if conn# (reset! rethinkdb-pool (release-connection (deref rethinkdb-pool) conn#))))))))