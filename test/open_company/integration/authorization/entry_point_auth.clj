(ns open-company.integration.authorization.entry-point-auth
  "Authorization tests for the API HATEOAS entry point."
  (:require [midje.sweet :refer :all]
            [open-company.lib.test-setup :as ts]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.api.common :as common]))

(def options "OPTIONS, GET")

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

(with-state-changes [(before :contents (ts/setup-system!))
                     (after :contents (ts/teardown-system!))]

  (facts "about OPTIONS on the API entry point"

    (fact "with a bad JWToken"
      (let [response (mock/api-request :options "/" {:auth mock/jwtoken-bad})]
        (:status response) => 401
        (:body response) => common/unauthorized)))

    (fact "with no JWToken"
      (let [response (mock/api-request :options "/" {:skip-auth true})]
        (:status response) => 204
        (:body response) => ""
        ((:headers response) "Allow") => options))
   
    (fact "with a valid JWToken"
      (let [response (mock/api-request :options "/")]
        (:status response) => 204
        (:body response) => ""
        ((:headers response) "Allow") => options))

  (facts "about GET on the API entry point"

    (fact "with a bad JWToken"
      (let [response (mock/api-request :get "/" {:auth mock/jwtoken-bad})]
        (:status response) => 401
        (:body response) => common/unauthorized))

    (fact "with no JWToken"
      (let [response (mock/api-request :get "/" {:skip-auth true})]
        (:status response) => 200))

    (fact "with a valid JWToken"
      (let [response (mock/api-request :get "/")]
        (:status response) => 200))))