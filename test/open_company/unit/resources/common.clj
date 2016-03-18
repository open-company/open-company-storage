(ns open-company.unit.resources.common
  (:require [open-company.resources.common :as co]
            [midje.sweet :refer :all]))

(facts "about with-timeout macro"
  (let [s "result"] 
    (co/with-timeout 50 s) => s
    (co/with-timeout 50 (do (Thread/sleep 40) s) => s)
    (co/with-timeout 50 (do (Thread/sleep 60) s)) => (throws Exception)))