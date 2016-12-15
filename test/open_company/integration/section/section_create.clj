(ns open-company.integration.section.section-create
  "Create a new section in a company."
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

;; There are 2 ways to create new sections. PATCH the section into the company, or PUT the section directly
;; at its own URL.

;; Identified combinatorial variation:
;; Method: Company PATCH, Section PUT
;; Operation: add section, add multiple sections
;; Section Type: known section, custom
;; Section contents: blank, valid section content, invalid section content, section content only


;; The system should support PATCHing the company, and handle the following scenarios:

;; For known and custom sections:
;; success - add section - no section content
;; success - add sections - no section content
;; success - add section - with valid content
;; success - add sections - with valid content
;; success - add section - with valid content
;; success - add sections - with valid content
;; failure - add section - with invalid content
;; failure - add sections - with invalid content
;; failure - add section - section content only
;; failure - add sections - section content only

;; For unknown sections:
;; failure - add section
;; failure - add sections


;; PUT the section directly with the REST API.

;; The system should support PUTing the section, and handle the following scenarios:

;; For known and custom sections:
;; success - add section - no section content
;; success - add section - with valid content
;; failure - add section - with invalid content

;; For unknown sections:
;; failure - add section


;; ----- Tests -----

(with-state-changes [(around :facts (schema.core/with-fn-validation ?form))
                     (before :contents (ts/setup-system!))
                     (after :contents (ts/teardown-system!))
                     (before :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                      (company/delete-all-companies! conn)
                                      (company/create-company! conn (company/->company r/open r/coyote))))
                     (after :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                     (company/delete-all-companies! conn)))]

  (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]

    (facts "about adding sections"

      (future-facts "by PATCHing the company"

        (future-fact "with one new section"

          (future-fact "without section content")

          (future-fact "with valid section content"))

        (future-fact "with multiple new sections"

          (future-fact "without section content")

          (future-fact "with valid section content")))

      (future-facts "by PUTing the section")

        (future-fact "without section content")

        (future-fact "with valid section content"))

    (facts "about failing to add sections"

      (future-facts "by PATCHing the company")

      (future-facts "by PUTing the section"))))


;         (fact "that don't really exist"
;           (let [new-sections (conj original-order "health")
;                 response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})
;                 body (mock/body-from-response response)]
;             (:status response) => 422))

;         (facts "without any section content"
    
;           (fact "that never existed"

;             (fact "and are known"
;               (let [new-sections (conj original-order "highlights")
;                     response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})
;                     resp-body (mock/body-from-response response)
;                     resp-highlights (:highlights resp-body)
;                     db-company (company/get-company conn r/slug)
;                     db-highlights (:highlights db-company)
;                     placeholder (dissoc (common-res/sections-by-name :highlights) :section-name)]
;                 (:status response) => 200
;                 ; verify section list in response and DB
;                 (doseq [body [resp-body db-company]]
;                   (:sections body) => new-sections)
;                 ; verify placeholder flag and content in response and DB
;                 (doseq [highlights [resp-highlights db-highlights]]
;                   (:placeholder highlights) => true
;                   highlights => (contains placeholder))))

;             (fact "and are custom"
;               (let [custom-name (str "custom-" (subs (str (java.util.UUID/randomUUID)) 0 4))
;                     custom-title (str "Custom " custom-name)
;                     custom-body {:title custom-title}
;                     new-sections (conj original-order custom-name)
;                     response (mock/api-request :patch (company-rep/url r/slug) {:body 
;                       {:sections new-sections
;                        custom-name custom-body}})
;                     resp-body (mock/body-from-response response)
;                     resp-custom-section (resp-body (keyword custom-name))
;                     db-company (company/get-company conn r/slug)
;                     db-custom-section (db-company (keyword custom-name))]
;                 (:status response) => 200
;                 ; verify section list in response and DB
;                 (doseq [body [resp-body db-company]]
;                   (:sections body) => new-sections)
;                 ; verify placeholder flag and content in response and DB
;                 (doseq [section [resp-custom-section db-custom-section]]
;                   section => (contains common-res/initial-custom-properties)
;                   section => (contains custom-body))))

