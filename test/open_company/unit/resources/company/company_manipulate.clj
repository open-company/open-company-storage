(ns open-company.unit.resources.company.company-manipulate
  (:require [midje.sweet :refer :all]
            [oc.lib.rethinkdb.pool :as pool]
            [open-company.lib.test-setup :as ts]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.resources.common :as common]
            [open-company.resources.company :as c]
            [open-company.resources.section :as s]))

;; ----- Tests -----

(with-state-changes [(before :contents (ts/setup-system!))
                     (after :contents (ts/teardown-system!))
                     (before :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                      (c/delete-all-companies! conn)
                                      (c/create-company! conn (c/->company r/open r/coyote))))
                     (after :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                     (c/delete-all-companies! conn)))]

  (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
  (facts "about fixing up companies"

    (fact "with missing placeholder sections"
      (let [company (c/get-company conn r/slug)
            missing-sections {:sections ["highlights" "help" "ownership"]}
            fixed-company (c/add-placeholder-sections (merge company missing-sections))] ; this is what's tested
        (:highlights fixed-company) => (-> (common/sections-by-name :highlights)
                                           (assoc :placeholder true))
        (:help fixed-company) => (-> (common/sections-by-name :help)
                                     (assoc :placeholder true))
        (:ownership fixed-company) => (-> (common/sections-by-name :ownership)
                                          (assoc :placeholder true))))

    (fact "with missing prior sections"
      ;; add the sections to be priors
      (s/put-section conn r/slug :update r/text-section-1 r/coyote)
      (s/put-section conn r/slug :values r/text-section-2 r/coyote)
      (s/put-section conn r/slug :finances r/finances-section-1 r/coyote)
      ;; remove the sections
      (let [company (c/get-company conn r/slug)
            updated-company (-> company 
                                (assoc :sections [])
                                (dissoc :update))]
        (c/update-company conn r/slug updated-company))
      ;; verify the sections are gone
      (let [company (c/get-company conn r/slug)]
        (:update company) => nil
        (:values company) => nil
        (:finances company) => nil)
      ;; velify missing prior sections comes back
      (let [company (c/get-company conn r/slug)
            missing-sections {:sections ["update" "values"]}
            fixed-company (c/add-prior-sections conn (merge company missing-sections))] ; this is what's tested
        (:update fixed-company) => (contains r/text-section-1)
        (:values fixed-company) => (contains r/text-section-2)
        (:finances fixed-company) => nil)) ; and that this one doesn't come back

    (future-fact "with partially specified prior sections"))))