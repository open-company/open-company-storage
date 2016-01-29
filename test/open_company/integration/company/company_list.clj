(ns open-company.integration.company.company-list
  (:require [midje.sweet :refer :all]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.hateoas :as hateoas]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.lib.slugify :refer (slugify)]
            [open-company.api.common :as common]
            [open-company.resources.company :as company]
            [open-company.representations.company :as company-rep]))

;; ----- Startup -----

(db/test-startup)

;; ----- Test Cases -----

;; GETing a company list with the REST API

;; The system should return a collection of companies and handle the following scenarios:

;; OPTIONS

;; fail - invalid JWToken - 401 Unauthorized

;; success - no JWToken - 204 No Content
;; success - valid JWToken - 204 No Content

;; GET

;; fail - invalid JWToken - 401 Unauthorized

;; success - no JWToken - 200 OK
;; success - valid JWToken - 200 OK

;; TODO
;; no accept
;; no content type
;; no charset
;; wrong accept
;; wrong content type
;; wrong charset

;; ----- Tests -----

(def options  "OPTIONS, GET")
(def authenticated-options  "OPTIONS, GET, POST")

(with-state-changes [(before :facts (do
                                      (company/delete-all-companies!)
                                      (company/create-company r/open r/coyote)
                                      (company/create-company r/uni r/camus)
                                      (company/create-company r/buffer r/sartre)))
                     (after :facts (company/delete-all-companies!))]

  (facts "about available options in listing companies"

    (fact "with a bad JWToken"
      (let [response (mock/api-request :options "/companies" {:auth mock/jwtoken-bad})]
        (:status response) => 401
        (:body response) => common/unauthorized))

    (fact "with no JWToken"
      (let [response (mock/api-request :options "/companies" {:skip-auth true})]
        (:status response) => 204
        (:body response) => ""
        ((:headers response) "Allow") => options))

    (fact "with a valid JWToken"
      (let [response (mock/api-request :options "/companies")]
        (:status response) => 204
        (:body response) => ""
        ((:headers response) "Allow") => authenticated-options)))

  (fact "about failing to list companies"

    (fact "with a bad JWToken"
      (let [response (mock/api-request :get "/companies" {:auth mock/jwtoken-bad})]
        (:status response) => 401
        (:body response) => common/unauthorized)))

  (facts "about listing companies"

    (fact "all existing companies are listed anonymously with NO JWToken"
      (let [response (mock/api-request :get "/companies" {:skip-auth true})
            body (mock/body-from-response response)
            companies (get-in body [:collection :companies])]
        (:status response) => 200
        (map :name companies) => (just (set (map :name [r/open r/uni r/buffer]))) ; verify names
        (map :slug companies) => (just (set [(:slug r/open) (slugify (:name r/uni)) (:slug r/buffer)])) ; verify slugs
        (doseq [company companies] ; verify HATEOAS links
          (count (:links company)) => 1
          (hateoas/verify-link "self" "GET" (company-rep/url company) company-rep/media-type (:links company)))))

    (fact "all existing companies are listed when providing a JWToken"
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