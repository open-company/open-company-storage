(ns open-company.unit.resources.section.section-revision
  (:require [midje.sweet :refer :all]
            [open-company.lib.check :as check]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.lib.test-setup :as ts]
            [oc.lib.rethinkdb.pool :as pool]
            [open-company.config :as config]
            [open-company.resources.common :as common]
            [open-company.resources.company :as c]
            [open-company.resources.section :as s]))

;; ----- Test Cases -----

;; Updating a section with the Clojure API.

;; The system should support updating the company sections, and handle the following scenarios:

;; success - new section, author is member of the company

;; fail - new section, author is not a member of the company
;; fail - update the section, auther is not a member of the company

;; Section has NO prior note cases:

;; new revision - going from no note to having a note
;; new revision - different author than prior revision
;; new revision - prior update was too long ago

;; same revision - same author and not too long ago


;; Section HAS a prior note cases:

;; new revision - no longer has a note
;; new revision - different author than prior revision
;; new revision - prior update was too long ago

;; new revision - note is by a new author - TODO
;; new revision - note update was too long ago - TODO

;; same revision - same author and not too long ago

;; ----- Tests -----

(with-state-changes [(before :contents (ts/setup-system!))
                     (after :contents (ts/teardown-system!))
                     (before :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                      (c/delete-company conn r/slug)
                                      (c/create-company! conn (c/->company r/open r/coyote))))
                     (after :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
                                     (c/delete-company conn r/slug)))]

  (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]

    (facts "about revising sections"

      (facts "when a sections is new"

        (fact "creates a new section when the author is a member of the company org"
          (s/put-section conn r/slug :update r/text-section-1 r/coyote)
          (let [section (s/get-section conn r/slug :update)
                updated-at (:updated-at section)]
            (:author section) => (contains (common/author-for-user r/coyote))
            (:body section) => (:body r/text-section-1)
            (:title section) => (:title r/text-section-1)
            (check/timestamp? updated-at) => true
            (check/about-now? updated-at) => true
            updated-at => (:created-at section))
          (count (s/get-revisions conn r/slug :update)) => 1)

        (fact "fails to create a new section when the author is NOT a member of the company org"
          (s/put-section conn r/slug :update r/text-section-1 r/sartre) => false
          (s/get-section conn r/slug :update) => nil
          (count (s/get-revisions conn r/slug :update)) => 0))

      (with-state-changes [(before :facts (s/put-section conn r/slug :finances r/finances-section-1 r/coyote))]

        (fact "fails to update a section when the author is NOT a member of the company org"
          (let [original-section (s/get-section conn r/slug :finances)]
            (s/put-section conn r/slug :finances r/finances-section-2 r/sartre) => false
            (s/get-section conn r/slug :finances) => original-section
            (count (s/get-revisions conn r/slug :finances)) => 1))

        (fact "creates a new revision when the update of the section is by a different author"
          (s/put-section conn r/slug :finances r/finances-section-2 r/camus)
          (let [section (s/get-section conn r/slug :finances)
                updated-at (:updated-at section)
                created-at (:created-at section)]
            (:author section) => (contains (common/author-for-user r/camus))
            (:data section) => (:data r/finances-section-2)
            (:title section) => (:title r/finances-section-2)
            (check/timestamp? updated-at) => true
            (check/about-now? updated-at) => true
            (= updated-at created-at) => true)
          (count (s/get-revisions conn r/slug :finances)) => 2)

        (fact "creates a new revision when the update of the section is over the time limit"
          (db/postdate conn r/slug :finances) ; long enough ago to trigger a new revision
          (s/put-section conn r/slug :finances r/finances-section-2 r/coyote)
          (let [section (s/get-section conn r/slug :finances)
                updated-at (:updated-at section)
                created-at (:created-at section)]
            (:author section) => (contains (common/author-for-user r/coyote))
            (:data section) => (:data r/finances-section-2)
            (:title section) => (:title r/finances-section-2)
            (check/timestamp? updated-at) => true
            (check/about-now? updated-at) => true
            (= updated-at created-at) => true)
          (count (s/get-revisions conn r/slug :finances)) => 2)

        (fact "updates existing revision when the update of the section is by the same author & is under the time limit"
          (check/delay-secs 1) ; not long enough to trigger a new revision
          (s/put-section conn r/slug :finances r/finances-section-2 r/coyote)
          (let [section (s/get-section conn r/slug :finances)
                updated-at (:updated-at section)
                created-at (:created-at section)]
            (:author section) => (contains (common/author-for-user r/coyote))
            (:data section) => (:data r/finances-section-2)
            (:title section) => (:title r/finances-section-2)
            (check/timestamp? updated-at) => true
            (check/about-now? updated-at) => true
            (check/before? created-at updated-at) => true)
          (count (s/get-revisions conn r/slug :finances)) => 1)))))