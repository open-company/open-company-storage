(ns open-company.integration.company.company-retrieval
  (:require [midje.sweet :refer :all]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.hateoas :as hateoas]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.resources.common :as common]
            [open-company.resources.company :as company]
            [open-company.resources.section :as section]
            [open-company.representations.common :refer (GET)]
            [open-company.representations.company :as company-rep]
            [open-company.representations.section :as section-rep]))

;; ----- Startup -----

(db/test-startup)

;; ----- Test Cases -----

;; GETing a company with the REST API

;; The system should return a representation of the company and handle the following scenarios:

;; GET

;; bad - invalid JWToken - 401 Unauthorized

;; bad - no matching company slug - 404 Not Found

;; all good - matching JWToken - 200 OK
;; all good - no JWToken - 200 OK
;; all good - not matching JWToken - 200 OK

;; TODO
;; no accept
;; no content type
;; no charset
;; wrong accept
;; wrong content type
;; wrong charset

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

  (facts "about failing to retrieve a company"

    (fact "with an invalid JWToken"
      (:status (mock/api-request :get (company-rep/url r/open) {:auth mock/jwtoken-bad})) => 401)

    (fact "that doesn't exist"
      (:status (mock/api-request :get (company-rep/url "foo"))) => 404))

  (facts "about retrieving a company"

    (fact "that does match the retrieving user's organization"
      (let [response (mock/api-request :get (company-rep/url r/open))
            body (mock/body-from-response response)]
        (:status response) => 200
        (:name body) => (:name r/open)
        (:slug body) => (:slug r/open)
        (:org-id body) => nil ; verify no org-id
        (:categories body) => (map name common/categories)
        (:sections body) =>
          {:company ["diversity" "values"], :financial ["finances"], :progress ["update" "team" "help"]}
        ;; verify section contents
        (:update body) => (contains r/text-section-1)
        (:finances body) => (contains r/finances-section-1)
        (:team body) => (contains r/text-section-2)
        (:help body) => (contains r/text-section-1)
        (:diversity body) => (contains r/text-section-2)
        (:values body) => (contains r/text-section-1)
         ;; verify each section has all the HATEOAS links
        (doseq [section-key (map keyword (flatten (vals (:sections body))))]
          (hateoas/verify-section-links (:slug r/open) section-key (:links (body section-key))))
        ;; verify the company has all the HATEOAS links
        (hateoas/verify-company-links (:slug r/open) (:links body))))

    (fact "anonymously with no JWToken"
      ;; retrieve with no JWToken
      (let [response (mock/api-request :get (company-rep/url r/open) {:skip-auth true})
            body (mock/body-from-response response)]
        (:status response) => 200
        (:sections body) =>
          {:company ["diversity" "values"], :financial ["finances"], :progress ["update" "team" "help"]}
        ;; verify each section has only a self HATEOAS link
        (doseq [section-key (map keyword (flatten (vals (:sections body))))]
          (count (:links (body section-key))) => 1
          (hateoas/verify-link "self" GET (section-rep/url (:slug r/open) section-key)
            section-rep/media-type (:links (body section-key))))
        ;; verify the company has only a self HATEOAS link
        (count (:links body)) => 1
        (hateoas/verify-link "self" GET (company-rep/url (:slug r/open)) company-rep/media-type (:links body))))

    (fact "that doesn't match the retrieving user's organization"
      ;; retrieve with Sartre (different org)
      (let [response (mock/api-request :get (company-rep/url r/open) {:auth mock/jwtoken-sartre})
            body (mock/body-from-response response)]
        (:status response) => 200
        (:sections body) =>
          {:company ["diversity" "values"], :financial ["finances"], :progress ["update" "team" "help"]}
        ;; verify each section has only a self HATEOAS link
        (doseq [section-key (map keyword (flatten (vals (:sections body))))]
          (count (:links (body section-key))) => 1)
        ;; verify the company has only a self HATEOAS link
        (count (:links body)) => 1
        (hateoas/verify-link "self" GET (company-rep/url (:slug r/open)) company-rep/media-type (:links body))))))