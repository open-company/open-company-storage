(ns open-company.unit.resources.section.section-revision
  (:require [midje.sweet :refer :all]
            [open-company.lib.check :as check]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.config :as config]
            [open-company.resources.common :as common]
            [open-company.resources.company :as c]
            [open-company.resources.section :as s]))

;; ----- Startup -----

(db/test-startup)

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

(with-state-changes [(before :facts (do (c/delete-company r/slug)
                                        (c/create-company! (c/->company r/open r/coyote))))
                     (after :facts (c/delete-company r/slug))]

  (facts "about revising sections"

    (facts "when a sections is new"

      (fact "creates a new section when the author is a member of the company org"
        (s/put-section r/slug :update r/text-section-1 r/coyote)
        (let [section (s/get-section r/slug :update)
              updated-at (:updated-at section)]
          (:author section) => (contains (common/author-for-user r/coyote))
          (:body section) => (:body r/text-section-1)
          (:title section) => (:title r/text-section-1)
          (check/timestamp? updated-at) => true
          (check/about-now? updated-at) => true
          updated-at => (:created-at section))
        (count (s/get-revisions r/slug :update)) => 1)

      (fact "fails to create a new section when the author is NOT a member of the company org"
        (s/put-section r/slug :update r/text-section-1 r/sartre) => false
        (s/get-section r/slug :update) => nil
        (count (s/get-revisions r/slug :update)) => 0))

    (with-state-changes [(before :facts (s/put-section r/slug :finances r/finances-section-1 r/coyote))]

      (fact "fails to update a section when the author is NOT a member of the company org"
        (let [original-section (s/get-section r/slug :finances)]
          (s/put-section r/slug :finances r/finances-notes-section-2 r/sartre) => false
          (s/get-section r/slug :finances) => original-section
          (count (s/get-revisions r/slug :finances)) => 1))

      (facts "when the prior revision of the section DOESN'T have notes"

        (fact "creates a new revision when the update of the section DOES have a note"
          (s/put-section r/slug :finances r/finances-notes-section-2 r/coyote)
          (let [section (s/get-section r/slug :finances)
                updated-at (:updated-at section)
                created-at (:created-at section)
                notes (:notes section)]
            (:author section) => (contains (common/author-for-user r/coyote))
            (:data section) => (:data r/finances-notes-section-2)
            (:title section) => (:title r/finances-notes-section-2)
            (check/timestamp? updated-at) => true
            (check/about-now? updated-at) => true
            (= updated-at created-at) => true
            (:body notes) => (get-in r/finances-notes-section-2 [:notes :body])
            (:author notes) => (contains (common/author-for-user r/coyote))
            (:updated-at notes) => updated-at)
          (count (s/get-revisions r/slug :finances)) => 2)

        (fact "creates a new revision when the update of the section is by a different author"
          (s/put-section r/slug :finances r/finances-section-2 r/camus)
          (let [section (s/get-section r/slug :finances)
                updated-at (:updated-at section)
                created-at (:created-at section)]
            (:author section) => (contains (common/author-for-user r/camus))
            (:data section) => (:data r/finances-section-2)
            (:title section) => (:title r/finances-section-2)
            (check/timestamp? updated-at) => true
            (check/about-now? updated-at) => true
            (= updated-at created-at) => true)
          (count (s/get-revisions r/slug :finances)) => 2)

        (fact "creates a new revision when the update of the section is over the time limit"
          (db/postdate r/slug :finances) ; long enough ago to trigger a new revision
          (s/put-section r/slug :finances r/finances-section-2 r/coyote)
          (let [section (s/get-section r/slug :finances)
                updated-at (:updated-at section)
                created-at (:created-at section)]
            (:author section) => (contains (common/author-for-user r/coyote))
            (:data section) => (:data r/finances-section-2)
            (:title section) => (:title r/finances-section-2)
            (check/timestamp? updated-at) => true
            (check/about-now? updated-at) => true
            (= updated-at created-at) => true)
          (count (s/get-revisions r/slug :finances)) => 2)

        (fact "updates existing revision when the update of the section is by the same author & is under the time limit"
          (check/delay-secs 1) ; not long enough to trigger a new revision
          (s/put-section r/slug :finances r/finances-section-2 r/coyote)
          (let [section (s/get-section r/slug :finances)
                updated-at (:updated-at section)
                created-at (:created-at section)]
            (:author section) => (contains (common/author-for-user r/coyote))
            (:data section) => (:data r/finances-section-2)
            (:title section) => (:title r/finances-section-2)
            (check/timestamp? updated-at) => true
            (check/about-now? updated-at) => true
            (check/before? created-at updated-at) => true)
          (count (s/get-revisions r/slug :finances)) => 1)))

    (with-state-changes [(before :facts (s/put-section r/slug :finances r/finances-notes-section-1 r/coyote))]

      (facts "when the prior revision of the section DOES have notes"

        (fact "creates a new revision when the update of the section DOESN'T have a note"
          (s/put-section r/slug :finances r/finances-section-2 r/coyote)
          (let [section (s/get-section r/slug :finances)
                updated-at (:updated-at section)
                created-at (:created-at section)]
            (:author section) => (contains (common/author-for-user r/coyote))
            (:data section) => (:data r/finances-section-2)
            (:title section) => (:title r/finances-section-2)
            (:notes section) => nil
            (check/timestamp? updated-at) => true
            (check/about-now? updated-at) => true
            (= updated-at created-at) => true)
          (count (s/get-revisions r/slug :finances)) => 2)

        (fact "creates a new revision when the update of the section is by a different author"
          (s/put-section r/slug :finances r/finances-notes-section-2 r/camus)
          (let [section (s/get-section r/slug :finances)
                updated-at (:updated-at section)
                created-at (:created-at section)
                notes (:notes section)]
            (:author section) => (contains (common/author-for-user r/camus))
            (:data section) => (:data r/finances-notes-section-2)
            (:title section) => (:title r/finances-notes-section-2)
            (check/timestamp? updated-at) => true
            (check/about-now? updated-at) => true
            (= updated-at created-at) => true
            (:body notes) => (get-in r/finances-notes-section-2 [:notes :body])
            (:author notes) => (contains (common/author-for-user r/camus))
            (:updated-at notes) => updated-at)
          (count (s/get-revisions r/slug :finances)) => 2)

        (fact "creates a new revision when the update of the section is over the time limit"
          (db/postdate r/slug :finances) ; long enough ago to trigger a new revision
          (s/put-section r/slug :finances r/finances-notes-section-2 r/coyote)
          (let [section (s/get-section r/slug :finances)
                updated-at (:updated-at section)
                created-at (:created-at section)
                notes (:notes section)]
            (:author section) => (contains (common/author-for-user r/coyote))
            (:data section) => (:data r/finances-notes-section-2)
            (:title section) => (:title r/finances-notes-section-2)
            (check/timestamp? updated-at) => true
            (check/about-now? updated-at) => true
            (= created-at updated-at) => true
            (:body notes) => (get-in r/finances-notes-section-2 [:notes :body])
            (:author notes) => (contains (common/author-for-user r/coyote))
            (:updated-at notes) => updated-at)
          (count (s/get-revisions r/slug :finances)) => 2)

        (future-fact "creates a new revision when the update of the section's note is by a different author")

        (future-fact "creates a new revision when the update of the section's note is over the time limit")

        (fact "updates the existing revision when the update of the section & its note were by the same author &
          are under the time limit"
          (check/delay-secs 1) ; not long enough to trigger a new revision
          (s/put-section r/slug :finances r/finances-notes-section-2 r/coyote)
          (let [section (s/get-section r/slug :finances)
                updated-at (:updated-at section)
                created-at (:created-at section)]
            (:author section) => (contains (common/author-for-user r/coyote))
            (:data section) => (:data r/finances-notes-section-2)
            (:title section) => (:title r/finances-notes-section-2)
            (check/timestamp? updated-at) => true
            (check/about-now? updated-at) => true
            (check/before? created-at updated-at) => true)
          (count (s/get-revisions r/slug :finances)) => 1)))))
