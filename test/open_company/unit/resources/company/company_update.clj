(ns open-company.unit.resources.company.company-update
  (:require [midje.sweet :refer :all]
            [clj-time.core :as t]
            [open-company.lib.check :as check]
            [open-company.lib.resources :as r]
            [open-company.resources.company :as c]))

(with-state-changes [(before :facts (do
                                      (c/delete-all-companies!)
                                      (c/create-company r/oc)))
                     (after :facts (c/delete-all-companies!))]

  (future-facts "about company update failures")

  (facts "about updating companies"

    (let [new-oc (assoc r/oc :name "Transparency, Inc.")]
      (c/update-company r/ok new-oc) => (contains new-oc)
      (c/get-company r/ok) => (contains new-oc))

    (future-facts "when updating the ticker symbol")

    (facts "and timestamps"
      (Thread/sleep 1000) ; delay for 1 second to allow timestamps to differ
      (let [new-oc (assoc r/oc :name "Transparency, Inc.")
            company (c/update-company r/ok new-oc)
            created-at (:created-at company)
            updated-at (:updated-at company)
            retrieved-company (c/get-company r/ok)]
        (check/timestamp? created-at) => true
        (check/timestamp? updated-at) => true
        (= created-at updated-at) => false
        (t/before? (check/time-for created-at) (check/time-for updated-at)) => true
        (check/about-now? updated-at) = true
        (= created-at (:created-at retrieved-company)) => true
        (= updated-at (:updated-at retrieved-company)) => true))))