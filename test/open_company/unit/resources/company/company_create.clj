(ns open-company.unit.resources.company.company-create
  (:require [midje.sweet :refer :all]
            [open-company.lib.check :as check]
            [open-company.lib.resources :as r]
            [open-company.resources.company :as c]))

(with-state-changes [(before :facts (c/delete-all-companies!))
                     (after :facts (c/delete-all-companies!))]

  (fact "about validity checks of a valid new companies"
    (c/valid-company r/TICKER r/OPEN) => true)

  (facts "about validity checks of invalid new companies"

    (facts "when no ticker symbol is provided"
      (doseq [ticker r/bad-symbols]
        (c/valid-company ticker (assoc r/OPEN :symbol ticker)) => :invalid-symbol
        (c/valid-company ticker r/OPEN) => :invalid-symbol
        (c/valid-company r/TICKER (assoc r/OPEN :symbol ticker)) => :invalid-symbol))

    (facts "when no name is provided"
      (c/valid-company r/TICKER (dissoc r/OPEN :name)) => :invalid-name
      (doseq [bad-name r/bad-names]
        (c/valid-company r/TICKER (assoc r/OPEN :name bad-name)) => :invalid-name))

    (fact "when a ticker symbol that's too long is provided"
      (c/valid-company r/too-long (assoc r/OPEN :symbol r/too-long)) => :invalid-symbol)

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

    (c/create-company r/OPEN) => (contains r/OPEN)
    (c/get-company r/TICKER) => (contains r/OPEN)

    (facts "and unicode names"
      (doseq [good-name r/names]
        (let [new-oc (assoc r/OPEN :name good-name)]
          (c/create-company new-oc) => (contains new-oc)
          (c/get-company r/TICKER) => (contains new-oc)
          (c/delete-company r/TICKER))))

    (facts "and timestamps"
      (let [company (c/create-company r/OPEN)
            created-at (:created-at company)
            updated-at (:updated-at company)
            retrieved-company (c/get-company r/TICKER)]
        (check/timestamp? created-at) => true
        (check/about-now? created-at) = true
        (= created-at updated-at) => true
        (= created-at (:created-at retrieved-company)) => true
        (= updated-at (:updated-at retrieved-company)) => true))))