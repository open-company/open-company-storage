(ns open-company.unit.resources.company.company-list
  (:require [midje.sweet :refer :all]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.resources.company :as c]))

;; ----- Startup -----

(db/test-startup)

;; ----- Tests -----

(with-state-changes [(before :facts (do
                                      (c/delete-all-companies!)
                                      (c/create-company r/open r/coyote)
                                      (c/create-company r/uni r/camus)
                                      (c/create-company r/buffer r/sartre)))
                     (after :facts (c/delete-all-companies!))]

  (facts "about listing companies"

    (fact "all existing companies are listed"
      (map :name (c/list-companies)) => (just (set (map :name [r/open r/uni r/buffer]))))

    (fact "removed companies are not listed"
      (c/delete-company (:slug r/buffer))
      (map :name (c/list-companies)) => (just (set (map :name [r/open r/uni]))))

    (fact "companies can be queried via secondary indexes"
      (count (c/get-companies-by-index "org-id" (:org-id r/sartre))) => 1
      (:slug (first (c/get-companies-by-index "org-id" (:org-id r/sartre)))) => (:slug r/buffer))))
