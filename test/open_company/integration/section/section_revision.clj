(ns open-company.integration.section.section-revision
  (:require [midje.sweet :refer :all]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.api.common :as common]
            [open-company.resources.company :as c]
            [open-company.resources.section :as s]
            [open-company.representations.section :as section-rep]))

;; ----- Startup -----

(db/test-startup)

;; ----- Test Cases -----

;; PUTing and PATCHing a section with the REST API.

;; The system should support PATCHing a section, and handle the following scenarios:

;; fail - invalid JWToken - 401 Unauthorized
;; fail - no org-id in JWToken - 401 Unauthorized
;; fail - organization doesn't match companies - 403 Forbidden

;; fail - no matching company slug - 404 Not Found
;; fail - no matching section slug - 404 Not Found

;; TODO
;; success - update existing revision title
;; success - update existing revision body
;; success - update existing revision note
;; success - update existing revision title, body, and note

;; TODO
;; success - create new revision with an updated title
;; success - create new revision with an updated body
;; success - create new revision with a new note
;; success - create new revision with an updated note
;; success - create new revision with a removed note
;; success - create new revision with an updated title, body, and note

;; TODO
;; no accept
;; no content type
;; no charset
;; wrong accept
;; wrong content type
;; wrong charset

;; ----- Tests -----

(with-state-changes [(before :facts (do (c/delete-company r/slug)
                                        (c/create-company r/open r/coyote)))
                     (after :facts (c/delete-company r/slug))]

  (with-state-changes [(before :facts (s/put-section r/slug :update r/text-section-1 r/coyote))]

        
    (facts "about failing to update a section"

      (doseq [method [:put :patch]]
        
        (fact "with an invalid JWToken"
          (let [response (mock/api-request method (section-rep/url r/slug :update) {:body r/text-section-2
                                                                                    :auth mock/jwtoken-bad})]
            (:status response) => 401
            (:body response) => common/unauthorized)
          ;; verify the initial section is unchanged
          (s/get-section r/slug :update) => (contains r/text-section-1)
          (count (s/get-revisions r/slug :update)) => 1)

        (fact "with no JWToken"
          (let [response (mock/api-request method (section-rep/url r/slug :update) {:body r/text-section-2
                                                                                    :skip-auth true})]
            (:status response) => 401
            (:body response) => common/unauthorized)
          ;; verify the initial section is unchanged
          (s/get-section r/slug :update) => (contains r/text-section-1)
          (count (s/get-revisions r/slug :update)) => 1)

        (fact "with an organization that doesn't match the company"
          (let [response (mock/api-request method (section-rep/url r/slug :update) {:body r/text-section-2
                                                                                    :auth mock/jwtoken-sartre})]
            (:status response) => 403
            (:body response) => common/forbidden)
          ;; verify the initial section is unchanged
          (s/get-section r/slug :update) => (contains r/text-section-1)
          (count (s/get-revisions r/slug :update)) => 1)

        (fact "with no company matching the company slug"
          (let [response (mock/api-request method (section-rep/url "foo" :update) {:body r/text-section-2})]
            (:status response) => 404
            (:body response) => "")
          ;; verify the initial section is unchanged
          (s/get-section r/slug :update) => (contains r/text-section-1)
          (count (s/get-revisions r/slug :update)) => 1)

        (fact "with no section matching the section slug"
          (let [response (mock/api-request method (section-rep/url r/slug :finances) {:body r/text-section-2})]
            (:status response) => 404
            (:body response) => "")
          ;; verify the initial section is unchanged
          (s/get-section r/slug :update) => (contains r/text-section-1)
          (count (s/get-revisions r/slug :update)) => 1)))))