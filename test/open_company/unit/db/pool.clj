(ns open-company.unit.db.pool
  (:require [midje.sweet :refer :all]
            [open-company.db.pool :as pool]))

(def counter (atom 0))

;; simple init function that makes each resource a unique ascending int
(def init (fn [] (swap! counter inc)))

(def rethinkdb-connection-keys #{:ch :db :in :loops :out :pub :r-ch :socket :token :waiting})

(defn reset-pool [new-pool] (reset! pool/rethinkdb-pool new-pool))

(with-state-changes [(before :facts (reset-pool nil))]

  (facts "about resource pools"

    (facts "when failing to start resource pools"

      ;; Pool size not positive
      (pool/start* 0 0 init) => (throws AssertionError)

      ;; Pool size not sane
      (pool/start* 5 3 init) => (throws AssertionError)

      ;; No init
      (pool/start* 3 3 nil) => (throws AssertionError))

    (with-state-changes [(before :facts (reset! counter 0))]

      (facts "when starting resource pools"

        (fact "pool starts at low size, not high size"

          (pool/start* 3 5 init) =>
            {:connections #{
              {:id 0 :connection 1}
              {:id 1 :connection 2}
              {:id 2 :connection 3}
            }
            :high 5
            :low 3
            :init init})

        (fact "pool knows when low and high size are the same"

          (pool/start* 5 5 init) =>
            {:connections #{
              {:id 0 :connection 1}
              {:id 1 :connection 2}
              {:id 2 :connection 3}
              {:id 3 :connection 4}
              {:id 4 :connection 5}
            }
            :high 5
            :low 5
            :init init})))

    (facts "when using resources"

      (fact "it provides 2 resources, then no more"

        (pool/start 2 2)

        (pool/with-connection [conn]
          (keys @conn) => (just rethinkdb-connection-keys) ; valid connection
          (pool/with-connection [conn]
            (keys @conn) => (just rethinkdb-connection-keys) ; valid connection
            (pool/with-connection [conn]
              conn => nil)))) ; no more connections

      (fact "it grows from 2 to 4, then no more"

        (pool/start 2 4)

        (pool/with-connection [conn]
          (keys @conn) => (just rethinkdb-connection-keys) ; valid connection
          (pool/with-connection [conn]
            (keys @conn) => (just rethinkdb-connection-keys) ; valid connection
            (pool/with-connection [conn]
              (keys @conn) => (just rethinkdb-connection-keys) ; valid connection
              (pool/with-connection [conn]
                (keys @conn) => (just rethinkdb-connection-keys) ; valid connection
                (pool/with-connection [conn]
                  conn => nil))))))))) ; no more connections

(facts "about resource pools"

  (fact "it provides the same resource sequentially"

    (reset! counter 0)
    (reset-pool (pool/start* 2 4 init))

    (pool/with-connection [res]
      res => 1
      (:connections @pool/rethinkdb-pool) => (just [{:id 0 :connection 1 :busy true} {:id 1 :connection 2}])))

    (pool/with-connection [res]
      res => 1
      (:connections @pool/rethinkdb-pool) => (just [{:id 0 :connection 1 :busy true} {:id 1 :connection 2}]))

    (pool/with-connection [res]
      res => 1
      (:connections @pool/rethinkdb-pool) => (just [{:id 0 :connection 1 :busy true} {:id 1 :connection 2}]))

    (pool/with-connection [res]
      res => 1
      (:connections @pool/rethinkdb-pool) => (just [{:id 0 :connection 1 :busy true} {:id 1 :connection 2}])
      (pool/with-connection [res]
        res => 2
        (:connections @pool/rethinkdb-pool) =>
          (just [{:id 0 :connection 1 :busy true} {:id 1 :connection 2 :busy true}])))

    (pool/with-connection [res]
      res => 1
      (:connections @pool/rethinkdb-pool) => (just [{:id 0 :connection 1 :busy true} {:id 1 :connection 2}])
      (pool/with-connection [res]
        res => 2
        (:connections @pool/rethinkdb-pool) =>
          (just [{:id 0 :connection 1 :busy true} {:id 1 :connection 2 :busy true}]))))