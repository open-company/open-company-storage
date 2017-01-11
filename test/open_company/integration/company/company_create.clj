(ns open-company.integration.company.company-create
  (:require [cheshire.core :as json]
            [open-company.lib.test-setup :as ts]
            [oc.lib.rethinkdb.pool :as pool]
            [schema.test]
            [midje.sweet :refer :all]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.hateoas :as hateoas]
            [open-company.lib.resources :as r]
            [open-company.lib.bot :as bot]
            [open-company.resources.common :as common]
            [open-company.resources.company :as company]
            [open-company.resources.section :as section]
            [open-company.representations.company :as company-rep]))

;; ----- Test Cases -----

;; Creating companies with the REST API

;; The system should store newly created valid companies and handle the following scenarios:

;; OPTIONS

;; PUT / POST
;; all good - 201 Created
;; all good, no ticker symbol in body - 201 Created
;; all good, unicode in the body - 201 Created

;; bad, no JWToken - 401 Unauthorized
;; bad, invalid JWToken - 401 Unauthorized

;; bad, company slug has invalid characters
;; bad, company slug is too long
;; bad, company slug in body is different than the provided URL

;; no accept
;; no content type
;; no charset
;; conflicting reserved properties
;; wrong accept
;; wrong content type
;; no body
;; wrong charset
;; body not valid JSON
;; no name in body

;; ----- Tests -----

; (with-state-changes [(before :facts (company/delete-all-companies!))
;                      (after :facts (company/delete-all-companies!))]

;   (facts "about using the REST API to create valid new companies"

;     ;; all good - 201 Created
;     (fact "when the ticker symbol in the body and the URL match"
;       ;; Create the company
;       (let [response (mock/put-company-with-api r/TICKER r/OPEN)]
;         (:status response) => 201
;         (mock/response-mime-type response) => (mock/base-mime-type company-rep/media-type)
;         (mock/response-location response) => (company-rep/url r/TICKER)
;         (mock/json? response) => true
;         (hateoas/verify-company-links r/TICKER (:links (mock/body-from-response response))))
;       ;; Get the created company and make sure it's right
;       (company/get-company r/TICKER) => (contains r/OPEN)
;       ;; Reports are empty?
;       (report/report-count r/TICKER) => 0)))

(with-state-changes [(before :contents (ts/setup-system!))
                     (after :contents (ts/teardown-system!))
                     (before :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                      (company/delete-all-companies! conn)
                                      (company/create-company! conn (company/->company r/open r/coyote))))]
  
  ;; TODO JWToken things

  (facts "about creating companies"

    ; TODO POST with empty map causes NPE
    ; (fact "missing fields cause 422"
    ;   (let [payload  {}
    ;         response (mock/api-request :post "/companies" {:body payload})]
    ;     (:status response) => 422))

    ; TODO this no longer works since we let unknown custom sections in,
    ; will need to rework data or schema to allow this validation to happen
    ; (fact "superflous fields cause 422"
    ;   (let [payload  {:bogus "xx" :name "hello"}
    ;         response (mock/api-request :post "/companies" {:body payload})]
    ;     (:status response) => 422))

    (fact "a company can be created with just a name"
      (let [payload  {:name "Hello World"}
            response (mock/api-request :post "/companies" {:body payload})
            body     (mock/body-from-response response)]
        (:status response) => 201
        (:slug body) => "hello-world"
        (:name body) => "Hello World"))

    (facts "about triggering bot onboarding"
        (fact "trigger is sent if user is owner of Slack Team"
          (let [sqs-msg (atom nil)]
            (with-redefs [bot/send-trigger! #(reset! sqs-msg %)]
              (let [payload  {:name "Send-Trigger-Test"}
                    response (mock/api-request :post "/companies" {:body payload :auth mock/jwtoken-camus})]
                (:bot @sqs-msg) =>    {:id "abc" :token "xyz"}
                ; Needs update of mock JWToken used
                ; (:script @sqs-msg) => {:id :onboard :params {:user/name "Albert" :company/name "Send-Trigger-Test"
                ;                                              :company/slug "send-trigger-test" :company/currency "USD"}}
                (:receiver @sqs-msg) => {:type :user :id "slack:1960-01-04"}
                (str "Bearer " (:api-token @sqs-msg)) => mock/jwtoken-camus)))
        (fact "trigger is sent if user is owner of Slack Team"
          (let [sqs-msg (atom nil)]
            (with-redefs [bot/send-trigger! #(reset! sqs-msg %)]
              (let [payload  {:name "Hello World" :description "x"}
                    response (mock/api-request :post "/companies" {:body payload})]
                @sqs-msg) => nil)))))

    (facts "about conflicting company slugs"
      
      (fact "used company slugs get a new suggested slug"
        (let [payload  {:name "Open"}
              response (mock/api-request :post "/companies" {:body payload})
              body     (mock/body-from-response response)]
          (:status response) => 201
          (:slug body) => "open-1"
          (:name body) => "Open"))
      
      (fact "reserved company slugs get a new suggested slug"
        (let [payload  {:name "About"}
              response (mock/api-request :post "/companies" {:body payload})
              body     (mock/body-from-response response)]
          (:status response) => 201
          (:slug body) => "about-1"
          (:name body) => "About")))


    (facts "slug"
      (fact "provided slug is taken causes 422"
        (let [payload  {:slug "open" :name "hello"}
              response (mock/api-request :post "/companies" {:body payload})]
          (:status response) => 422))
       (fact "provided slug is reserved causes 422"
        (let [payload  {:slug "about" :name "hello"}
              response (mock/api-request :post "/companies" {:body payload})]
          (:status response) => 422))
      (fact "provided slug does not follow slug format causes 422"
        (let [payload  {:slug "under_score" :name "hello"}
              response (mock/api-request :post "/companies" {:body payload})]
          (:status response) => 422)))

    (facts "sections"

      ; TODO this should work but doesn't ,it seems we don't realize this section isn't one of ours or a custom
      ; section since they never pass it in as a member of :sections
      ; (fact "unknown sections cause 422"
      ;  (let [payload  {:name "hello" :unknown-section {}}
      ;         response (mock/api-request :post "/companies" {:body payload})]
      ;    (:status response) => 422))

      (facts "known user supplied sections"
        (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
          
          (let [diversity {:title "Diversity" :headline "All the peoples." :body "Belong to us."}
                payload  {:name "Diverse Co" :description "Diversity is important to us." :diversity diversity}
                response (mock/api-request :post "/companies" {:body payload})
                company  (company/get-company conn "diverse-co")
                sect     (section/get-section conn "diverse-co" :diversity)]
            
            (fact "are added with author, updated-at, image and description"
              (:status response) => 201
              sect => (contains diversity)
              (:placeholder sect) => falsey
              (:author sect) => truthy
              (:updated-at sect) => truthy
              (:description sect) => (:description (common/sections-by-name :diversity)))))))))