(ns open-company.integration.section.section-manipulation
  (:require [midje.sweet :refer :all]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.db.pool :as pool]
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

(def categories (map name common-res/category-names))

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
  (facts "about failing to reorder sections"

    (fact "with an invalid JWToken"
      (let [new-order {:progress ["team" "update" "finances" "help" "custom-a1b2"]
                       :company ["diversity" "values"]}
            response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}
                                                                        :auth mock/jwtoken-bad})]
        (:status response) => 401
        (:body response) => common/unauthorized)
      ;; verify the initial order is unchanged
      (let [db-company (company/get-company conn r/slug)]
        (:categories db-company) => categories
        (:sections db-company) => {:company ["diversity" "values"]
                                   :progress ["update" "finances" "team" "help" "custom-a1b2"]}))

    (fact "with no JWToken"
      (let [new-order {:progress ["team" "update" "finances" "help" "custom-a1b2"]
                       :company ["diversity" "values"]}
            response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}
                                                                        :skip-auth true})]
        (:status response) => 401
        (:body response) => common/unauthorized)
      ;; verify the initial order is unchanged
      (let [db-company (company/get-company conn r/slug)]
        (:categories db-company) => categories
        (:sections db-company) => {:company ["diversity" "values"]
                                   :progress ["update" "finances" "team" "help" "custom-a1b2"]}))

    (fact "with an organization that doesn't match the company"
      (let [new-order {:progress ["team" "update" "finances" "help" "custom-a1b2"]
                       :company ["diversity" "values"]}
            response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}
                                                                        :auth mock/jwtoken-sartre})]
        (:status response) => 403
        (:body response) => common/forbidden)
      ;; verify the initial order is unchanged
      (let [db-company (company/get-company conn r/slug)]
        (:categories db-company) => categories
        (:sections db-company) => {:company ["diversity" "values"]
                                   :progress ["update" "finances" "team" "help" "custom-a1b2"]}))

    (fact "with no company matching the company slug"
      (let [new-order {:company ["diversity" "values"]
                       :progress ["team" "update" "finances" "help" "custom-a1b2"]}
            response (mock/api-request :patch (company-rep/url "foo") {:body {:sections new-order}})]
        (:status response) => 404
        (:body response) => "")
      ;; verify the initial order is unchanged
      (let [db-company (company/get-company conn r/slug)]
        (:categories db-company) => categories
        (:sections db-company) => {:company ["diversity" "values"]
                                   :progress ["update" "finances" "team" "help" "custom-a1b2"]})))

  (facts "about section reordering"

    ;; verify the initial order
    (let [db-company (company/get-company conn r/slug)]
      (:categories db-company) => categories
      (:sections db-company) => {:company ["diversity" "values"]
                                 :progress ["update" "finances" "team" "help" "custom-a1b2"]})

    (facts "when the new order is valid"

      (fact "the section order in the progress category can be gently adjusted"
        (let [new-order {:progress ["team" "update" "help" "finances" "custom-a1b2"]
                         :company ["diversity" "values"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})]
          (:status response) => 200
          (:sections (mock/body-from-response response)) => new-order
          ;; verify the new order
          (:sections (company/get-company conn r/slug)) => new-order))

      (fact "the sections order in the progress category can be greatly adjusted"
        (let [new-order {:progress ["custom-a1b2" "help" "team" "update" "finances"]
                         :company ["diversity" "values"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})]
          (:status response) => 200
          (:sections (mock/body-from-response response)) => new-order
          ;; verify the new order
          (:sections (company/get-company conn r/slug)) => new-order))

      (fact "the section order in the company category can be adjusted"
        (let [new-order {:progress ["update" "finances" "team" "help" "custom-a1b2"]
                         :company ["values" "diversity"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})]
          (:status response) => 200
          (:sections (mock/body-from-response response)) => new-order
          ;; verify the new order
          (:sections (company/get-company conn r/slug)) => new-order))

      (fact "the section order in the progress and company category can both be adjusted at once"
        (let [new-order {:progress ["custom-a1b2" "help" "team" "update" "finances"]
                         :company ["values" "diversity"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})]
          (:status response) => 200
          (:sections (mock/body-from-response response)) => new-order
          ;; verify the new order
          (:sections (company/get-company conn r/slug)) => new-order))))

  (facts "about placeholder section removal"
    (let [slug     "hello-world"
          payload  {:name "Hello World" :description "x"}
          post-response (mock/api-request :post "/companies" {:body payload})
          placeholder-sections {:progress ["update" "growth" "challenges" "team" "product" "finances"]
                                :company ["mission" "values"]}
          patch-response (mock/api-request :patch (company-rep/url slug) {:body {:sections placeholder-sections}})
          company  (company/get-company conn slug)]
      ;; Ensure the placeholder sections are in company
      (:sections company) => placeholder-sections
      (:growth company) => truthy
      (:challenges company) => truthy
      (:team company) => truthy
      (:product company) => truthy
      (:mission company) => truthy
      (let [new-set {:company ["values"]
                     :progress ["update" "finances" "help" "custom-a1b2"]}
            patch-response (mock/api-request :patch (company-rep/url slug) {:body {:sections new-set}})
            patch-body     (mock/body-from-response patch-response)
            db-company  (company/get-company conn slug)
            get-response (mock/api-request :get (company-rep/url slug))
            get-body (mock/body-from-response get-response)]
        ;; verify the response status
        (doseq [response [patch-response get-response]]  
          (:status response) => 200)
        ;; verify the DB data and API responses
        (doseq [body [patch-body db-company get-body]]
          (:sections body) => new-set
          (:growth body) => falsey
          (:challenges body) => falsey
          (:team body) => falsey
          (:product body) => falsey
          (:mission body) => falsey)
        ;; verify placeholders don't archive
        (doseq [body [patch-body get-body]]
          (:archived body) => [])))) 

  (facts "about section removal"

    ;; verify the initial set of sections
    (:sections (company/get-company conn r/slug)) => {:company ["diversity" "values"]
                                                      :progress ["update" "finances" "team" "help" "custom-a1b2"]}

      (fact "a section can be removed from the progress category"
        (let [new-set {:company ["diversity" "values"]
                       :progress ["help" "update" "finances" "custom-a1b2"]}
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
            (:update body) => (contains r/text-section-1)
            (:finances body) => (contains r/finances-section-1)
            (:team body) => nil
            (:help body) => (contains r/text-section-1)
            (:diversity body) => (contains r/text-section-2)
            (:values body) => (contains r/text-section-1))
          ;; verify removed section is archived
          (doseq [body [patch-body get-body]]
            (:archived body) => [{:section "team" :title "Text Section 2"}])))

      (fact "multiple sections can be removed from the progress category"
        (let [new-set {:company ["diversity" "values"]
                       :progress ["help"]}
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
            (:team body) => nil
            (:finances body) => nil
            (:help body) => (contains r/text-section-1)
            (:diversity body) => (contains r/text-section-2)
            (:values body) => (contains r/text-section-1))
          ;; verify removed section is archived
          (doseq [body [patch-body get-body]]
            (:archived body) => [{:section "custom-a1b2" :title "Text Section 2"}
                                 {:section "team" :title "Text Section 2"}
                                 {:section "finances" :title "Finances Section 1"}
                                 {:section "update" :title "Text Section 1"}])))

      (fact "a section can be removed from the company category"
        (let [new-order {:company ["diversity"]
                         :progress ["help" "update" "team" "finances" "custom-a1b2"]}
              patch-response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})
              patch-body (mock/body-from-response patch-response)
              db-company (company/get-company conn r/slug)
              get-response (mock/api-request :get (company-rep/url r/slug))
              get-body (mock/body-from-response get-response)]
          ;; verify the response statuses
          (doseq [response [patch-response get-response]]  
            (:status response) => 200)
          ;; verify the DB data and API responses
          (doseq [body [patch-body db-company get-body]]
            (:sections body) => new-order
            (:update body) => (contains r/text-section-1)
            (:finances body) => (contains r/finances-section-1)
            (:team body) => (contains r/text-section-2)
            (:help body) => (contains r/text-section-1)
            (:diversity body) => (contains r/text-section-2)
            (:values body) => nil)
          ;; verify removed section is archived
          (doseq [body [patch-body get-body]]
            (:archived body) => [{:section "values" :title "Text Section 1"}])))

      (fact "sections can be removed from all the categories at once"
        (let [new-order {:company ["values"]
                         :progress ["update" "help"]}
              patch-response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})
              patch-body (mock/body-from-response patch-response)
              db-company (company/get-company conn r/slug)
              get-response (mock/api-request :get (company-rep/url r/slug))
              get-body (mock/body-from-response get-response)]
          ;; verify the response statuses
          (doseq [response [patch-response get-response]]  
            (:status response) => 200)
          ;; verify the DB data and API responses
          (doseq [body [patch-body db-company get-body]]
            (:sections body) => new-order
            (:update body) => (contains r/text-section-1)
            (:finances body) => nil
            (:team body) => nil
            (:help body) => (contains r/text-section-1)
            (:diversity body) => nil
            (:values body) => (contains r/text-section-1))
          ;; verify removed sections are archived
          (doseq [body [patch-body get-body]]
            (:archived body) => [{:section "custom-a1b2" :title "Text Section 2"}
                                 {:section "diversity" :title "Text Section 2"}
                                 {:section "team" :title "Text Section 2"}
                                 {:section "finances" :title "Finances Section 1"}]))))
  
  (facts "about adding sections"

    (fact "that don't really exist"
      (let [new-sections {:company [] :progress ["health"]}
            response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})
            body (mock/body-from-response response)]
        (:status response) => 422))

    (facts "without any section content"
    
      (fact "that never existed"

        (fact "and are known"
          (let [new-sections {:company [] :progress ["highlights"]}
                response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})
                resp-body (mock/body-from-response response)
                resp-highlights (:highlights resp-body)
                db-company (company/get-company conn r/slug)
                db-highlights (:highlights db-company)
                placeholder (dissoc (common-res/section-by-name :highlights) :section-name :core)]
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
                new-sections {:company [] :progress [custom-name]}
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
              section => (contains section/initial-custom-properties)
              (:snippet section) => section/custom-topic-placeholder-snippet
              section => (contains custom-body)))))

      (fact "that used to exist"
        (let [_delay (Thread/sleep 1000) ; wait long enough for timestamps of the new revision to differ definitively
              new-content {:title "Update" :headline "Headline #2" :body "Update #2."}
              ; Update the content using another user to create a newer revision
              patch1-response (mock/api-request :patch (section-rep/url r/slug "update") {:auth mock/jwtoken-camus 
                                                                                          :body new-content})
              ; Then remove the content
              new-sections {:company [] :progress []}
              patch2-response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})
              patch2-body (mock/body-from-response patch2-response)
              db1-company (company/get-company conn r/slug)
              ; Now add the section again
              newer-sections {:company [] :progress ["update"]}
              patch3-response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections newer-sections}})
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
          (:sections patch3-body) => newer-sections
          (:update patch3-body) => (contains new-content)
          ; verify update is in the DB AND contains the latest content
          (:update db2-company) => (contains new-content))))

    (facts "with section content"

      (let [new-sections {:company ["diversity" "values"]
                            :progress ["help" "update" "team" "kudos" "finances"]}
            kudos-placeholder (common-res/section-by-name "kudos")]

        (future-fact "with minimal content"
          (let [kudos-content {:body "Fred is killing it"}
                response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections
                                                                                 :kudos kudos-content}})
                body (mock/body-from-response response)
                db-company (company/get-company conn r/slug)]
            (:status response) => 200
            (:body response) => nil))

        (future-fact "with maximal content"
          (let [kudos-content {:title "Great Jobs!" :headline "Good stuff" :body "Fred is killing it"}
                response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections
                                                                                   :kudos kudos-content}})
                body (mock/body-from-response response)
                db-company (company/get-company conn r/slug)]
            (:status response) => 200
            (:body response) => nil))

        (future-fact "with too much content"
      
          (future-fact "extra properties aren't allowed")

          (future-fact "read/only properties are ignored")))))))