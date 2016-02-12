(ns open-company.integration.company.company-create
  (:require [cheshire.core :as json]
            [schema.test]
            [midje.sweet :refer :all]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.hateoas :as hateoas]
            [open-company.lib.resources :as r]
            [open-company.resources.company :as company]
            [open-company.resources.section :as section]
            [open-company.representations.company :as company-rep]))

;; ----- Test Cases -----

;; Creating companies with the REST API

;; The system should store newly created valid companies and handle the following scenarios:

;; OPTIONS

;; PUT / POST
;; all good - 201 Created
;; all good, no ticker symbol in body - 201 Created
;; all good, unicode in the body - 201 Created

;; bad, no JWToken - 401 Unauthorized
;; bad, invalid JWToken - 401 Unauthorized

;; bad, company slug has invalid characters
;; bad, company slug is too long
;; bad, company slug in body is different than the provided URL

;; no accept
;; no content type
;; no charset
;; conflicting reserved properties
;; wrong accept
;; wrong content type
;; no body
;; wrong charset
;; body not valid JSON
;; no name in body

;; ----- Tests -----

; (with-state-changes [(before :facts (company/delete-all-companies!))
;                      (after :facts (company/delete-all-companies!))]

;   (facts "about using the REST API to create valid new companies"

;     ;; all good - 201 Created
;     (fact "when the ticker symbol in the body and the URL match"
;       ;; Create the company
;       (let [response (mock/put-company-with-api r/TICKER r/OPEN)]
;         (:status response) => 201
;         (mock/response-mime-type response) => (mock/base-mime-type company-rep/media-type)
;         (mock/response-location response) => (company-rep/url r/TICKER)
;         (mock/json? response) => true
;         (hateoas/verify-company-links r/TICKER (:links (mock/body-from-response response))))
;       ;; Get the created company and make sure it's right
;       (company/get-company r/TICKER) => (contains r/OPEN)
;       ;; Reports are empty?
;       (report/report-count r/TICKER) => 0)))
(with-state-changes [(before :facts (do (company/delete-all-companies!)
                                        (company/create-company! (company/->company r/open r/coyote))))]
  ;; -------------------
  ;; JWToken things are covered in open-company.integration.company.company-list
  ;; -------------------
  (facts "about creating companies"

    (fact "missing fields cause 422"
      (let [payload  {:name "hello"}
            response (mock/api-request :post "/companies" {:body payload})]
        (:status response) => 422))
    (fact "superflous fields cause 422"
      (let [payload  {:bogus "xx" :name "hello" :description "x"}
            response (mock/api-request :post "/companies" {:body payload})]
        (:status response) => 422))

    (fact "company can be created with name and description"
      (let [payload  {:name "Hello World" :description "x"}
            response (mock/api-request :post "/companies" {:body payload})
            body     (mock/body-from-response response)]
        (:status response) => 201
        (:slug body) => "hello-world"
        (:description body) => "x"
        (:name body) => "Hello World"))

    (facts "slug"
      (fact "provided slug is taken causes 422"
        (let [payload  {:slug "open" :name "hello" :description "x"}
              response (mock/api-request :post "/companies" {:body payload})]
          (:status response) => 422))
      (fact "provided slug does not follow slug format causes 422"
        (let [payload  {:slug "under_score" :name "hello" :description "x"}
              response (mock/api-request :post "/companies" {:body payload})]
          (:status response) => 422)))

    (facts "sections"
      (fact "unknown sections cause 422"
       (let [payload  {:name "hello" :description "x" :unknown-section {}}
              response (mock/api-request :post "/companies" {:body payload})]
         (:status response) => 422))
      (facts "known user supplied sections"
        (let [diversity {:title "Diversity" :description "Diversity is important to us." :body "TBD" :section-name "diversity"}
              payload  {:name "Diverse Co" :description "x" :diversity diversity}
              response (mock/api-request :post "/companies" {:body payload})
              company  (company/get-company "diverse-co")
              sect     (section/get-section "diverse-co" :diversity)]
          (fact "are added with author and updated-at"
            (:status response) => 201
            (-> company :diversity :placeholder) => falsey
            (-> company :diversity :author) => truthy
            (-> company :diversity :updated-at) => truthy
            (:description sect) => "Diversity is important to us."))))))