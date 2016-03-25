(ns open-company.integration.section.section-revision
  (:require [midje.sweet :refer :all]
            [cheshire.core :as json]
            [open-company.lib.check :as check]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.db.pool :as pool]
            [open-company.lib.test-setup :as ts]
            [open-company.api.common :as common]
            [open-company.resources.company :as c]
            [open-company.resources.section :as s]
            [open-company.representations.section :as section-rep]))

;; ----- Test Cases -----

;; OPTIONS

;; fail - invalid JWToken - 401 Unauthorized
;; fail - no matching company slug - 404 Not Found
;; fail - no matching section slug - 404 Not Found

;; success - no JWToken - 204 No Content
;; success - organization doesn't match companies - 204 No Content
;; success - matching JWToken - 204 No Content

;; PUT/PATCH a section with the REST API.

;; The system should support PATCHing a section, and handle the following scenarios:

;; fail - invalid JWToken - 401 Unauthorized
;; fail - no org-id in JWToken - 401 Unauthorized
;; fail - organization doesn't match companies - 403 Forbidden

;; fail - no matching company slug - 404 Not Found
;; fail - no matching section slug - 404 Not Found

;; PUT/PATCH
;; success - update existing revision title
;; success - update existing revision body
;; success - update existing revision note
;; success - update existing revision title, body, and note

;; TODO
;; PUT/PATCH
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

(def limited-options "OPTIONS, GET")
(def full-options "OPTIONS, GET, PUT, PATCH")

