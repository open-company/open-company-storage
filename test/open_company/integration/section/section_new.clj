  (ns open-company.integration.section.section-new
    (:require [clojure.walk :refer (keywordize-keys)]
              [midje.sweet :refer :all]
              [cheshire.core :as json]
              [open-company.lib.rest-api-mock :as mock]
              [open-company.lib.resources :as r]
              [open-company.lib.test-setup :as ts]
              [open-company.lib.hateoas :as hateoas]
              [oc.lib.db.pool :as pool]
              [open-company.resources.company :as company]
              [open-company.representations.common :refer (PUT)]
              [open-company.representations.company :as company-rep]
              [open-company.representations.section :as section-rep]
              [open-company.config :as config]))

;; ----- Test Cases -----

;; The system should return a collection of section templates and handle the following scenarios:

;; GET

;; success - 200 OK

;; ----- Tests -----

(def url (str (company-rep/url r/slug) "/section/new"))

(with-state-changes [(before :contents (ts/setup-system!))
                     (before :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                      (company/delete-all-companies! conn)
                                      (company/create-company! conn (company/->company r/open r/coyote))))
                     (after :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                     (company/delete-all-companies! conn)))
                     (after :contents (ts/teardown-system!))]

  (fact "about getting new section templates"
    (let [response (mock/api-request :get url)
          response-body (keywordize-keys (json/decode (:body response)))
          response-templates (:templates response-body)
          config-templates (:templates config/sections)]
      (:status response) => 200
      (:categories response-body) => (:categories config/sections)
      ;; check templates
      (count response-templates) => (count config-templates) ; same #
      (doseq [i (range 0 (- (count response-templates) 1))]
        (let [response-template (nth response-templates i)
              links (:links response-template)
              section-name (:section-name response-template)]
          ;; each has same contents
          response-template => (contains (nth config-templates i))
          ;; each has correct link
          (count links) => 1
          (hateoas/verify-link "create" PUT (section-rep/url r/slug section-name) section-rep/media-type links))))))