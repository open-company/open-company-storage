(ns open-company.integration.stakeholder-update.stakeholder-update-create
  (:require [cheshire.core :as json]
            [open-company.lib.test-setup :as ts]
            [oc.lib.rethinkdb.pool :as pool]
            [midje.sweet :refer :all]
            [open-company.lib.check :as check]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.hateoas :as hateoas]
            [open-company.lib.resources :as r]
            [open-company.api.common :as common]
            [open-company.resources.company :as company]
            [open-company.resources.section :as section]
            [open-company.resources.stakeholder-update :as su]
            [open-company.representations.company :as company-rep]
            [open-company.representations.stakeholder-update :as su-rep]))

;; ----- Test Cases -----

;; Creating stakeholder updates with the REST API

;; The system should store newly created, valid stakeholder updates and handle the following scenarios:

;; OPTIONS

;; POST (stakeholder-update)
;; bad, no JWToken - 401 Unauthorized
;; bad, invalid JWToken - 401 Unauthorized
;; bad, no matching company
;; bad, no sections

;; PATCH (company)
;; bad, invalid sections

;; POST (URL Link, Email, Slack)
;; all good, 1 section - 201 Created
;; all good, many sections - 201 Created
;; all good, custom sections - 201 Created
;; all good, mixed sections - 201 Created

;; no accept
;; no content type
;; no charset
;; wrong accept
;; wrong content type
;; no body
;; wrong charset
;; body not valid JSON
;; no sections in body

;; ----- Data -----

(def link nil)
(def slack {:slack true :note "Slack"})
(def email {:email true :note "Email" :to ["foo@bar.com", "bar@foo.com"]})

;; ----- Tests -----

