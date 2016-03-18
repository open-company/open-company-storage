(ns open-company.unit.resources.company.company-manipulate
  (:require [midje.sweet :refer :all]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.resources.common :as common]
            [open-company.resources.company :as c]
            [open-company.resources.section :as s]))

;; ----- Startup -----

(db/test-startup)

;; ----- Tests -----

(with-state-changes [(before :facts (do
                                      (c/delete-all-companies!)
                                      (c/create-company! (c/->company r/open r/coyote))))
                     (after :facts (c/delete-all-companies!))]

  (facts "about fixing up companies"

    (fact "with missing placeholder sections"
      (let [company (c/get-company r/slug)
            missing-sections {:sections {:progress ["highlights"] :company ["help"] :financial ["ownership"]}}
            fixed-company (c/add-placeholder-sections (merge company missing-sections))]
        (:highlights fixed-company) => (-> (common/section-by-name "highlights")
                                          (assoc :placeholder true)
                                          (dissoc :core))
        (:help fixed-company) => (-> (common/section-by-name "help")
                                          (assoc :placeholder true)
                                          (dissoc :core))
        (:ownership fixed-company) => (-> (common/section-by-name "ownership")
                                          (assoc :placeholder true)
                                          (dissoc :core))))

    (fact "with missing prior sections"
      ; add the sections to be priors
      (s/put-section r/slug :update r/text-section-1 r/coyote)
      (s/put-section r/slug :values r/text-section-2 r/coyote)
      (s/put-section r/slug :finances r/finances-section-1 r/coyote)
      ; remove the sections
      (let [company (c/get-company r/slug)
            updated-company (-> company 
                              (assoc :sections {:sections {:progress [] :company [] :financial []}})
                              (dissoc :update))]
        (c/update-company r/slug updated-company))
      ; verify the sections are gone
      (:update (c/get-company r/slug)) => nil
      (:values (c/get-company r/slug)) => nil
      (:finances (c/get-company r/slug)) => nil
      ; velify missing prior sections comes back
      (let [company (c/get-company r/slug)
            missing-sections {:sections {:progress ["update"] :company ["values"] :financial []}}
            fixed-company (c/add-prior-sections (merge company missing-sections))]
        (:update fixed-company) => (contains r/text-section-1)
        (:values fixed-company) => (contains r/text-section-2)
        (:finances fixed-company) => nil)))) ; and that this one doesn't come back