(ns open-company.unit.pool
  (:require [midje.sweet :refer :all]
            [open-company.lib.pool :refer (start with-resource stop)]))

(def counter (atom 0))

;; simple init function that makes each resource a unique ascending int
(def init (fn [] (swap! counter inc)))

;; simple close function that can keep track of what was closed
(def closed (atom []))
(def close (fn [res] (swap! closed conj res)))

(facts "about resource pools"

  (facts "when failing to start resource pools"

    ;; Pool size not positive
    (start 0 init nil) => (throws AssertionError)
    (start 0 0 init nil) => (throws AssertionError)

    ;; Pool size not sane
    (start 5 3 init nil) => (throws AssertionError)

    ;; No init
    (start 3 nil nil) => (throws AssertionError))

  (facts "when starting resource pools"

    (reset! counter 0)

    @(start 3 5 init nil) =>
      {:resources #{
        {:id 0 :resource 1}
        {:id 1 :resource 2}
        {:id 2 :resource 3}
      }
      :high 5
      :low 3
      :init init
      :close nil}

    (reset! counter 0)

    @(start 5 init close) =>
      {:resources #{
        {:id 0 :resource 1}
        {:id 1 :resource 2}
        {:id 2 :resource 3}
        {:id 3 :resource 4}
        {:id 4 :resource 5}
      }
      :high 5
      :low 5
      :init init
      :close close})

  (facts "when using resources"

    (fact "it provides 2 resources, then no more"

      (reset! counter 0)

      (let [pool (start 2 init nil)]
        (with-resource [res pool]
          res => 2
          (:resources @pool) => (just [{:id 0, :resource 1} {:id 1, :resource 2, :busy true}])
          (with-resource [res pool]
            res => 1
            (:resources @pool) => (just [{:id 0, :resource 1, :busy true} {:id 1, :resource 2, :busy true}])
            (with-resource [res pool]
              res => nil
              (:resources @pool) => (just [{:id 0, :resource 1, :busy true} {:id 1, :resource 2, :busy true}]))))))

    (fact "it grows from 2 to 4, then no more"

      (reset! counter 0)

      (let [pool (start 2 4 init nil)]
        (with-resource [res pool]
          res => 2
          (:resources @pool) => (just [{:id 0, :resource 1} {:id 1, :resource 2, :busy true}])
          (with-resource [res pool]
            res => 1
            (:resources @pool) => (just [{:id 0, :resource 1, :busy true} {:id 1, :resource 2, :busy true}])
            (with-resource [res pool]
              res => 3
              (:resources @pool) => (just [
                {:id 0, :resource 1, :busy true}
                {:id 1, :resource 2, :busy true}
                {:id 2, :resource 3, :busy true}])
              (with-resource [res pool]
                res => 4
                (:resources @pool) => (just [
                  {:id 0, :resource 1, :busy true}
                  {:id 1, :resource 2, :busy true}
                  {:id 2, :resource 3, :busy true}
                  {:id 3, :resource 4, :busy true}])
                (with-resource [res pool]
                  res => nil
                  (:resources @pool) => (just [
                    {:id 0, :resource 1, :busy true}
                    {:id 1, :resource 2, :busy true}
                    {:id 2, :resource 3, :busy true}
                    {:id 3, :resource 4, :busy true}])))))))))

    (fact "it provides the same resource sequentially"

      (reset! counter 0)

      (let [pool (start 2 4 init nil)]

        (with-resource [res pool]
          res => 2
          (:resources @pool) => (just [{:id 0, :resource 1} {:id 1, :resource 2, :busy true}]))

        (with-resource [res pool]
          res => 2
          (:resources @pool) => (just [{:id 0, :resource 1} {:id 1, :resource 2, :busy true}]))

        (with-resource [res pool]
          res => 2
          (:resources @pool) => (just [{:id 0, :resource 1} {:id 1, :resource 2, :busy true}]))

        (with-resource [res pool]
          res => 2
          (:resources @pool) => (just [{:id 0, :resource 1} {:id 1, :resource 2, :busy true}])
          (with-resource [res pool]
            res => 1
            (:resources @pool) => (just [{:id 0, :resource 1, :busy true} {:id 1, :resource 2, :busy true}])))

        (with-resource [res pool]
          res => 2
          (:resources @pool) => (just [{:id 0, :resource 1} {:id 1, :resource 2, :busy true}])
          (with-resource [res pool]
            res => 1
            (:resources @pool) => (just [{:id 0, :resource 1, :busy true} {:id 1, :resource 2, :busy true}])))))

  (facts "when stopping resource pools"

    (reset! counter 0)

    (stop (start 2 4 init nil)) => nil

    (fact "it calls close for each initially initiated resource"

      (reset! counter 0)

      (stop (start 2 4 init close)) => nil
        (provided
          (close 1) => true :times 1)
        (provided
          (close 2) => true :times 1)
        (provided
          (close 3) => true :times 0)
        (provided
          (close 4) => true :times 0))

    (fact "it calls close for each eventually initiated resource"

      (reset! counter 0)
      (reset! closed [])

      (let [pool (start 2 4 init close)]
        (with-resource [res pool]
          res => 2
          (with-resource [res pool]
            res => 1
            (with-resource [res pool]
              res => 3)))
        (stop pool) => nil)
      @closed => (just #{1 2 3}))))