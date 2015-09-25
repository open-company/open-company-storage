(ns open-company.integration.report.report-update
  (:require [midje.sweet :refer :all]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.hateoas :as hateoas]
            [open-company.lib.resources :as r]
            [open-company.resources.company :as company]
            [open-company.resources.report :as report]
            [open-company.representations.report :as report-rep]))

;; ----- Test Cases -----

;; Updating reports with the REST API

;; The system should update reports and handle the following scenarios:

;; PUT
;; all good, updating a property - 200 OK
;; all good, adding a property - 200 OK
;; all good, removing a property - 200 OK
;; all good, no ticker symbol in body - 200 OK
;; all good, no year in the body - 200 OK
;; all good, no period in the body - 200 OK
;; all good, unicode in the body - 200 OK

;; bad, year has invalid characters
;; bad, year is outside of bounds
;; bad, period is invalid
;; bad, ticker symbol in body is different than the provided URL
;; bad, year in body is different than the provided URL
;; bad, priod in body is different than the provided URL

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

; (with-state-changes [(before :facts (do 
;                                       (company/delete-all-companies!)
;                                       (company/create-company r/OPEN)
;                                       (report/create-report r/OPEN-2015-Q2)))
;                      (after :facts (company/delete-all-companies!))]

;   (facts "about using the REST API to update reports"

;     ; all good, updating a property - 200 OK
;     (fact "when updating a property"
;       ;; Create the report
;       (let [updated-report (assoc-in r/OPEN [:finances :revenue] 0)
;            response (mock/put-report-with-api r/TICKER 2015 "Q2" updated-report)]
;         (:status response) => 200
;         (mock/response-mime-type response) => (mock/base-mime-type report-rep/media-type)
;         (mock/json? response) => true
;         (mock/body-from-response response) => (contains updated-report)
;         (hateoas/verify-report-links r/TICKER 2015 "Q2" (:links (mock/body-from-response response)))
;         ;; Get the updated report and make sure it's right
;         (report/get-report r/TICKER 2015 "Q2") => (contains updated-report))
;       ;; There is still 1 report?
;       (report/report-count r/TICKER) => 1)

;     ; all good, adding a property - 200 OK
;     (fact "when adding a property"
;       ;; Create the report
;       (let [updated-report (assoc-in r/OPEN [:headcount :executives] 0)
;            response (mock/put-report-with-api r/TICKER 2015 "Q2" updated-report)]
;         (:status response) => 200
;         (mock/response-mime-type response) => (mock/base-mime-type report-rep/media-type)
;         (mock/json? response) => true
;         (mock/body-from-response response) => (contains updated-report)
;         (hateoas/verify-report-links r/TICKER 2015 "Q2" (:links (mock/body-from-response response)))
;         ;; Get the updated report and make sure it's right
;         (report/get-report r/TICKER 2015 "Q2") => (contains updated-report))
;       ;; There is still 1 report?
;       (report/report-count r/TICKER) => 1)

;     ; all good, updating a property - 200 OK
;     (fact "when removing a property"
;       ;; Create the report
;       (let [updated-report (dissoc r/OPEN :compensation)
;            response (mock/put-report-with-api r/TICKER 2015 "Q2" updated-report)
;            body (mock/body-from-response response)]
;         (:status response) => 200
;         (mock/response-mime-type response) => (mock/base-mime-type report-rep/media-type)
;         (mock/json? response) => true
;         body => (contains updated-report)
;         (:compensation body) => nil
;         (hateoas/verify-report-links r/TICKER 2015 "Q2" (:links (mock/body-from-response response)))
;         ;; Get the updated report and make sure it's right
;         (let [retrieved-report (report/get-report r/TICKER 2015 "Q2")]
;           retrieved-report => (contains updated-report)
;           (:compensation retrieved-report) => nil))
;       ;; There is still 1 report?
;       (report/report-count r/TICKER) => 1)))