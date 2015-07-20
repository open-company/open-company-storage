(ns open-company.unit.resources.company.company-create
  (:require [midje.sweet :refer :all]
            [open-company.lib.check :as check]
            [open-company.lib.resources :as r]
            [open-company.resources.company :as c]))

(with-state-changes [(before :facts (c/delete-all-companies!))
                     (after :facts (c/delete-all-companies!))]

  (fact "about validity checks of a valid new companies"
    (c/valid-company r/ok r/oc) => true)

  (facts "about validity checks of invalid new companies"

    (facts "when no ticker symbol is provided"
      (doseq [ticker r/bad-symbols]
        (c/valid-company ticker (assoc r/oc :symbol ticker)) => :invalid-symbol
        (c/valid-company ticker r/oc) => :invalid-symbol
        (c/valid-company r/ok (assoc r/oc :symbol ticker)) => :invalid-symbol))

    (facts "when no name is provided"
      (c/valid-company r/ok (dissoc r/oc :name)) => :invalid-name
      (doseq [bad-name r/bad-names]
        (c/valid-company r/ok (assoc r/oc :name bad-name)) => :invalid-name))

    (fact "when a ticker symbol that's too long is provided"
      (c/valid-company r/too-long (assoc r/oc :symbol r/too-long)) => :invalid-symbol)

    (future-fact "when a ticker symbol with special characters is provided")

    (future-fact "when a reserved property is included"))

  (future-facts "about company creation failures"

    (future-facts "when no ticker symbol is provided")

    (future-facts "when no name is provided")

    (future-facts "when a ticker symbol that's too long is provided")

    (future-fact "when a ticker symbol with special characters is provided")

    (future-fact "when a reserved property is included")

    (future-fact "when a ticker symbol is already used"))

  (facts "about company creation"

    (c/create-company r/oc) => (contains r/oc)

    (facts "and unicode names"
      (doseq [good-name r/names]
        (let [new-oc (assoc r/oc :name good-name)]
          (c/create-company new-oc) => (contains new-oc)
          (c/delete-company ok))))

    (facts "and timestamps"
      (let [company (c/create-company r/oc)
            created-at (:created-at company)
            updated-at (:updated-at company)
            retrieved-company (c/get-company r/ok)]
        (check/timestamp? created-at) => true
        (= created-at updated-at) => true
        (check/about-now? created-at) = true
        (= created-at (:created-at retrieved-company)) => true
        (= updated-at (:updated-at retrieved-company)) => true))))