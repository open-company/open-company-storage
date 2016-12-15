(ns open-company.integration.entry-point
  "Tests about the links provided by the HATEOAS entry point."
  (:require [midje.sweet :refer :all]
            [oc.lib.rethinkdb.pool :as pool]
            [open-company.lib.test-setup :as ts]
            [open-company.lib.rest-api-mock :as mock]
            [open-company.lib.hateoas :as hateoas]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [oc.lib.slugify :refer (slugify)]
            [open-company.api.common :as common]
            [open-company.resources.company :as company]
            [open-company.representations.company :as company-rep]))

(defn get-links-by-rel [rel response]
  (let [links (:links (mock/body-from-response response))]
    (-> (group-by :rel links)
        (get rel))))

(def options (hateoas/options-response [:options :get]))

(with-state-changes [(before :contents (ts/setup-system!))
                     (after :contents (ts/teardown-system!))
                     (before :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                      (company/delete-all-companies! conn)
                                      (company/create-company! conn (company/->company r/open r/coyote))
                                      (company/create-company! conn (company/->company r/uni r/camus (slugify (:name r/uni))))
                                      (company/create-company! conn (company/->company r/buffer r/sartre))))
                     (after :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                     (company/delete-all-companies! conn)))]

  (facts "about links provided by entry point"

    (fact "link to company list is provided"
      (fact "if authenticated"
        (let [res (mock/api-request :get "/" {:auth mock/jwtoken-sartre})]
          (:status res) => 200
          (first (get-links-by-rel "company-list" res)) => truthy))
      (fact "if not authenticated"
        (let [res (mock/api-request :get "/" {:skip-auth true})]
          (:status res) => 200
          (first (get-links-by-rel "company-list" res)) => truthy)))

    (fact "link to create company"
      (fact "is provided if authenticated"
        (let [res (mock/api-request :get "/" {:auth mock/jwtoken-sartre})]
          (:status res) => 200
          (first (get-links-by-rel "company-create" res)) => truthy))
      (fact "is not provided if not authenticated"
        (let [res (mock/api-request :get "/" {:skip-auth true})]
          (:status res) => 200
          (first (get-links-by-rel "company-create" res)) => falsey)))

    (future-fact "public companies are listed")

    (fact "private companies associated with user are listed"
      (let [res (mock/api-request :get "/" {:auth mock/jwtoken-sartre})]
        (:status res) => 200
        (get-links-by-rel "company" res) => seq
        (-> (get-links-by-rel "company" res) first :name) => (:name r/buffer)))))