(with-state-changes [(before :contents (ts/setup-system!))
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
                                      (section/put-section conn r/slug :custom-a1b2 r/text-section-1 r/coyote)
                                      (section/put-section conn r/slug :custom-r2d2 r/text-section-2 r/coyote)))
                     (after :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                     (company/delete-all-companies! conn)))]

  (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]

    (let [company-url (company-rep/url r/slug)
          su-url (su-rep/stakeholder-updates-url company-url)]

      (facts "about failing to create stakeholder updates"

        (fact "with an invalid JWToken"
          (let [response (mock/api-request :post su-url {:body nil :auth mock/jwtoken-bad})]
            (:status response) => 401
            (:body response) => common/unauthorized)
          ;; verify no stakeholder update is created
          (count (su/list-stakeholder-updates conn r/slug)) => 0)

        (fact "with no JWToken"
          (let [response (mock/api-request :post su-url {:body nil :skip-auth true})]
            (:status response) => 401
            (:body response) => common/unauthorized)
          ;; verify no stakeholder update is created
          (count (su/list-stakeholder-updates conn r/slug)) => 0)

        (future-fact "with an organization that doesn't match the company"
          (let [response (mock/api-request :post su-url {:body nil :auth mock/jwtoken-sartre})]
            (:status response) => 403
            (:body response) => common/forbidden)
          ;; verify no stakeholder update is created
          (count (su/list-stakeholder-updates conn r/slug)) => 0)

        (fact "with no company matching the company slug"
          (let [response (mock/api-request :post (su-rep/stakeholder-updates-url (company-rep/url "foo")) {:body nil})]
            (:status response) => 404
            (:body response) => "")
          ;; verify no stakeholder update is created
          (count (su/list-stakeholder-updates conn r/slug)) => 0)

        (future-fact "with no sections in the update"
          (mock/api-request :patch company-url {:stakeholder-update {:title "Blank" :sections []}})
          (let [response (mock/api-request :post su-url {:body nil})]
            (:status response) => 422
            (:body response) => "")
          ;; verify no stakeholder update is created
          (count (su/list-stakeholder-updates conn r/slug)) => 0)

        (future-fact "with invalid sections in the update"
          (let [response (mock/api-request :patch company-url
                            {:stakeholder-update {:title "Invalid" :sections ["foo"]}})]
            (:status response) => 422
            (:body response) => "")
          ;; verify the stakeholder update property is unmodified
          (company/get-company conn r/slug) => (contains {:stakeholder-update {:title "" :sections []}})))

      (facts "about creating stakeholder updates"

        ;; 1 time through for link, Slack and email distribution respectively
        (doseq [body [link]]
          ;[link slack email]]

          (fact "with 1 section"
            (mock/api-request :patch company-url {:body {:stakeholder-update {:title "1 section" :sections [:update]}}})
            (let [response (mock/api-request :post su-url {:body body})
                  put-response-body (mock/body-from-response response)
                  su-slug (:slug put-response-body)
                  db-response-body (su/get-stakeholder-update conn r/slug su-slug)]
              (:status response) => 201
              (get-in response [:headers "Location"]) => (su-rep/stakeholder-update-url company-url su-slug)
              ;; Check the response and the database
              (doseq [response-body [put-response-body db-response-body]]
                (:title response-body) => "1 section"
                (:sections response-body) => ["update"]
                (-> response-body :author :name) => (:real-name r/coyote)
                (-> response-body :author :avatar) => (:image r/coyote)
                (-> response-body :author :user-id) => (:user-id r/coyote)
                (check/timestamp? (:created-at response-body)) => true
                (check/about-now? (:created-at response-body)) => true
                (:update response-body) => (contains r/text-section-1)
                (:finances response-body) => nil
                (:diversity response-body) => nil
                (:team response-body) => nil
                (:custom-a1b2 response-body) => nil
                (:custom-r2d2 response-body) => nil
                (when (= body slack)
                  (:note response-body) => (:note slack))
                (when (= body email)
                  (:note response-body) => (:note email))))
            ;; verify a stakeholder update is created
            (count (su/list-stakeholder-updates conn r/slug)) => 1)

          (fact "with many sections"
            (mock/api-request :patch company-url {:body {:stakeholder-update {:title "Many sections" :sections [:update :finances :diversity]}}})
            (let [response (mock/api-request :post su-url {:body body})
                  put-response-body (mock/body-from-response response)
                  su-slug (:slug put-response-body)
                  db-response-body (su/get-stakeholder-update conn r/slug su-slug)]
              (:status response) => 201
              (get-in response [:headers "Location"]) => (su-rep/stakeholder-update-url company-url su-slug)
              ;; Check the response and the database
              (doseq [response-body [put-response-body db-response-body]]
                (:title response-body) => "Many sections"
                (:sections response-body) => ["update" "finances" "diversity"]
                (-> response-body :author :name) => (:real-name r/coyote)
                (-> response-body :author :avatar) => (:image r/coyote)
                (-> response-body :author :user-id) => (:user-id r/coyote)
                (check/timestamp? (:created-at response-body)) => true
                (check/about-now? (:created-at response-body)) => true
                (:update response-body) => (contains r/text-section-1)
                (:finances response-body) => (contains r/finances-section-1)
                (:diversity response-body) => (contains r/text-section-2)
                (:team response-body) => nil
                (:custom-a1b2 response-body) => nil
                (:custom-r2d2 response-body) => nil
                (when (= body slack)
                  (:note response-body) => (:note slack))
                (when (= body email)
                  (:note response-body) => (:note email))))
            ;; verify a stakeholder update is created
            (count (su/list-stakeholder-updates conn r/slug)) => 1)

          (fact "with 1 custom section"
            (mock/api-request :patch company-url {:body {:stakeholder-update {:title "1 custom section" :sections [:custom-a1b2]}}})
            (let [response (mock/api-request :post su-url {:body body})
                  put-response-body (mock/body-from-response response)
                  su-slug (:slug put-response-body)
                  db-response-body (su/get-stakeholder-update conn r/slug su-slug)]
              (:status response) => 201
              (get-in response [:headers "Location"]) => (su-rep/stakeholder-update-url company-url su-slug)
              ;; Check the response and the database
              (doseq [response-body [put-response-body db-response-body]]
                (:title response-body) => "1 custom section"
                (:sections response-body) => ["custom-a1b2"]
                (-> response-body :author :name) => (:real-name r/coyote)
                (-> response-body :author :avatar) => (:image r/coyote)
                (-> response-body :author :user-id) => (:user-id r/coyote)
                (check/timestamp? (:created-at response-body)) => true
                (check/about-now? (:created-at response-body)) => true
                (:update response-body) => nil
                (:finances response-body) => nil
                (:diversity response-body) => nil
                (:team response-body) => nil
                (:custom-a1b2 response-body) => (contains r/text-section-1)
                (:custom-r2d2 response-body) => nil
                (when (= body slack)
                  (:note response-body) => (:note slack))
                (when (= body email)
                  (:note response-body) => (:note email))))
            ;; verify a stakeholder update is created
            (count (su/list-stakeholder-updates conn r/slug)) => 1)
          
          (fact "with mixed sections"
            (mock/api-request :patch company-url {:body {:stakeholder-update {:title "Mixed sections" :sections [:update :finances :diversity :custom-a1b2 :custom-r2d2]}}})
            (let [response (mock/api-request :post su-url {:body body})
                  put-response-body (mock/body-from-response response)
                  su-slug (:slug put-response-body)
                  db-response-body (su/get-stakeholder-update conn r/slug su-slug)]
              (:status response) => 201
              (get-in response [:headers "Location"]) => (su-rep/stakeholder-update-url company-url su-slug)
              ;; Check the response and the database
              (doseq [response-body [put-response-body db-response-body]]
                (:title response-body) => "Mixed sections"
                (:sections response-body) => ["update" "finances" "diversity" "custom-a1b2" "custom-r2d2"]
                (-> response-body :author :name) => (:real-name r/coyote)
                (-> response-body :author :avatar) => (:image r/coyote)
                (-> response-body :author :user-id) => (:user-id r/coyote)
                (check/timestamp? (:created-at response-body)) => true
                (check/about-now? (:created-at response-body)) => true
                (:update response-body) => (contains r/text-section-1)
                (:finances response-body) => (contains r/finances-section-1)
                (:diversity response-body) => (contains r/text-section-2)
                (:team response-body) => nil
                (:custom-a1b2 response-body) => (contains r/text-section-1)
                (:custom-r2d2 response-body) => (contains r/text-section-2)
                (when (= body slack)
                  (:note response-body) => (:note slack))
                (when (= body email)
                  (:note response-body) => (:note email))))
            ;; verify a stakeholder update is created
            (count (su/list-stakeholder-updates conn r/slug)) => 1))))))