;             (facts "and are duplicated"
;               (let [new-sections (conj original-order "update") ; update twice
;                     response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})
;                     resp-body (mock/body-from-response response)]
;                 (:status response) => 422
;                 (s/includes? (:body response) "sections") => true)
;               (let [new-sections (conj original-order "highlights" "highlights")
;                     response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})]
;                 (:status response) => 422
;                 (s/includes? (:body response) "sections") => true)
;               (let [new-sections (conj original-order "custom-1234" "custom-1234")
;                     response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})]
;                 (:status response) => 422
;                 (s/includes? (:body response) "sections") => true)))

;           (future-fact "that used to exist"
;             (let [_delay (Thread/sleep 1000) ; wait long enough for timestamps of the new revision to differ definitively
;                   new-content {:title "Update" :headline "Headline #2" :body "Update #2."}
;                   ; Update the content using another user to create a newer revision
;                   put1-response (mock/api-request :put (section-rep/url r/slug "update") {:auth mock/jwtoken-camus 
;                                                                                               :body new-content})
;                   ; Then remove the content
;                   new-sections (rest original-order)
;                   put2-response (mock/api-request :put (company-rep/url r/slug) {:body {:sections new-sections}})
;                   put2-body (mock/body-from-response put2-response)
;                   db1-company (company/get-company conn r/slug)
;                   ; Now add the section again
;                   put3-response (mock/api-request :put (company-rep/url r/slug) {:body {:sections original-order}})
;                   put3-body (mock/body-from-response put3-response)
;                   db2-company (company/get-company conn r/slug)]
;               ;; verify the response statuses
;               (doseq [response [put1-response put2-response put3-response]]  
;                 (:status response) => 200)
;               ;; verify update is not in the removal response
;               (:sections put2-body) => new-sections
;               (:update put2-body) => nil
;               ;; verify update is not in the DB
;               (:update db1-company) => nil
;               ;; verify update is in the re-add response AND contains the latest content
;               (:sections put3-body) => original-order
;               (:update put3-body) => (contains new-content)
;               ; verify update is in the DB AND contains the latest content
;               (:update db2-company) => (contains new-content))))
      

;         (facts "with section content"

;           (let [new-sections (conj original-order "kudos")
;                 kudos-placeholder (dissoc (common-res/sections-by-name :kudos) :placeholder :section-name)]

;             (facts "with minimal content"
;               (let [kudos-content {:headline "Fred is killing it!"}
;                     response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections
;                                                                                        :kudos kudos-content}})
;                     body (mock/body-from-response response)
;                     db-company (company/get-company conn r/slug)
;                     db-kudos (:kudos db-company)
;                     db-kudos-2 (section/get-section conn r/slug :kudos)]
;                 (:status response) => 200
;                 (doseq [kudos [(:kudos body) db-kudos db-kudos-2]]
;                   kudos => (contains (merge kudos-placeholder kudos-content)))))

;             (fact "with maximal content"
;               (let [kudos-content {:title "Great Jobs!"
;                                    :headline "Good stuff"
;                                    :body "Fred is killing it"
;                                    :image-url "url"
;                                    :image-height 42
;                                    :image-width 7}
;                     response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections
;                                                                                        :kudos kudos-content}})
;                     body (mock/body-from-response response)
;                     db-company (company/get-company conn r/slug)
;                     db-kudos (:kudos db-company)
;                     db-kudos-2 (section/get-section conn r/slug :kudos)]
;                 (:status response) => 200
;                 (doseq [kudos [(:kudos body) db-kudos db-kudos-2]]
;                   kudos => (contains (merge kudos-placeholder kudos-content))))))

;           (let [new-sections (conj original-order "custom-c3p0")]

;             (facts "with custom topic content"
;               (let [custom-content {:headline "Fred is killing it!"}
;                     response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections
;                                                                                        :custom-c3p0 custom-content}})
;                     body (mock/body-from-response response)
;                     db-company (company/get-company conn r/slug)
;                     db-custom (:custom-c3p0 db-company)
;                     db-custom-2 (section/get-section conn r/slug :custom-c3p0)]
;                 (:status response) => 200
;                 (doseq [custom [(:custom-c3p0 body) db-custom db-custom-2]]
;                   custom => (contains custom-content))))))))))