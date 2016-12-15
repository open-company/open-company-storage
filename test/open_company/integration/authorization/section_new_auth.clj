(ns open-company.integration.authorization.section-new-auth
  (:require [clojure.walk :refer (keywordize-keys)]
            [midje.sweet :refer :all]
            [cheshire.core :as json]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.resources :as r]
            [oc.lib.rethinkdb.pool :as pool]
            [open-company.lib.test-setup :as ts]
            [open-company.config :as config]
            [open-company.api.common :as common]
            [open-company.resources.company :as company]
            [open-company.representations.company :as company-rep]))

;; ----- Test Cases -----

;; GETing a new section template with the REST API

;; The system should return a collection of section templates and handle the following scenarios:

;; OPTIONS

;; fail - invalid JWToken - 401 Unauthorized
;; fail - no JWToken - 401 Unauthorized
;; fail - organization doesn't match companies - 403 Forbidden
;; fail - no matching company slug - 404 Not Found
;; success - matching JWToken - 204 No Content

;; GET

;; fail - invalid JWToken - 401 Unauthorized
;; fail - no JWToken - 401 Unauthorized
;; fail - organization doesn't match companies - 403 Forbidden
;; fail - no matching company slug - 404 Not Found
;; success - matching JWToken - 200 OK

;; ----- Tests -----

(def url (str (company-rep/url r/slug) "/section/new"))

(with-state-changes [(before :contents (ts/setup-system!))
                     (after :contents (ts/teardown-system!))
                     (before :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                      (company/delete-all-companies! conn)
                                      (company/create-company! conn (company/->company r/open r/coyote))))
                     (after :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)] (company/delete-all-companies! conn)))]

  (facts "about available options for new section templates"

    (fact "with a bad JWToken"
      (let [response (mock/api-request :options url {:auth mock/jwtoken-bad})]
        (:status response) => 401
        (:body response) => common/unauthorized))

    (fact "with no JWToken"
      (let [response (mock/api-request :options url {:skip-auth true})]
        (:status response) => 401
        (:body response) => common/unauthorized))

    (fact "with an organization that doesn't match the company"
      (let [response (mock/api-request :options url {:auth mock/jwtoken-sartre})]
        (:status response) => 403
        (:body response) => common/forbidden))

    (fact "with no company matching the company slug"
      (let [response (mock/api-request :options (str (company-rep/url "foo") "/section/new"))]
        (:status response) => 404
        (:body response) => ""))

    (fact "with a user's org in a JWToken that matches the company's org"
      (let [response (mock/api-request :options url)]
        (:status response) => 204
        (:body response) => ""
        ((:headers response) "Allow") => "OPTIONS, GET")))

  (facts "about failing to get new section templates"

    (fact "with a bad JWToken"
      (let [response (mock/api-request :get url {:auth mock/jwtoken-bad})]
        (:status response) => 401
        (:body response) => common/unauthorized))

    (fact "without a JWToken"
      (let [response (mock/api-request :get url {:skip-auth true})]
        (:status response) => 401
        (:body response) => common/unauthorized))

    (fact "with an organization that doesn't match the company"
      (let [response (mock/api-request :get url {:auth mock/jwtoken-sartre})]
        (:status response) => 403
        (:body response) => common/forbidden))

    (fact "with no company matching the company slug"
      (let [response (mock/api-request :get (str (company-rep/url "foo") "/section/new"))]
        (:status response) => 404
        (:body response) => "")))

  (fact "about getting new section templates"

    (fact "with a user's org in a JWToken that matches the company's org"
      (let [response (mock/api-request :get url)]
        (:status response) => 200))))