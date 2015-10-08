(ns open-company.integration.company.company-update
  (:require [midje.sweet :refer :all]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.hateoas :as hateoas]
            [open-company.lib.resources :as r]
            [open-company.resources.company :as company]
            [open-company.representations.company :as company-rep]))

;; ----- Test Cases -----

;; Updating companies with the REST API

;; The system should update valid companies and handle the following scenarios:

;; PUT
;; all good, updating a property - 200 OK
;; all good, adding a property - 200 OK
;; all good, removing a property - 200 OK
;; all good, no ticker symbol in body - 200 OK
;; all good, unicode in the body - 200 OK

;; bad, ticker symbol has invalid characters
;; bad, ticker symbol is too long
;; bad, ticker symbol in body is different than the provided URL

;; good, no accept - 200 OK
;; good, no content type - 200 OK
;; good, no charset - 200 OK

;; bad, conflicting reserved properties
;; bad, wrong accept
;; bad, wrong content type
;; bad, no body
;; bad, wrong charset
;; bad, body not valid JSON

;; ----- Tests -----

; (with-state-changes [(before :facts (do (company/delete-all-companies!) (company/create-company r/OPEN)))
;                      (after :facts (company/delete-all-companies!))]

;   (facts "about using the REST API to update companies"

;     ;; all good - 201 Created
;     (fact "when updating a property"
;       ;; Update the company
;       (let [updated-company (assoc-in r/OPEN [:web :company] "https://opencompany.io")
;             response (mock/put-company-with-api r/TICKER updated-company)]
;         (:status response) => 200
;         (mock/response-mime-type response) => (mock/base-mime-type company-rep/media-type)
;         (mock/json? response) => true
;         (mock/body-from-response response) => (contains updated-company)
;         (hateoas/verify-company-links r/TICKER (:links (mock/body-from-response response)))
;         ;; Get the updated company and make sure it's right
;         (company/get-company r/TICKER) => (contains updated-company)))

;     ;; all good - 201 Created
;     (fact "when adding a property"
;       ;; Update the company
;       (let [updated-company (assoc-in r/OPEN [:web :about] "https://opencompany.io/about")
;             response (mock/put-company-with-api r/TICKER updated-company)]
;         (:status response) => 200
;         (mock/response-mime-type response) => (mock/base-mime-type company-rep/media-type)
;         (mock/json? response) => true
;         (mock/body-from-response response) => (contains updated-company)
;         (hateoas/verify-company-links r/TICKER (:links (mock/body-from-response response)))
;         ;; Get the updated company and make sure it's right
;         (company/get-company r/TICKER) => (contains updated-company)))

;     ;; all good - 201 Created
;     (fact "when removing a property"
;       ;; Update the company
;       (let [updated-company (update-in r/OPEN [:web] dissoc :company)
;             response (mock/put-company-with-api r/TICKER updated-company)
;             body (mock/body-from-response response)]
;         (:status response) => 200
;         (mock/response-mime-type response) => (mock/base-mime-type company-rep/media-type)
;         (mock/json? response) => true
;         body => (contains updated-company)
;         (get-in body [:web :company]) => nil
;         (hateoas/verify-company-links r/TICKER (:links (mock/body-from-response response)))
;         ;; Get the updated company and make sure it's right
;         (let [retrieved-company (company/get-company r/TICKER)]
;            retrieved-company => (contains updated-company)
;            (get-in retrieved-company [:web :company]) => nil)))))