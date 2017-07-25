(ns open-company.integration.authorization.company-list-auth
  "Authorization tests for the company listing API."
  (:require [midje.sweet :refer :all]
            [open-company.lib.test-setup :as ts]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.hateoas :as hateoas]
            [open-company.api.common :as common]))

;; ----- Test Cases -----

;; OPTIONS
;; fail - invalid JWToken - 401 Unauthorized
;; success - no JWToken - 204 No Content
;; success - with JWToken - 204 No Content

;; GET
;; fail - invalid JWToken - 401 Unauthorized
;; success - no JWToken - 200 OK
;; success - with JWToken - 200 OK

;; ----- Tests -----

(def options (hateoas/options-response [:options :get]))

(with-state-changes [(before :contents (ts/setup-system!))
                     (after :contents (ts/teardown-system!))]

  (facts "about failing to access the API entry point"

    (doseq [method [:options :get]]
  
      (fact "with a bad JWToken"
        (let [response (mock/api-request method "/" {:auth mock/jwtoken-bad})]
          (:status response) => 401
          (:body response) => common/unauthorized))))

  (fact "about OPTIONS access on the API entry point"

    (fact "with no JWToken"
      (let [response (mock/api-request :options "/" {:skip-auth true})]
        (:status response) => 204
        (:body response) => ""
        ((:headers response) "Allow") => options))

    (fact "with a valid JWToken"
      (let [response (mock/api-request :options "/")]
        (:status response) => 204
        (:body response) => ""
        ((:headers response) "Allow") => options)))

  (fact "about GET access on the API entry point"

    (fact "with no JWToken"
      (let [response (mock/api-request :get "/" {:skip-auth true})]
        (:status response) => 200))

    (fact "with a valid JWToken"
      (let [response (mock/api-request :get "/")]
        (:status response) => 200))))