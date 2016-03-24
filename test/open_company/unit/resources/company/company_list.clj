(ns open-company.unit.resources.company.company-list
  (:require [midje.sweet :refer :all]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.lib.test-setup :as ts]
            [open-company.db.pool :as pool]
            [open-company.lib.slugify :refer (slugify)]
            [open-company.resources.company :as c]))

;; ----- Tests -----

(with-state-changes [(before :contents (ts/setup-system!))
                     (after :contents (ts/teardown-system!))
                     (before :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                      (c/delete-all-companies! conn)
                                      (c/create-company! conn (c/->company r/open r/coyote))
                                      (c/create-company! conn (c/->company r/uni r/camus (slugify (:name r/uni))))
                                      (c/create-company! conn (c/->company r/buffer r/sartre))))
                     (after :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                     (c/delete-all-companies! conn)))]

  (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
    (facts "about listing companies"
      (fact "all existing companies are listed"
        (map :name (c/list-companies conn)) => (just (set (map :name [r/open r/uni r/buffer]))))

      (fact "removed companies are not listed"
        (c/delete-company conn (:slug r/buffer))
        (map :name (c/list-companies conn)) => (just (set (map :name [r/open r/uni]))))

      (fact "companies can be queried via secondary indexes"
        (count (c/get-companies-by-index conn "org-id" (:org-id r/sartre))) => 1
        (:slug (first (c/get-companies-by-index conn "org-id" (:org-id r/sartre)))) => (:slug r/buffer)))))