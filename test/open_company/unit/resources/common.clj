(ns open-company.unit.resources.common
  (:require [open-company.resources.common :as co]
            [midje.sweet :refer :all]))

(facts "about with-timeout macro"
  (let [s "result"] 
    (co/with-timeout 100 s) => s
    (co/with-timeout 100 (do (Thread/sleep 90) s) => s)
    (co/with-timeout 100 (do (Thread/sleep 110) s)) => (throws Exception)))