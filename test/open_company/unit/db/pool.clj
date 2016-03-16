(ns open-company.unit.db.pool
  (:require [midje.sweet :refer :all]
            [open-company.db.pool :as p]))

(facts "about claiming & releasing pool inventory"
         (let [x (atom 0)
               pool (p/fixed-pool #(swap! x inc) {:size 2 :block-start true})
               ; Claim both elements
               a  (p/claim pool)
               b  (p/claim pool)
               ; Pool is empty; should throw
               c  (try (p/claim pool 2/1000)
                       (catch Exception e
                         (.getMessage e)))
               a' (p/release pool a)
               ; Should re-acquire a
               d  (p/claim pool)
               ; Empty
               e  (try (p/claim pool)
                       (catch Exception _ :timeout))
               b' (p/release pool b)
               ; Re-acquire b
               f  (p/claim pool)]
           #{a b} => #{1 2}
           c => "Couldn't claim a resource from the pool within 2 ms"
           a => d
           e => :timeout
           b => f
           ; Shouldn't have (open)'d more than twice.
           @x => 2))

(facts "about claiming and invalidating"
         (let [x (atom 0)
               pool (p/fixed-pool #(swap! x inc) {:size 2 :block-start true})
               a  (p/claim pool)
               a' (p/invalidate pool a)
               b  (p/claim pool)

               b' (p/invalidate pool b)
               c  (p/claim pool 1)
               d  (p/claim pool 1)
               e  (try (p/claim pool 1) (catch Exception e nil))
               c' (p/invalidate pool c)
               d' (p/invalidate pool d)
               ; Invalidate nil should be a noop
               e' (p/invalidate pool e)]
           ; Wait for futures.
           a' => truthy
           b' => truthy
           c' => truthy
           d' => truthy
           e' => nil

           (dorun (map deref [a' b' c' d']))

           a => 1
           b => 2
           #{a b c d} #{1 2 3 4}
           e => nil
           ; Should have opened twice to start and 4 times after invalidations.
           @x => 6))

(facts "about with-pool macro"
         (let [x (atom 0)
               pool (p/fixed-pool #(swap! x inc) {:size 1 :block-start true})]

           ; Regular claim
           (let [a (p/with-pool [a pool] a)]
             a => 1
             @x => 1)

           ; With-pool should have released.
           (let [a (p/claim pool)]
             a => 1
             (p/release pool a))

           ; Throwing errors
           (p/with-pool [b pool]
             b => 1
             (throw (RuntimeException. "whoops")))
           => (throws RuntimeException)

           ; Pool should have regenerated.
           (Thread/sleep 250)
           @x => 2))