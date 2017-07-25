(ns open-company.integration.section.section-manipulation
  (:require [clojure.string :as s]
            [midje.sweet :refer :all]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [oc.lib.db.pool :as pool]
            [open-company.lib.test-setup :as ts]
            [open-company.api.common :as common]
            [open-company.resources.common :as common-res]
            [open-company.resources.company :as company]
            [open-company.resources.section :as section]
            [open-company.representations.company :as company-rep]
            [open-company.representations.section :as section-rep]))

;; ----- Test Cases -----

;; PATCHing company's :sections properties with the REST API.

;; The system should support PATCHing the company's :sections property, and handle the following scenarios:

;; fail - invalid JWToken - 401 Unauthorized
;; fail - no JWToken - 401 Unauthorized
;; fail - organization doesn't match companies - 403 Forbidden

;; fail - no matching company slug - 404 Not Found

;; success - reorder sections
;; success - remove sections
;; success - add sections (blank)
;; success - add sections (with content)


;; ----- Tests -----

(with-state-changes [(around :facts (schema.core/with-fn-validation ?form))
                     (before :contents (ts/setup-system!))
                     (after :contents (ts/teardown-system!))
                     (before :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                      (company/delete-all-companies! conn)
                                      (company/create-company! conn (company/->company r/open r/coyote))
                                      (section/put-section conn r/slug :update r/text-section-1 r/coyote)
                                      (section/put-section conn r/slug :finances r/finances-section-1 r/coyote)
                                      (section/put-section conn r/slug :team r/text-section-2 r/coyote)
                                      (section/put-section conn r/slug :help r/text-section-1 r/coyote)
                                      (section/put-section conn r/slug :diversity r/text-section-2 r/coyote)
                                      (section/put-section conn r/slug :values r/text-section-1 r/coyote)
                                      (section/put-section conn r/slug :custom-a1b2 r/text-section-2 r/coyote)))
                     (after :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                     (company/delete-all-companies! conn)))]

  (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]

    (let [original-order ["update" "finances" "team" "help" "diversity" "values" "custom-a1b2"]
          new-order ["team" "update" "finances" "help" "custom-a1b2" "diversity" "values"]]

      (facts "about failing to reorder sections"

        (fact "with an invalid JWToken"
          (let [response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}
                                                                            :auth mock/jwtoken-bad})]
            (:status response) => 401
            (:body response) => common/unauthorized)
          ;; verify the initial order is unchanged
          (let [db-company (company/get-company conn r/slug)]
            (:sections db-company) => original-order))

        (fact "with no JWToken"
          (let [response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}
                                                                            :skip-auth true})]
            (:status response) => 401
            (:body response) => common/unauthorized)
          ;; verify the initial order is unchanged
          (let [db-company (company/get-company conn r/slug)]
            (:sections db-company) => original-order))

        (fact "with an organization that doesn't match the company"
          (let [response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}
                                                                            :auth mock/jwtoken-sartre})]
            (:status response) => 403
            (:body response) => common/forbidden)
          ;; verify the initial order is unchanged
          (let [db-company (company/get-company conn r/slug)]
            (:sections db-company) => original-order))

        (fact "with no company matching the company slug"
          (let [response (mock/api-request :patch (company-rep/url "foo") {:body {:sections new-order}})]
            (:status response) => 404
            (:body response) => "")
          ;; verify the initial order is unchanged
          (let [db-company (company/get-company conn r/slug)]
            (:sections db-company) => original-order)))

      (facts "about section reordering"

        ;; verify the initial order
        (let [db-company (company/get-company conn r/slug)]
          (:sections db-company) => original-order)

        (facts "when the new order is valid"

          (fact "the section order can be adjusted"
            (let [response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})]
              (:status response) => 200
              (:sections (mock/body-from-response response)) => new-order
              ;; verify the new order
              (:sections (company/get-company conn r/slug)) => new-order))))

      (facts "about placeholder section removal"
        (let [slug     "hello-world"
              payload  {:name "Hello World" :description "x" :sections original-order}
              post-response (mock/api-request :post "/companies" {:body payload})
              company  (company/get-company conn slug)]
          ;; Ensure the placeholder sections are in company
          (:sections company) => original-order
          (doseq [section original-order]
            (company (keyword section)) => truthy)
          (let [new-sections (rest original-order)
                patch-response (mock/api-request :patch (company-rep/url slug) {:body {:sections new-sections}})
                patch-body     (mock/body-from-response patch-response)
                db-company  (company/get-company conn slug)
                get-response (mock/api-request :get (company-rep/url slug))
                get-body (mock/body-from-response get-response)]
            ;; verify the response status
            (doseq [response [patch-response get-response]]  
              (:status response) => 200)
            ;; verify the DB data and API responses
            (doseq [body [patch-body db-company get-body]]
              (doseq [section new-sections]
                (body (keyword section)) => truthy)
              (body (keyword (first original-order))) => nil)
            ;; verify placeholders don't archive
            (doseq [body [patch-body get-body]]
              (:archived body) => []))))

      (facts "about section removal"

        ;; verify the initial set of sections
        (:sections (company/get-company conn r/slug)) => original-order

        (fact "a section can be removed"
          (let [new-set (rest original-order)
                patch-response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-set}})
                patch-body (mock/body-from-response patch-response)
                db-company (company/get-company conn r/slug)
                get-response (mock/api-request :get (company-rep/url r/slug))
                get-body (mock/body-from-response get-response)]
            ;; verify the response statuses
            (doseq [response [patch-response get-response]]  
              (:status response) => 200)
            ;; verify the DB data and API responses
            (doseq [body [patch-body db-company get-body]]
              (:sections body) => new-set
              (:update body) => nil
              (:finances body) => (contains r/finances-section-1)
              (:team body) => (contains r/text-section-2)
              (:help body) => (contains r/text-section-1)
              (:diversity body) => (contains r/text-section-2)
              (:values body) => (contains r/text-section-1)
              (:custom-a1b2 body) => (contains r/text-section-2))
            ;; verify removed section is archived
            (doseq [body [patch-body get-body]]
              (:archived body) => [{:section "update" :title "Text Section 1"}])))

        (fact "multiple sections can be removed"
          (let [new-set ["diversity" "values" "help"]
                patch-response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-set}})
                patch-body (mock/body-from-response patch-response)
                db-company (company/get-company conn r/slug)
                get-response (mock/api-request :get (company-rep/url r/slug))
                get-body (mock/body-from-response get-response)]
            ;; verify the response statuses
            (doseq [response [patch-response get-response]]  
              (:status response) => 200)
            ;; verify the DB data and API responses
            (doseq [body [patch-body db-company get-body]]
              (:sections body) => new-set
              (:update body) => nil
              (:finances body) => nil
              (:team body) => nil
              (:help body) => (contains r/text-section-1)
              (:diversity body) => (contains r/text-section-2)
              (:values body) => (contains r/text-section-1)
              (:custom-a1b2 body) => nil)
            ;; verify removed section is archived
            (doseq [body [patch-body get-body]]
              (:archived body) => (contains [{:section "custom-a1b2" :title "Text Section 2"}
                                             {:section "team" :title "Text Section 2"}
                                             {:section "finances" :title "Finances Section 1"}
                                             {:section "update" :title "Text Section 1"}] :in-any-order))))))))