(with-state-changes [(around :facts (schema.core/with-fn-validation ?form))
                     (before :contents (ts/setup-system!))
                     (after :contents (ts/teardown-system!))
                     (before :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                      (c/delete-all-companies! conn)
                                      (c/create-company! conn (c/->company r/open r/coyote))))
                     (after :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                     (c/delete-all-companies! conn)))]

  (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
  (with-state-changes [(before :facts (s/put-section conn r/slug :update r/text-section-1 r/coyote))]

    (facts "about available options for new section revisions"

      (fact "with a bad JWToken"
        (let [response (mock/api-request :options (section-rep/url r/slug :update) {:auth mock/jwtoken-bad})]
          (:status response) => 401
          (:body response) => common/unauthorized))

      (fact "with no company matching the company slug"
        (let [response (mock/api-request :options (section-rep/url "foo" :update))]
          (:status response) => 404
          (:body response) => ""))

      (fact "with no section matching the section name"
        (let [response (mock/api-request :options (section-rep/url r/slug :diversity))]
          (:status response) => 404
          (:body response) => ""))

      (fact "with no JWToken"
        (let [response (mock/api-request :options (section-rep/url r/slug :update) {:skip-auth true})]
          (:status response) => 204
          (:body response) => ""
          ((:headers response) "Allow") => limited-options))

      (fact "with an organization that doesn't match the company"
        (let [response (mock/api-request :options (section-rep/url r/slug :update) {:auth mock/jwtoken-sartre})]
          (:status response) => 204
          (:body response) => ""
          ((:headers response) "Allow") => limited-options))

      (fact "with an organization that matches the company"
        (let [response (mock/api-request :options (section-rep/url r/slug :update))]
          (:status response) => 204
          (:body response) => ""
          ((:headers response) "Allow") => full-options)))

    (facts "about failing to update a section"

      (doseq [method [:put :patch]]

        (fact "with an invalid JWToken"
          (let [response (mock/api-request method (section-rep/url r/slug :update) {:body r/text-section-2
                                                                                    :auth mock/jwtoken-bad})]
            (:status response) => 401
            (:body response) => common/unauthorized)
          ;; verify the initial section is unchanged
          (s/get-section conn r/slug :update) => (contains r/text-section-1)
          (count (s/get-revisions conn r/slug :update)) => 1)

        (fact "with no JWToken"
          (let [response (mock/api-request method (section-rep/url r/slug :update) {:body r/text-section-2
                                                                                    :skip-auth true})]
            (:status response) => 401
            (:body response) => common/unauthorized)
          ;; verify the initial section is unchanged
          (s/get-section conn r/slug :update) => (contains r/text-section-1)
          (count (s/get-revisions conn r/slug :update)) => 1)

        (fact "with an organization that doesn't match the company"
          (let [response (mock/api-request method (section-rep/url r/slug :update) {:body r/text-section-2
                                                                                    :auth mock/jwtoken-sartre})]
            (:status response) => 403
            (:body response) => common/forbidden)
          ;; verify the initial section is unchanged
          (s/get-section conn r/slug :update) => (contains r/text-section-1)
          (count (s/get-revisions conn r/slug :update)) => 1)

        (fact "with no company matching the company slug"
          (let [response (mock/api-request method (section-rep/url "foo" :update) {:body r/text-section-2})]
            (:status response) => 404
            (:body response) => "")
          ;; verify the initial section is unchanged
          (s/get-section conn r/slug :update) => (contains r/text-section-1)
          (count (s/get-revisions conn r/slug :update)) => 1)

        (fact "with no section matching the section name"
          (let [response (mock/api-request method (section-rep/url r/slug :finances) {:body r/text-section-2})]
            (:status response) => 404
            (:body response) => "")
          ;; verify the initial section is unchanged
          (s/get-section conn r/slug :update) => (contains r/text-section-1)
          (count (s/get-revisions conn r/slug :update)) => 1))))

  (facts "about updating an existing section revision"

    (facts "with PUT"

      (with-state-changes [(before :facts (s/put-section conn r/slug :update r/text-section-1 r/coyote))]

        (fact "update existing revision title"
          (let [updated (assoc r/text-section-1 :title "New Title")
                response (mock/api-request :put (section-rep/url r/slug :update) {:body updated})
                body (mock/body-from-response response)
                updated-at (:updated-at body)]
            (:status response) => 200
            body => (contains updated)
            ;; verify the initial revision is changed
            (let [updated-section (s/get-section conn r/slug :update)]
              updated-section => (contains updated)
              (check/timestamp? updated-at) => true
              (check/about-now? updated-at) => true
              (check/before? (:created-at updated-section) updated-at) => true)
            (count (s/get-revisions conn r/slug :update)) => 1)) ; but there is still just 1 revision

        (fact "update existing revision body"
          (let [updated (assoc r/text-section-1 :body "New Body")
                response (mock/api-request :put (section-rep/url r/slug :update) {:body updated})
                body (mock/body-from-response response)
                updated-at (:updated-at body)]
            (:status response) => 200
            body => (contains updated)
            ;; verify the initial revision is changed
            (let [updated-section (s/get-section conn r/slug :update)]
              updated-section => (contains updated)
              (check/timestamp? updated-at) => true
              (check/about-now? updated-at) => true
              (check/before? (:created-at updated-section) updated-at) => true)
            (count (s/get-revisions conn r/slug :update)) => 1))) ; but there is still just 1 revision

      (with-state-changes [(before :facts (s/put-section conn r/slug :finances r/finances-section-1 r/coyote))]

        (fact "update existing revision note"
          (let [updated (assoc r/text-section-1 :note "New Note")
                response (mock/api-request :put (section-rep/url r/slug :finances) {:body updated})
                body (mock/body-from-response response)
                updated-at (:updated-at body)]
            (:status response) => 200
            body => (contains updated)
            ;; verify the initial revision is changed
            (let [updated-section (s/get-section conn r/slug :finances)]
              updated-section => (contains updated)
              (check/timestamp? updated-at) => true
              (check/about-now? updated-at) => true
              (check/before? (:created-at updated-section) updated-at) => true)
            (count (s/get-revisions conn r/slug :finances)) => 1)) ; but there is still just 1 revision

        (fact "update existing revision title, body and note"
          (let [updated {:body "New Body" :title "New Title" :note "New Note"}
                response (mock/api-request :put (section-rep/url r/slug :finances) {:body updated})
                body (mock/body-from-response response)
                updated-at (:updated-at body)]
            (:status response) => 200
            body => (contains updated)
            ;; verify the initial revision is changed
            (let [updated-section (s/get-section conn r/slug :finances)]
              updated-section => (contains updated)
              (check/timestamp? updated-at) => true
              (check/about-now? updated-at) => true
              (check/before? (:created-at updated-section) updated-at) => true)
            (count (s/get-revisions conn r/slug :finances)) => 1)))) ; but there is still just 1 revision

    (facts "with PATCH"

      (with-state-changes [(before :facts (s/put-section conn r/slug :update r/text-section-1 r/coyote))]

        (fact "update existing revision title"
          (let [updated {:title "New Title"}
                response (mock/api-request :patch (section-rep/url r/slug :update) {:body updated})
                body (mock/body-from-response response)
                updated-at (:updated-at body)
                updated-section (merge r/text-section-1 updated)]
            (:status response) => 200
            body => (contains updated-section)
            ;; verify the initial revision is changed
            (let [updated-section (s/get-section conn r/slug :update)]
              updated-section => (contains updated-section)
              (check/timestamp? updated-at) => true
              (check/about-now? updated-at) => true
              (check/before? (:created-at updated-section) updated-at) => true)
            (count (s/get-revisions conn r/slug :update)) => 1)) ; but there is still just 1 revision

        (fact "update existing revision body"
          (let [updated {:body "New Body"}
                response (mock/api-request :patch (section-rep/url r/slug :update) {:body updated})
                body (mock/body-from-response response)
                updated-at (:updated-at body)
                updated-section (merge r/text-section-1 updated)]
            (:status response) => 200
            body => (contains updated-section)
            ;; verify the initial revision is changed
            (let [updated-section (s/get-section conn r/slug :update)]
              updated-section => (contains updated-section)
              (check/timestamp? updated-at) => true
              (check/about-now? updated-at) => true
              (check/before? (:created-at updated-section) updated-at) => true)
            (count (s/get-revisions conn r/slug :update)) => 1))) ; but there is still just 1 revision

      (with-state-changes [(before :facts (s/put-section conn r/slug :finances r/finances-section-1 r/coyote))]

        (fact "update existing revision note"
          (let [updated {:note "New Note"}
                response (mock/api-request :patch (section-rep/url r/slug :finances) {:body updated})
                body (mock/body-from-response response)
                updated-at (:updated-at body)
                updated-section (merge r/finances-section-1 updated)]
            (:status response) => 200
            body => (contains updated-section)
            ;; verify the initial revision is changed
            (let [updated-section (s/get-section conn r/slug :finances)]
              updated-section => (contains updated-section)
              (check/timestamp? updated-at) => true
              (check/about-now? updated-at) => true
              (check/before? (:created-at updated-section) updated-at) => true)
            (count (s/get-revisions conn r/slug :finances)) => 1)) ; but there is still just 1 revision

        (fact "update existing revision title, body and note"
          (let [updated {:body "New Body" :title "New Title" :note "New Note"}
                response (mock/api-request :patch (section-rep/url r/slug :finances) {:body updated})
                body (mock/body-from-response response)
                updated-at (:updated-at body)]
            (:status response) => 200
            body => (contains updated)
            ;; verify the initial revision is changed
            (let [updated-section (s/get-section conn r/slug :finances)]
              updated-section => (contains updated)
              (check/timestamp? updated-at) => true
              (check/about-now? updated-at) => true
              (check/before? (:created-at updated-section) updated-at) => true)
            (count (s/get-revisions conn r/slug :finances)) => 1))))) ; but there is still just 1 revision

  (facts "about updating a placeholder section"

    (facts "with PUT"
      (with-state-changes [(before :facts (c/create-company! conn (c/add-core-placeholder-sections (c/->company r/buffer r/coyote))))
                           (after :facts (c/delete-company conn (:slug r/buffer)))]
        (fact "update existing revision title"
          (let [updated  (assoc r/text-section-1 :title "New Title")
                response (mock/api-request :put (section-rep/url (:slug r/buffer) :update) {:body updated})
                body     (mock/body-from-response response)
                company  (c/get-company conn (:slug r/buffer))]
            (:status response) => 200
            (-> company :update :placeholder) => falsey
            body => (contains updated)
            (:placeholder body) => falsey))))

    (facts "with PATCH"
      (with-state-changes [(before :facts (c/create-company! conn (c/add-core-placeholder-sections (c/->company r/buffer r/coyote))))
                           (after :facts (c/delete-company conn (:slug r/buffer)))]
        (fact "update existing revision title"
          (let [updated  {:title "New Title"}
                response (mock/api-request :patch (section-rep/url (:slug r/buffer) :update) {:body updated})
                body     (mock/body-from-response response)
                company  (c/get-company conn (:slug r/buffer))]
            (:status response) => 200
            (-> company :update :placeholder) => falsey
            (:title body) => (:title updated)
            (:placeholder body) => falsey))))

    (future-facts "with DELETE"
                  #_(with-state-changes [(before :facts (c/create-company! conn (c/->company r/buffer r/coyote)))
                                         (after :facts (c/delete-company conn (:slug r/buffer)))]
                      (fact "update existing revision title"
                        (let [response (mock/api-request :delete (section-rep/url (:slug r/buffer) :update))
                              body     (mock/body-from-response response)
                              company  (c/get-company conn (:slug r/buffer))]
                          (:status response) => 200
                          (-> company :update) => nil)))))

  (future-facts "about creating a new section revision"
                (future-facts "with PUT")
                (future-facts "with PATCH"))))