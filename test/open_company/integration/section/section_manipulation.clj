(ns open-company.integration.section.section-manipulation
  (:require [midje.sweet :refer :all]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.api.common :as common]
            [open-company.resources.common :as common-res]
            [open-company.resources.company :as company]
            [open-company.resources.section :as section]
            [open-company.representations.company :as company-rep]))

;; ----- Startup -----

(db/test-startup)

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
                     (before :facts (do (company/delete-all-companies!)
                                        (company/create-company! (company/->company r/open r/coyote))
                                        (section/put-section r/slug :update r/text-section-1 r/coyote)
                                        (section/put-section r/slug :finances r/finances-section-1 r/coyote)
                                        (section/put-section r/slug :team r/text-section-2 r/coyote)
                                        (section/put-section r/slug :help r/text-section-1 r/coyote)
                                        (section/put-section r/slug :diversity r/text-section-2 r/coyote)
                                        (section/put-section r/slug :values r/text-section-1 r/coyote)))
                     (after :facts (company/delete-all-companies!))]

  (facts "about failing to reorder sections"

    (fact "with an invalid JWToken"
      (let [new-order {:progress ["team" "update" "finances" "help"]
                       :company ["diversity" "values"]}
            response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}
                                                                        :auth mock/jwtoken-bad})]
        (:status response) => 401
        (:body response) => common/unauthorized)
      ;; verify the initial order is unchanged
      (let [db-company (company/get-company r/slug)]
        (:categories db-company) => categories
        (:sections db-company) => {:progress ["update" "team" "help"]
                                   :financial ["finances"]
                                   :company ["diversity" "values"]}))

    (fact "with no JWToken"
      (let [new-order {:progress ["team" "update" "finances" "help"]
                       :company ["diversity" "values"]}
            response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}
                                                                        :skip-auth true})]
        (:status response) => 401
        (:body response) => common/unauthorized)
      ;; verify the initial order is unchanged
      (let [db-company (company/get-company r/slug)]
        (:categories db-company) => categories
        (:sections db-company) => {:progress ["update" "team" "help"]
                                   :financial ["finances"]
                                   :company ["diversity" "values"]}))

    (fact "with an organization that doesn't match the company"
      (let [new-order {:progress ["team" "update" "finances" "help"]
                       :company ["diversity" "values"]}
            response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}
                                                                        :auth mock/jwtoken-sartre})]
        (:status response) => 403
        (:body response) => common/forbidden)
      ;; verify the initial order is unchanged
      (let [db-company (company/get-company r/slug)]
        (:categories db-company) => categories
        (:sections db-company) => {:progress ["update" "team" "help"]
                                   :financial ["finances"]
                                   :company ["diversity" "values"]}))

    (fact "with no company matching the company slug"
      (let [new-order {:progress ["team" "update" "finances" "help"]
                       :company ["diversity" "values"]}
            response (mock/api-request :patch (company-rep/url "foo") {:body {:sections new-order}})]
        (:status response) => 404
        (:body response) => "")
      ;; verify the initial order is unchanged
      (let [db-company (company/get-company r/slug)]
        (:categories db-company) => categories
        (:sections db-company) => {:progress ["update" "team" "help"]
                                   :financial ["finances"]
                                   :company ["diversity" "values"]})))

  (facts "about section reordering"

    ;; verify the initial order
    (let [db-company (company/get-company r/slug)]
      (:categories db-company) => categories
      (:sections db-company) => {:progress ["update" "team" "help"]
                                 :financial ["finances"]
                                 :company ["diversity" "values"]})

    (facts "when the new order is valid"

      (fact "the section order in the progress category can be gently adjusted"
        (let [new-order {:progress ["team" "update" "finances" "help"]
                         :company ["diversity" "values"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})]
          (:status response) => 200
          (:sections (mock/body-from-response response)) => new-order
          ;; verify the new order
          (:sections (company/get-company r/slug)) => new-order))

      (fact "the sections order in the progress category can be greatly adjusted"
        (let [new-order {:progress ["help" "team" "finances" "update"]
                         :company ["diversity" "values"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})]
          (:status response) => 200
          (:sections (mock/body-from-response response)) => new-order
          ;; verify the new order
          (:sections (company/get-company r/slug)) => new-order))

      (fact "the section order in the company category can be adjusted"
        (let [new-order {:progress ["update" "finances" "team" "help"]
                         :company ["values" "diversity"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})]
          (:status response) => 200
          (:sections (mock/body-from-response response)) => new-order
          ;; verify the new order
          (:sections (company/get-company r/slug)) => new-order))

      (fact "the section order in the progress and company category can both be adjusted at once"
        (let [new-order {:progress ["help" "team" "finances" "update"]
                         :company ["values" "diversity"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})]
          (:status response) => 200
          (:sections (mock/body-from-response response)) => new-order
          ;; verify the new order
          (:sections (company/get-company r/slug)) => new-order)))

    (future-facts "when the new order is NOT valid"))

  (facts "about placeholder section removal"
    (let [slug     "hello-world"
          payload  {:name "Hello World" :description "x"}
          response (mock/api-request :post "/companies" {:body payload})
          company  (company/get-company slug)]
      ;; ensure all placeholder sections are in company
      (:sections company) => {:progress ["update" "growth" "challenges" "team" "product"]
                              :financial ["finances"]
                              :company ["values" "mission"]}
      (:growth company) => truthy
      (:challenges company) => truthy
      (:team company) => truthy
      (:product company) => truthy
      (:mission company) => truthy
      (let [new-set {:progress ["update" "finances" "help"]
                     :company ["values"]}
            response (mock/api-request :patch (company-rep/url slug) {:body {:sections new-set}})
            body     (mock/body-from-response response)
            company  (company/get-company slug)]
        (:status response) => 200
        (:sections company) => new-set
        (:growth company) => falsey
        (:challenges company) => falsey
        (:team company) => falsey
        (:product company) => falsey
        (:mission company) => falsey)))

  (facts "about section removal"

    ;; verify the initial set of sections
    (:sections (company/get-company r/slug)) => {:progress ["update" "team" "help"]
                                                 :financial ["finances"]
                                                 :company ["diversity" "values"]}

      (fact "a section can be removed from the progress category"
        (let [new-set {:progress ["update" "finances" "help"]
                         :company ["diversity" "values"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-set}})
              body (mock/body-from-response response)
              db-company (company/get-company r/slug)]
          (:status response) => 200
          (:sections body) => new-set
          (:update body) => (contains r/text-section-1)
          (:finances body) => (contains r/finances-section-1)
          (:team body) => nil
          (:help body) => (contains r/text-section-1)
          (:diversity body) => (contains r/text-section-2)
          (:values body) => (contains r/text-section-1)
          ;; verify the new set
          (:sections db-company) => new-set
          (:update db-company) => (contains r/text-section-1)
          (:finances db-company) => (contains r/finances-section-1)
          (:team db-company) => nil
          (:help db-company) => (contains r/text-section-1)
          (:diversity db-company) => (contains r/text-section-2)
          (:values db-company) => (contains r/text-section-1)))

      (fact "multiple sections can be removed from the progress category"
        (let [new-set {:progress ["finances"]
                       :company ["diversity" "values"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-set}})
              body (mock/body-from-response response)
              db-company (company/get-company r/slug)]
          (:status response) => 200
          (:sections body) => new-set
          (:update body) => nil
          (:finances body) => (contains r/finances-section-1)
          (:team body) => nil
          (:help body) => nil
          (:diversity body) => (contains r/text-section-2)
          (:values body) => (contains r/text-section-1)
          ;; verify the new set
          (:sections db-company) => new-set
          (:update db-company) => nil
          (:finances db-company) => (contains r/finances-section-1)
          (:team db-company) => nil
          (:help db-company) => nil
          (:diversity db-company) => (contains r/text-section-2)
          (:values db-company) => (contains r/text-section-1)))

      (fact "a section can be removed from the company category"
        (let [new-order {:progress ["update" "finances" "team" "help"]
                         :company ["diversity"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})
              body (mock/body-from-response response)
              db-company (company/get-company r/slug)]
          (:status response) => 200
          (:sections body) => new-order
          (:update body) => (contains r/text-section-1)
          (:finances body) => (contains r/finances-section-1)
          (:team body) => (contains r/text-section-2)
          (:help body) => (contains r/text-section-1)
          (:diversity body) => (contains r/text-section-2)
          (:values body) => nil
          ;; verify the new set
          (:sections db-company) => new-order
          (:update db-company) => (contains r/text-section-1)
          (:finances db-company) => (contains r/finances-section-1)
          (:team db-company) => (contains r/text-section-2)
          (:help db-company) => (contains r/text-section-1)
          (:diversity db-company) => (contains r/text-section-2)
          (:values db-company) => nil))

      (fact "sections can be removed from the progress and company categories at once"
        (let [new-order {:progress ["update" "help"]
                         :company ["values"]}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-order}})
              body (mock/body-from-response response)
              db-company (company/get-company r/slug)]
          (:status response) => 200
          (:sections body) => new-order
          (:update body) => (contains r/text-section-1)
          (:finances body) => nil
          (:team body) => nil
          (:help body) => (contains r/text-section-1)
          (:diversity body) => nil
          (:values body) => (contains r/text-section-1)
          ;; verify the new set
          (:sections db-company) => new-order
          (:update db-company) => (contains r/text-section-1)
          (:finances db-company) => nil
          (:team db-company) => nil
          (:help db-company) => (contains r/text-section-1)
          (:diversity db-company) => nil
          (:values db-company) => (contains r/text-section-1))))
  
  (facts "about adding sections"

    (fact "that don't really exist"
      (let [new-sections {:progress ["health"] :company [] :financial []}
            response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})
            body (mock/body-from-response response)]
        (:status response) => 200))

    (facts "without any section content"
    
      (fact "that never existed"
        (let [new-sections {:progress ["highlights"] :company [] :financial []}
              response (mock/api-request :patch (company-rep/url r/slug) {:body {:sections new-sections}})
              body (mock/body-from-response response)
              resp-highlights (:highlights body)
              db-company (company/get-company r/slug)
              db-highlights (:highlights db-company)
              placeholder (dissoc (common-res/section-by-name :highlights) :section-name :core)]
          (:status response) => 200
          (:sections body) => new-sections
          ; verify placeholder flag and content in response
          (:placeholder resp-highlights) => true 
          resp-highlights => (contains placeholder)
          ; verify placeholder flag and content in DB
          (:placeholder db-highlights) => true
          db-highlights => (contains placeholder)))

        (future-fact "that used to exist"))

    (future-fact "with section content")

    (future-fact "with too much content"
      
      (future-fact "extra properties aren't allowed")

      (future-fact "read/only properties are ignored"))))