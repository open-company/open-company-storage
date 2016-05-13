(ns open-company.unit.resources.company.company-manipulate
  (:require [midje.sweet :refer :all]
            [open-company.db.pool :as pool]
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
            missing-sections {:sections {:progress ["highlights" "help"] :company ["ownership"]}}
            fixed-company (c/add-placeholder-sections (merge company missing-sections))] ; this is what's tested
        (:highlights fixed-company) => (-> (common/section-by-name "highlights")
                                           (assoc :placeholder true)
                                           (dissoc :core))
        (:help fixed-company) => (-> (common/section-by-name "help")
                                     (assoc :placeholder true)
                                     (dissoc :core))
        (:ownership fixed-company) => (-> (common/section-by-name "ownership")
                                          (assoc :placeholder true)
                                          (dissoc :core))))

    (future-fact "with partially specified placeholder sections"
      (let [company (c/get-company conn r/slug)
            updated-sections {:sections {:progress ["highlights" "help"] :company ["ownership"]}}
            updated-content (-> updated-sections
                                (assoc :highlights {:body "body a"})
                                (assoc :help {:title "title b" :body "body b"})
                                (assoc :ownership {:title "title c" :headline "headline c" :body "body c"}))
            fixed-company (c/merge-company company updated-content)] ; this is what's tested
        (:highlights fixed-company) => (-> (common/section-by-name "highlights")
                                           (assoc :placeholder false)
                                           (assoc :body "body a")
                                           (dissoc :core))
        (:help fixed-company) => (-> (common/section-by-name "help")
                                     (assoc :placeholder false)
                                     (assoc :title "title b")
                                     (assoc :body "body b")
                                     (dissoc :core))
        (:ownership fixed-company) => (-> (common/section-by-name "ownership")
                                          (assoc :placeholder false)
                                          (assoc :title "title c")
                                          (assoc :headline "headline c")
                                          (assoc :body "body c")
                                          (dissoc :core))))

    (fact "with missing prior sections"
      ;; add the sections to be priors
      (s/put-section conn r/slug :update r/text-section-1 r/coyote)
      (s/put-section conn r/slug :values r/text-section-2 r/coyote)
      (s/put-section conn r/slug :finances r/finances-section-1 r/coyote)
      ;; remove the sections
      (let [company (c/get-company conn r/slug)
            updated-company (-> company 
                                (assoc :sections {:sections {:progress [] :company []}})
                                (dissoc :update))]
        (c/update-company conn r/slug updated-company))
      ;; verify the sections are gone
      (:update (c/get-company conn r/slug)) => nil
      (:values (c/get-company conn r/slug)) => nil
      (:finances (c/get-company conn r/slug)) => nil
      ;; velify missing prior sections comes back
      (let [company (c/get-company conn r/slug)
            missing-sections {:sections {:progress ["update"] :company ["values"]}}
            fixed-company (c/add-prior-sections conn (merge company missing-sections))] ; this is what's tested
        (:update fixed-company) => (contains r/text-section-1)
        (:values fixed-company) => (contains r/text-section-2)
        (:finances fixed-company) => nil)) ; and that this one doesn't come back

    (future-fact "with partially specified prior sections"))))