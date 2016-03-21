(ns open-company.unit.representations.company
  (:require [midje.sweet :refer :all]
            [open-company.representations.company :as c]))

(facts "about company-links*"
  (fact "returns all links if provided with ::all links keyword"
    (let [links (#'c/company-links* {:name "Test" :slug "test"} :all-links)]
      (map (comp keyword :rel) links) => [:self :update :partial-update :delete :section-list]))
  (fact "returns only links that have been provided"
    (let [links (#'c/company-links* {:name "Test" :slug "test"} [:self :partial-update])]
      (map (comp keyword :rel) links) => [:self :partial-update]
      (count links) => 2))
  (fact "throws assertions when wrong args are passed"
    (#'c/company-links* {:name "Test" :slug "test"} :bogus) => (throws AssertionError)
    (#'c/company-links* {:name "Test" :slug "test"} [:bogus]) => (throws AssertionError)))