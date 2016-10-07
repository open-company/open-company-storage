(ns open-company.unit.resources.section.section-retrieval
  (:require [midje.sweet :refer :all]
            [open-company.lib.check :as check]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.lib.test-setup :as ts]
            [oc.lib.rethinkdb.pool :as pool]
            [open-company.config :as config]
            [open-company.resources.common :as common]
            [open-company.resources.company :as c]
            [open-company.resources.section :as section]))

;; ----- Test Cases -----

(with-state-changes [(before :contents (ts/setup-system!))
                     (after :contents (ts/teardown-system!))
                     (before :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                      (c/delete-company! conn r/slug)
                                      (c/create-company! conn (c/->company r/open r/coyote))
                                      (section/put-section conn r/slug :update r/text-section-1 r/coyote)
                                      (section/put-section conn r/slug :finances r/finances-section-1 r/coyote)
                                      (section/put-section conn r/slug :custom-a1b2 r/text-section-1 r/coyote)
                                      (section/put-section conn r/slug :custom-r2d2 r/text-section-2 r/coyote)))
                     (after :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                     (c/delete-company! conn r/slug)))]

  (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]

    (facts "about retrieving latest sections"

      (fact "when it is a content section"
        (section/get-section conn r/slug :update (common/current-timestamp)) => (contains r/text-section-1)
        (count (section/list-revisions conn r/slug :update)) => 1)

      (fact "when it is a data section"
        (section/get-section conn r/slug :finances (common/current-timestamp)) => (contains r/finances-section-1)
        (count (section/list-revisions conn r/slug :finances)) => 1)

      (fact "when it i a custom section"
        (section/get-section conn r/slug :custom-a1b2 (common/current-timestamp)) => (contains r/text-section-1)
        (count (section/list-revisions conn r/slug :custom-a1b2)) => 1))))