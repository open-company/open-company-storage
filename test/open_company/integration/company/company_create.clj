(ns open-company.integration.company.company-create
  (:require [midje.sweet :refer :all]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.hateoas :as hateoas]
            [open-company.resources.company :as company]
            [open-company.representations.company :as company-rep]
            [open-company.resources.report :as report]))

;; ----- Test Cases -----

;; Creating companies with the REST API

;; The system should store newly created valid companies and handle the following scenarios:

;; PUT
;; all good - 201 Created
;; all good, no ticker symbol in body - 201 Created
;; all good, unicode in the body - 201 Created

;; bad, ticker symbol has invalid characters
;; bad, ticker symbol is too long
;; bad, ticker symbol in body is different than the provided URL

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

;; ----- Test Data -----

(def TICKER "OPEN")
(def OPEN {:symbol TICKER :name "Transparency, LLC" :url "https://opencompany.io/"})
(def UNI {:symbol TICKER :name "$€¥£ &‼⁇ ∆∰≈ ☃♔☂Ǽ ḈĐĦ, LLC" :url "https://opencompany.io/"})

;; ----- Utilities -----

(defn- create-company-with-api
  "Makes an API request to create the company and returns the response."  
  ([ticker body]
     (mock/api-request :put (str "/v1/companies/" ticker) {
      :headers {
        :Accept-Charset "utf-8"
        :Accept company-rep/media-type
        :Content-Type company-rep/media-type}
      :body body}))
  ([headers ticker body]
     (mock/api-request :put (str "/v1/companies/" ticker) {
        :headers headers
        :body body})))

;; ----- Tests -----
(with-state-changes [(before :facts (company/delete-all!))
                     (after :facts (company/delete-all!))]

  (facts "about using the REST API to create valid new companies"

    (comment 
      all good - 201 Created
      curl -i -X PUT \
      -d "{symbol: 'OPEN', name: 'Transparency, LLC', url: 'https://opencompany.io/'}" \
      --header "Accept: application/vnd.open-company.company+json;version=1" \
      --header "Accept-Charset: utf-8" \
      --header "Content-Type: application/vnd.open-company.company+json;version=1" \
      http://localhost:3000/v1/companies/OPEN
    )
    (fact "when the ticker symbol in the body and the URL match"
      ;; Create the company
      (let [response (create-company-with-api TICKER OPEN)]
        (:status response) => 201
        (mock/response-mime-type response) => (mock/base-mime-type company-rep/media-type)
        (mock/response-location response) => "/v1/companies/OPEN"
        (mock/json? response) => true
        (hateoas/verify-company-links TICKER (:links (mock/body-from-response response))))
      ;; Get the created company and make sure it's right
      (company/get-company TICKER) => (contains OPEN)
      ;; Reports are empty?
      (report/report-count TICKER) => 0)))