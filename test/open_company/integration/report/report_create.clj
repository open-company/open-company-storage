(ns open-company.integration.report.report-create
  (:require [midje.sweet :refer :all]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.hateoas :as hateoas]
            [open-company.lib.resources :as r]
            [open-company.resources.company :as company]
            [open-company.resources.report :as report]
            [open-company.representations.report :as report-rep]))

;; ----- Test Cases -----

;; Creating reports with the REST API

;; The system should store newly created valid reports and handle the following scenarios:

;; PUT
;; all good - 201 Created
;; all good, no ticker symbol in body - 201 Created
;; all good, no year in the body - 201 Created
;; all good, no period in the body - 201 Created
;; all good, unicode in the body - 201 Created

;; bad, year has invalid characters
;; bad, year is outside of bounds
;; bad, period is invalid
;; bad, ticker symbol in body is different than the provided URL
;; bad, year in body is different than the provided URL
;; bad, priod in body is different than the provided URL

;; good, no accept - 201 Created
;; good, no content type - 201 Created
;; good, no charset - 201 Created

;; bad, conflicting reserved properties
;; bad, wrong accept
;; bad, wrong content type
;; bad, no body
;; bad, wrong charset
;; bad, body not valid JSON

;; ----- Tests -----

(with-state-changes [(before :facts (do (company/delete-all-companies!)(company/create-company r/OPEN)))
                     (after :facts (company/delete-all-companies!))]

  (facts "about using the REST API to create valid new reports"

    ; all good - 201 Created
    (fact "when the ticker symbol in the body and the URL match"
      ;; Create the report
      (let [response (mock/put-report-with-api r/TICKER 2015 "Q2" r/OPEN-2015-Q2)]
        (:status response) => 201
        (mock/response-mime-type response) => (mock/base-mime-type report-rep/media-type)
        (mock/response-location response) => (report-rep/url r/TICKER 2015 "Q2")
        (mock/json? response) => true
        (hateoas/verify-report-links r/TICKER 2015 "Q2" (:links (mock/body-from-response response))))
      ;; Get the created report and make sure it's right
      (report/get-report r/TICKER 2015 "Q2") => (contains r/OPEN-2015-Q2)
      ;; There is 1 report?
      (report/report-count r/TICKER) => 1)))