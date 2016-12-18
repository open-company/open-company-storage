(ns open-company.integration.authorization.company-auth
  "Authorization tests for the company API."
  (:require [midje.sweet :refer :all]
            [open-company.lib.test-setup :as ts]
            [oc.lib.rethinkdb.pool :as pool]
            [open-company.lib.resources :as r]
            [oc.lib.slugify :refer (slugify)]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.hateoas :as hateoas]
            [open-company.api.common :as common]
            [open-company.resources.company :as company]
            [open-company.representations.company :as company-rep]))

; ;; ----- Test Cases -----

;; Identified combinatorial variation...
;; Method: OPTIONS, GET, PATCH, DELETE
;; Company scope: public, private
;; User token: none, invalid, same org, different org

;; OPTIONS, GET, PATCH, DELETE
;; fail - invalid JWToken - 401 Unauthorized

;; OPTIONS, GET, PATCH, DELETE - private company
;; fail - no JWToken - 401 Unauthorized
;; fail - other org JWToken - 403 Forbidden

;; OPTIONS - private company
;; success - with matching JWToken - 204 No Content

;; OPTIONS - public company
;; success - no JWToken - 204 No Content
;; success - other org JWToken - 204 No Content

;; GET - private company
;; success - with matching JWToken - 200 OK

;; GET - public company
;; success - no JWToken - 200 OK
;; success - other org JWToken - 200 OK

;; PATCH - private company
;; success - with matching JWToken - 200 OK

;; PATCH - public company
;; fail - no JWToken - 401 Unauthorized
;; fail - other org JWToken - 403 Forbidden

;; DELETE - private company
;; success - with matching JWToken - 204 No Content

;; DELETE - public company
;; fail - no JWToken - 401 Unauthorized
;; fail - other org JWToken - 403 Forbidden

;; ----- Tests -----

(def limited-options (hateoas/options-response [:options :get]))
(def full-options (hateoas/options-response [:options :get :patch :delete]))

(with-state-changes [(before :contents (ts/setup-system!))
                     (before :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                      (company/delete-all-companies! conn)
                                      ;; 3 companies: private same org, private different org, public different org
                                      (company/create-company! conn (company/->company r/open r/coyote))
                                      (company/create-company! conn (company/->company (assoc r/uni :public false)
                                                                      r/sartre (slugify (:name r/uni))))
                                      (company/create-company! conn (company/->company (assoc r/buffer :public true)
                                                                      r/sartre))))
                     (after :contents (ts/teardown-system!))]

  (facts "about accessing the company API"

    (doseq [method [:options :get :patch :delete]]

      (facts "with a bad JWToken"
        (let [response (mock/api-request method (company-rep/url r/slug) {:auth mock/jwtoken-bad})]
          (:status response) => 401
          (:body response) => common/unauthorized))

      (facts "on a private company"
 
        (fact "with no JWToken"
          (let [response (mock/api-request method (company-rep/url r/slug) {:skip-auth true})]
          (:status response) => 401
          (:body response) => common/unauthorized))

        (fact "with a JWToken from a different org"
          (let [response (mock/api-request method (company-rep/url (slugify (:name r/uni))))]
            (:status response) => 403
            (:body response) => common/forbidden))))

    (facts "with OPTIONS"

      (fact "with a JWToken from the same org"
        (let [response (mock/api-request :options (company-rep/url r/slug))]
          (:status response) => 204
          (:body response) => ""
          ((:headers response) "Allow") => full-options))

      (facts "on a public company"

        (fact "with no JWToken"
          (let [response (mock/api-request :options (company-rep/url (:slug r/buffer)) {:skip-auth true})]
            (:status response) => 204
            (:body response) => ""
            ((:headers response) "Allow") => limited-options))

        (fact "with a JWToken from a different org"
          (let [response (mock/api-request :options (company-rep/url (:slug r/buffer)))]
            (:status response) => 204
            (:body response) => ""
            ((:headers response) "Allow") => limited-options))))

    (facts "with GET"

      (fact "with a JWToken from the same org"
        (let [response (mock/api-request :get (company-rep/url r/slug))]
          (:status response) => 200))

      (facts "on a public company"

        (fact "with no JWToken"
          (let [response (mock/api-request :get (company-rep/url (:slug r/buffer)) {:skip-auth true})]
            (:status response) => 200))

        (fact "with a JWToken from a different org"
          (let [response (mock/api-request :get (company-rep/url (:slug r/buffer)))]
            (:status response) => 200))))

    (facts "with PATCH"

      (fact "with a JWToken from the same org"
        (let [response (mock/api-request :patch (company-rep/url r/slug) {:body {:currency "FKP"}})]
          (:status response) => 200))

      (facts "on a public company"

        (fact "with no JWToken"
          (let [response (mock/api-request :patch (company-rep/url (:slug r/buffer)) {:body {:currency "FKP"}
                                                                                      :skip-auth true})]
            (:status response) => 401
            (:body response) => common/unauthorized))

        (fact "with a JWToken from a different org"
          (let [response (mock/api-request :patch (company-rep/url (:slug r/buffer)) {:body {:currency "FKP"}})]
            (:status response) => 403
            (:body response) => common/forbidden))))

    (facts "with DELETE"

      (fact "with a JWToken from the same org"
        (let [response (mock/api-request :delete (company-rep/url r/slug))]
          (:status response) => 204))

      (facts "on a public company"

        (fact "with no JWToken"
          (let [response (mock/api-request :delete (company-rep/url (:slug r/buffer)) {:skip-auth true})]
            (:status response) => 401
            (:body response) => common/unauthorized))

        (fact "with a JWToken from a different org"
          (let [response (mock/api-request :delete (company-rep/url (:slug r/buffer)))]
            (:status response) => 403
            (:body response) => common/forbidden))))))