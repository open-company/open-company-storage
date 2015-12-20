(ns open-company.integration.section.section-new
  (:require [midje.sweet :refer :all]
            [cheshire.core :as json]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.config :as config]
            [open-company.api.common :as common]
            [open-company.resources.company :as company]
            [open-company.representations.company :as company-rep]))

;; ----- Startup -----

(db/test-startup)

;; ----- Test Cases -----

;; GETing a new section template with the REST API

;; The system should return a collection of section templates and handle the following scenarios:

;; GET

;; bad - invalid JWToken - 401 Unauthorized
;; bad - no JWToken - 401 Unauthorized
;; bad - organization doesn't match companies - 403 Forbidden

;; bad - no matching company slug - 404 Not Found

;; all good - matching JWToken - 200 OK

;; TODO
;; no accept
;; no content type
;; no charset
;; wrong accept
;; wrong content type
;; wrong charset

;; ----- Tests -----

(with-state-changes [(before :facts (do
                                      (company/delete-all-companies!)
                                      (company/create-company r/open r/coyote)))
                     (after :facts (company/delete-all-companies!))]

  (fact "about failing to get new section templates"

    (fact "with a bad JWToken"
      (let [response (mock/api-request :get (str (company-rep/url r/slug) "/section/new") {:auth mock/jwtoken-bad})]
        (:status response) => 401
        (:body response) => common/unauthorized))

    (fact "without a JWToken"
      (let [response (mock/api-request :get (str (company-rep/url r/slug) "/section/new") {:skip-auth true})]
        (:status response) => 401
        (:body response) => common/unauthorized))

    (fact "with an organization that doesn't match the company"
      (let [response (mock/api-request :get (str (company-rep/url r/slug) "/section/new") {:auth mock/jwtoken-sartre})]
        (println (:headers response))
        (:status response) => 403
        (:body response) => common/forbidden))

    (fact "with no company matching the company slug"
      (let [response (mock/api-request :get (str (company-rep/url "foo") "/section/new"))]
        (:status response) => 404
        (:body response) => "")))

  (fact "about getting new section templates"

    (fact "with a user's org in a JWToken that matches the company's org"
      (let [response (mock/api-request :get (str (company-rep/url r/slug) "/section/new"))]
        (:status response) => 200
        (json/decode (:body response)) => config/sections))))