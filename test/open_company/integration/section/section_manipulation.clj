(ns open-company.integration.section.section-manipulation
  (:require [midje.sweet :refer :all]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.resources.company :as company]
            [open-company.resources.section :as section]
            [open-company.representations.company :as company-rep]
            ))

;; ----- Startup -----

(db/test-startup)

;; ----- Test Cases -----

;; PATCHing company sections with the REST API.

;; The system should support PATCHing the company sections property, and handle the following scenarios:

;; reorder sections
;; remove sections

;; ----- Tests -----

(with-state-changes [(before :facts (do (company/delete-all-companies!)
                                        (company/create-company r/open r/coyote)
                                        (section/put-section r/slug :update r/text-section-1 r/coyote)
                                        (section/put-section r/slug :finances r/finances-section-1 r/coyote)
                                        (section/put-section r/slug :team r/text-section-2 r/coyote)
                                        (section/put-section r/slug :help r/text-section-1 r/coyote)
                                        (section/put-section r/slug :diversity r/text-section-2 r/coyote)
                                        (section/put-section r/slug :values r/text-section-1 r/coyote)))
                     (after :facts (company/delete-all-companies!))]

  (facts "about section reordering"

    ;; verify the initial order
    (let [db-company (company/get-company r/slug)]
      (:categories db-company) => ["progress" "company"]
      (:sections db-company) => {:progress ["update" "finances" "team" "help"]
                                 :company ["diversity" "values"]})

    (facts "when the new order is valid"

      (fact "the section order in the progress category can be gently adjusted"
        (let [new-order {:progress ["team" "update" "finances" "help"]
                         :company ["diversity" "values"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})]
          (:status response) => 200
          (:sections (mock/body-from-response response)) => new-order
          ;; verify the new order
          (:sections (company/get-company r/slug)) => new-order))

      (fact "the sections order in the progress category can be greatly adjusted"
        (let [new-order {:progress ["help" "team" "finances" "update"]
                         :company ["diversity" "values"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})]
          (:status response) => 200
          (:sections (mock/body-from-response response)) => new-order
          ;; verify the new order
          (:sections (company/get-company r/slug)) => new-order))

      (fact "the section order in the company category can be adjusted"
        (let [new-order {:progress ["update" "finances" "team" "help"]
                         :company ["values" "diversity"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})]
          (:status response) => 200
          (:sections (mock/body-from-response response)) => new-order
          ;; verify the new order
          (:sections (company/get-company r/slug)) => new-order))

      (fact "the section order in the progress and company category can both be adjusted at once"
        (let [new-order {:progress ["help" "team" "finances" "update"]
                         :company ["values" "diversity"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})]
          (:status response) => 200
          (:sections (mock/body-from-response response)) => new-order
          ;; verify the new order
          (:sections (company/get-company r/slug)) => new-order)))

    (future-facts "when the new order is NOT valid"))

  (facts "about section removal"

    ;; verify the initial set of sections
    (:sections (company/get-company r/slug)) => {:progress ["update" "finances" "team" "help"]
                                                 :company ["diversity" "values"]}

      (fact "a section can be removed from the progress category"
        (let [new-set {:progress ["update" "finances" "help"]
                         :company ["diversity" "values"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-set}})
              body (mock/body-from-response response)
              db-company (company/get-company r/slug)]
          (:status response) => 200
          (:sections body) => new-set
          (:update body) => (contains r/text-section-1)
          (:finances body) => (contains r/finances-section-1)
          (:team body) => nil
          (:help body) => (contains r/text-section-1)
          (:diversity body) => (contains r/text-section-2)
          (:values body) => (contains r/text-section-1)
          ;; verify the new set
          (:sections db-company) => new-set
          (:update db-company) => (contains r/text-section-1)          
          (:finances db-company) => (contains r/finances-section-1)
          (:team db-company) => nil
          (:help db-company) => (contains r/text-section-1)
          (:diversity db-company) => (contains r/text-section-2)
          (:values db-company) => (contains r/text-section-1)))

      (fact "multiple sections can be removed from the progress category"
        (let [new-set {:progress ["finances"]
                         :company ["diversity" "values"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-set}})
              body (mock/body-from-response response)
              db-company (company/get-company r/slug)]
          (:status response) => 200
          (:sections body) => new-set
          (:update body) => nil
          (:finances body) => (contains r/finances-section-1)
          (:team body) => nil
          (:help body) => nil
          (:diversity body) => (contains r/text-section-2)
          (:values body) => (contains r/text-section-1)
          ;; verify the new set
          (:sections db-company) => new-set
          (:update db-company) => nil
          (:finances db-company) => (contains r/finances-section-1)
          (:team db-company) => nil
          (:help db-company) => nil
          (:diversity db-company) => (contains r/text-section-2)
          (:values db-company) => (contains r/text-section-1)))

      (fact "a section can be removed from the company category"
        (let [new-order {:progress ["update" "finances" "team" "help"]
                         :company ["diversity"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})
              body (mock/body-from-response response)
              db-company (company/get-company r/slug)]
          (:status response) => 200
          (:sections body) => new-order
          (:update body) => (contains r/text-section-1)
          (:finances body) => (contains r/finances-section-1)
          (:team body) => (contains r/text-section-2)
          (:help body) => (contains r/text-section-1)
          (:diversity body) => (contains r/text-section-2)
          (:values body) => nil
          ;; verify the new set
          (:sections db-company) => new-order
          (:update db-company) => (contains r/text-section-1)
          (:finances db-company) => (contains r/finances-section-1)
          (:team db-company) => (contains r/text-section-2)
          (:help db-company) => (contains r/text-section-1)
          (:diversity db-company) => (contains r/text-section-2)
          (:values db-company) => nil))

      (fact "sections can be removed from the progress and company categories at once"
        (let [new-order {:progress ["update" "help"]
                         :company ["values"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})
              body (mock/body-from-response response)
              db-company (company/get-company r/slug)]
          (:status response) => 200
          (:sections body) => new-order
          (:update body) => (contains r/text-section-1)
          (:finances body) => nil
          (:team body) => nil
          (:help body) => (contains r/text-section-1)
          (:diversity body) => nil
          (:values body) => (contains r/text-section-1)
          ;; verify the new set
          (:sections db-company) => new-order
          (:update db-company) => (contains r/text-section-1)
          (:finances db-company) => nil
          (:team db-company) => nil
          (:help db-company) => (contains r/text-section-1)
          (:diversity db-company) => nil
          (:values db-company) => (contains r/text-section-1)))))