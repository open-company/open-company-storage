  (ns open-company.integration.authorization.section-new
  (:require [clojure.walk :refer (keywordize-keys)]
            [midje.sweet :refer :all]
            [cheshire.core :as json]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.resources :as r]
            [open-company.lib.test-setup :as ts]
            [open-company.config :as config]]))

;; ----- Test Cases -----

;; The system should return a collection of section templates and handle the following scenarios:

;; GET

;; success - 200 OK

;; ----- Tests -----

(with-state-changes [(before :contents (ts/setup-system!))
                     (after :contents (ts/teardown-system!))]

  (fact "about getting new section templates"
    (let [response (mock/api-request :get "/sections/new")]
      (:status response) => 200
      (keywordize-keys (json/decode (:body response))) => config/sections)))