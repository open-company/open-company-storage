(ns open-company.db.pool
  "RethinkDB database connection pool."
  (:require [rethinkdb.query :as r]
            [taoensso.timbre :as timbre]
            [open-company.config :as config])
  (:import [java.util.concurrent LinkedBlockingQueue TimeUnit]))

;; Opening & closing of connections

(defn init-conn []
  (apply r/connect config/db-options))

(defn close-conn [conn]
  ;; Sometimes this blocks for reasons I don't yet understand
  (timbre/trace "Closing connection...")
  (rethinkdb.core/close conn)
  (timbre/trace "Connection closed."))

;; Taken from the wonderful aphyr's Riemann
;; https://github.com/riemann/riemann/blob/master/src/riemann/pool.clj

(defprotocol Pool
  (grow [pool]
    "Adds an element to the pool.")
  (claim [pool] [pool timeout]
    "Take a thingy from the pool. Timeout in seconds; if unspecified, 0.
     Returns nil if no thingy available.")
  (release [pool thingy]
    "Returns a thingy to the pool.")
  (invalidate [pool thingy]
    "Tell the pool a thingy is no longer valid."))

(defrecord FixedQueuePool [queue open close regenerate-interval]
  Pool
  (grow [this]
    (loop []
      (if-let [thingy (try (open) (catch Throwable t nil))]
        (do
          (timbre/trace "New connection being added to pool")
          (.put ^LinkedBlockingQueue queue thingy))
        (do
          (Thread/sleep (* 1000 regenerate-interval))
          (recur)))))

  (claim [this]
    (claim this nil))

  (claim [this timeout]
    (let [timeout (* 1000 (or timeout 0))]
      (or
       (try
         (.poll ^LinkedBlockingQueue queue timeout TimeUnit/MILLISECONDS)
         (catch java.lang.InterruptedException e
           nil))
       (throw
        (ex-info (str "Couldn't claim a resource from the pool within " timeout " ms") {})))))

  (release [this thingy]
    (when thingy
      (timbre/trace "Releasing" thingy "back into pool")
      (.put ^LinkedBlockingQueue queue thingy)))

  (invalidate [this thingy]
    (when thingy
      (try (close thingy)
           (catch Throwable t
             (timbre/warn t "Closing" thingy "threw")))
      (future (grow this)))))

(defn fixed-pool
  "A fixed pool of thingys. (open) is called to generate a thingy. (close
  thingy) is called when a thingy is invalidated. When thingys are invalidated,
  the pool will immediately try to open a new one; if open throws or returns
  nil, the pool will sleep for regenerate-interval seconds before retrying
  (open).
  :regenerate-interval    How long to wait between retrying (open).
  :size                   Number of thingys in the pool.
  :block-start            Should (fixed-pool) wait until the pool is full
                          before returning?
  Note that fixed-pool is correct only if every successful (claim) is followed
  by exactly one of either (invalidate) or (release). If calls are unbalanced;
  e.g. resources are not released, doubly released, or released *and*
  invalidated, starvation or unbounded blocking could occur. (with-pool)
  provides this guarantee."
  ([open]
   (fixed-pool open {}))
  ([open opts]
   (fixed-pool open identity opts))
  ([open close opts]
   (let [^int size            (or (:size opts) (* 2 (.availableProcessors
                                                      (Runtime/getRuntime))))
         regenerate-interval  (or (:regenerate-interval opts) 5)
         block-start          (get opts :block-start true)
         pool (FixedQueuePool.
                (LinkedBlockingQueue. size)
                open
                close
                regenerate-interval)
         openers (doall
                   (map (fn open-pool [_]
                          (future (grow pool)))
                        (range size)))]
     (when block-start
       (doseq [worker openers] @worker))
     pool)))

;; --- Handy macro to claim and release things from pool

(defmacro with-pool
  "Evaluates body in a try expression with a symbol 'thingy claimed from the
  given pool, with specified claim timeout. Releases thingy at the end of the
  body, or if an exception is thrown, invalidates them and rethrows. Example:
  ; With client, taken from connection-pool, waiting 5 seconds to claim, send
  ; client a message.
  (with-pool [client connection-pool 5]
    (send client a-message))"
  [[thingy pool timeout] & body]
  ;; Destructuring bind could change nil to a, say, vector, and cause unbalanced claim/release.
  `(let [thingy# (claim ~pool ~timeout)
         ~thingy thingy#]
     (try
       (let [res# (do ~@body)]
         (release ~pool thingy#)
         res#)
       (catch Throwable t#
         (invalidate ~pool thingy#)
         (throw t#)))))

;; --- Define stateful pool & functions to rebuild

(defn shutdown-pool!
  [pool]
  (doseq [thing (to-array (:queue pool))]
    ((:close pool) thing))
  (.clear (:queue pool)))

(defn rebuild-pool!
  "Rebuild the entire pool. This is not atomic so other threads
  taking connections at the same time may get stale ones.
  Those are invalidated as soon as they cause an exception."
  [pool]
  (let [size (.size (:queue pool))]
    (shutdown-pool! pool)
    (dotimes [i size]
      (grow pool))))

(def rethinkdb-pool
  (do (when (bound? #'rethinkdb-pool) (shutdown-pool! rethinkdb-pool))
      (fixed-pool init-conn close-conn {:size config/db-pool-size
                                              :regenerate-interval 15})))


(comment
  (timbre/set-config! (assoc open-company.config/log-config :level :trace))

  (.size (:queue rethinkdb-pool))

  (rebuild-pool! rethinkdb-pool)

  (shutdown-pool! rethinkdb-pool)

  (def c (.poll (:queue rethinkdb-pool)))

  (deref c)

  (rethinkdb.core/close c)

  (defn repro []
    (let [mk-conn #(r/connect :host "127.0.0.1" :port 28015 :db "test")
          n     (-> (Runtime/getRuntime)
                    (.availableProcessors)
                    (* 2)
                    (+ 42))
          conns (doall (map (fn [_] (mk-conn)) (range n)))]
      (rethinkdb.core/close (rand-nth conns))))

  (repro))
