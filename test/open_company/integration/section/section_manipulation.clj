(ns open-company.integration.section.section-manipulation
  (:require [clojure.string :as s]
            [midje.sweet :refer :all]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [oc.lib.rethinkdb.pool :as pool]
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

;; TODO
;; no accept
;; no content type
;; no charset
;; wrong accept
;; wrong content type
;; wrong charset

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
              (:archived body) => [{:section "custom-a1b2" :title "Text Section 2"}
                                   {:section "team" :title "Text Section 2"}
                                   {:section "finances" :title "Finances Section 1"}
                                   {:section "update" :title "Text Section 1"}]))))
  
      (facts "about adding sections"

        (fact "that don't really exist"
          (let [new-sections (conj original-order "health")
                response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})
                body (mock/body-from-response response)]
            (:status response) => 422))

        (facts "without any section content"
    
          (fact "that never existed"

            (fact "and are known"
              (let [new-sections (conj original-order "highlights")
                    response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})
                    resp-body (mock/body-from-response response)
                    resp-highlights (:highlights resp-body)
                    db-company (company/get-company conn r/slug)
                    db-highlights (:highlights db-company)
                    placeholder (dissoc (common-res/sections-by-name :highlights) :section-name)]
                (:status response) => 200
                ; verify section list in response and DB
                (doseq [body [resp-body db-company]]
                  (:sections body) => new-sections)
                ; verify placeholder flag and content in response and DB
                (doseq [highlights [resp-highlights db-highlights]]
                  (:placeholder highlights) => true
                  highlights => (contains placeholder))))

            (fact "and are custom"
              (let [custom-name (str "custom-" (subs (str (java.util.UUID/randomUUID)) 0 4))
                    custom-title (str "Custom " custom-name)
                    custom-body {:title custom-title :placeholder true}
                    new-sections (conj original-order custom-name)
                    response (mock/api-request :patch (company-rep/url r/slug) {:body 
                      {:sections new-sections
                       custom-name custom-body}})
                    resp-body (mock/body-from-response response)
                    resp-custom-section (resp-body (keyword custom-name))
                    db-company (company/get-company conn r/slug)
                    db-custom-section (db-company (keyword custom-name))]
                (:status response) => 200
                ; verify section list in response and DB
                (doseq [body [resp-body db-company]]
                  (:sections body) => new-sections)
                ; verify placeholder flag and content in response and DB
                (doseq [section [resp-custom-section db-custom-section]]
                  (:placeholder section) => true
                  section => (contains common-res/initial-custom-properties)
                  section => (contains custom-body))))

            (facts "and are duplicated"
              (let [new-sections (conj original-order "update") ; update twice
                    response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})
                    resp-body (mock/body-from-response response)]
                (:status response) => 422
                (s/includes? (:body response) "sections") => true)
              (let [new-sections (conj original-order "highlights" "highlights")
                    response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})]
                (:status response) => 422
                (s/includes? (:body response) "sections") => true)
              (let [new-sections (conj original-order "custom-1234" "custom-1234")
                    response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})]
                (:status response) => 422
                (s/includes? (:body response) "sections") => true)))

          (fact "that used to exist"
            (let [_delay (Thread/sleep 1000) ; wait long enough for timestamps of the new revision to differ definitively
                  new-content {:title "Update" :headline "Headline #2" :body "Update #2."}
                  ; Update the content using another user to create a newer revision
                  patch1-response (mock/api-request :patch (section-rep/url r/slug "update") {:auth mock/jwtoken-camus 
                                                                                              :body new-content})
                  ; Then remove the content
                  new-sections (rest original-order)
                  patch2-response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})
                  patch2-body (mock/body-from-response patch2-response)
                  db1-company (company/get-company conn r/slug)
                  ; Now add the section again
                  patch3-response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections original-order}})
                  patch3-body (mock/body-from-response patch3-response)
                  db2-company (company/get-company conn r/slug)]
              ;; verify the response statuses
              (doseq [response [patch1-response patch2-response patch3-response]]  
                (:status response) => 200)
              ;; verify update is not in the removal response
              (:sections patch2-body) => new-sections
              (:update patch2-body) => nil
              ;; verify update is not in the DB
              (:update db1-company) => nil
              ;; verify update is in the re-add response AND contains the latest content
              (:sections patch3-body) => original-order
              (:update patch3-body) => (contains new-content)
              ; verify update is in the DB AND contains the latest content
              (:update db2-company) => (contains new-content))))
      

        (facts "with section content"

          (let [new-sections (conj original-order "kudos")
                kudos-placeholder (dissoc (common-res/sections-by-name :kudos) :placeholder :section-name)]

            (facts "with minimal content"
              (let [kudos-content {:headline "Fred is killing it!"}
                    response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections
                                                                                       :kudos kudos-content}})
                    body (mock/body-from-response response)
                    db-company (company/get-company conn r/slug)
                    db-kudos (:kudos db-company)
                    db-kudos-2 (section/get-section conn r/slug :kudos)]
                (:status response) => 200
                (doseq [kudos [(:kudos body) db-kudos db-kudos-2]]
                  kudos => (contains (merge kudos-placeholder kudos-content)))))

            (fact "with maximal content"
              (let [kudos-content {:title "Great Jobs!"
                                   :headline "Good stuff"
                                   :body "Fred is killing it"
                                   :image-url "url"
                                   :image-height 42
                                   :image-width 7
                                   :pin true}
                    response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections
                                                                                       :kudos kudos-content}})
                    body (mock/body-from-response response)
                    db-company (company/get-company conn r/slug)
                    db-kudos (:kudos db-company)
                    db-kudos-2 (section/get-section conn r/slug :kudos)]
                (:status response) => 200
                (doseq [kudos [(:kudos body) db-kudos db-kudos-2]]
                  kudos => (contains (merge kudos-placeholder kudos-content))))))

          (let [new-sections (conj original-order "custom-c3p0")]

            (facts "with custom topic content"
              (let [custom-content {:headline "Fred is killing it!"}
                    response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections
                                                                                       :custom-c3p0 custom-content}})
                    body (mock/body-from-response response)
                    db-company (company/get-company conn r/slug)
                    db-custom (:custom-c3p0 db-company)
                    db-custom-2 (section/get-section conn r/slug :custom-c3p0)]
                (:status response) => 200
                (doseq [custom [(:custom-c3p0 body) db-custom db-custom-2]]
                  custom => (contains custom-content))))))))))