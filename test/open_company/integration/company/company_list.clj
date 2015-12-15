(ns open-company.integration.company.company-list
  (:require [midje.sweet :refer :all]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.hateoas :as hateoas]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.lib.slugify :refer (slugify)]
            [open-company.resources.company :as company]
            [open-company.representations.common :refer (GET)]
            [open-company.representations.company :as company-rep]))

;; ----- Startup -----

(db/test-startup)

;; ----- Tests -----

(with-state-changes [(before :facts (do
                                      (company/delete-all-companies!)
                                      (company/create-company r/open r/coyote)
                                      (company/create-company r/uni r/camus)
                                      (company/create-company r/buffer r/sartre)))
                     (after :facts (company/delete-all-companies!))]

  (facts "about listing companies"

    (fact "all existing companies are listed"
      (let [response (mock/api-request :get "/companies")
            body (mock/body-from-response response)
            companies (get-in body [:collection :companies])]
        (:status response) => 200
        (map :name companies) => (just (set (map :name [r/open r/uni r/buffer]))) ; verify names
        (map :slug companies) => (just (set [(:slug r/open) (slugify (:name r/uni)) (:slug r/buffer)])) ; verify slugs
        (doseq [company companies] ; verify HATEOAS links
          (count (:links company)) => 1
          (hateoas/verify-link "self" "GET" (company-rep/url company) company-rep/media-type (:links company)))))

    (fact "removed companies are not listed"
      (company/delete-company (:slug r/buffer))
      (let [response (mock/api-request :get "/companies")
            body (mock/body-from-response response)
            companies (get-in body [:collection :companies])]
        (:status response) => 200
        (map :name companies) => (just (set (map :name [r/open r/uni]))))))) ; verify names