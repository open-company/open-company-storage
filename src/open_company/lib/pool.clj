(ns open-company.lib.pool
  "Pool for expensive resources.

  Simplest possible pool that can work. Doesn't account for testing resource for validity or resource reconnection.

  Inspired by: https://github.com/robertluo/clj-poolman")

(defstruct resource-pool :init :close :low :high :resources)

;; ----- Grow the Pool with a New Resource -----

(defn- next-id
  "Find the next resource id (sequential numbers)"
  [resources]
  (let [ids (set (map :id resources))]
    (first (remove ids (iterate inc 0)))))

(defn- new-resource
  "Make a new resource for the pool"
  [init resources]
  (let [id (next-id resources)]
    {:id id :resource (init)}))

(defn- assoc-new-resource
  [{:keys [resources init] :as pool}]
  (assoc pool :resources (conj resources (new-resource init resources))))

;; ----- Pool Initialization and Shutdown -----

(defn- start*
  "Internal function for starting a pool. Use start instead."
  [low high init close]
  {:pre [(>= high low) (pos? low) init]} ; high and low must be sane, and the init function can't be nil
  (let [pool (struct resource-pool init close low high #{})]
    (reduce (fn [p _] (assoc-new-resource p)) pool (range low))))

(defn- stop*
  "Intenal function, to shutdown a pool. Use stop instead."
  [{:keys [resources close]}]
  (when close
    (dorun (map #(close (:resource %)) resources))))

;; ----- Get and Release Resources from the Pool -----

(defn get-resource
  "Low level function for getting process. Use with-resource macro instead."
  [{:keys [init high resources] :as pool}]
  ;; Get a resource that's not busy if we can
  (let [free-resources (remove :busy resources) ; all non-busy resources
        resource (if (seq free-resources) ; if there are some
                    (first free-resources) ; get the first one
                    (when (> high (count resources)) ; if not, can the pool still grow?
                      (new-resource init resources))) ; create a new resource for the pool
        ;; mark the resource as busy
        resource-after (assoc resource :busy true)
        ;; replace the free resource with the buny one
        resources (-> resources (disj resource) (conj resource-after))
        ;; if we got a resource, then update the state of the pool
        pool (if resource
                (assoc pool :resources resources)
                pool)]
    ;; return the new state of the pool, and the resource we got (if any)
    [pool resource]))

(defn release-resource
  "Low level function for releasing. Use with-resource macro instead."
  [{:keys [low close resources] :as pool} {res-id :id :as resource}]
  (let [busy-resource (first (filter #(= (:id %) res-id) resources))
  resources (disj resources busy-resource)
  resources (if (>= (count resources) low)
       (do
         (when close
           (close (:resource resource)))
         resources)
       (conj resources resource))]
    (assoc pool :resources resources)))

;; ----- External API -----

(defn start
  "
  Make a new resource pool for the specified `size` or `high` watermark and `low` watermark.
  An `init` function without argument is used to open a new resource, and `close` is a function
  which takes the resource as an argument and does something to release the resource.
  The return value of close will be ignored.
  "
  ([size init close] (start size size init close))

  ([low high init close] (atom (start* low high init close))))

(defn stop
  "Shutdown a resource pool"
  [pool]
  (stop* @pool))

(defmacro with-resource
  "Get a resource from a pool, bind it to res-name, so you can use it in body,
   after body finish, the resource will be returned to the pool."
  [[res-name ref-pool] & body]
  `(let [[new-pool# resource#] (get-resource (deref ~ref-pool))]
     (reset! ~ref-pool new-pool#)
     (try
      (let [~res-name (:resource resource#)]
        (do ~@body))
      (finally
       (reset! ~ref-pool (release-resource (deref ~ref-pool) resource#))))))