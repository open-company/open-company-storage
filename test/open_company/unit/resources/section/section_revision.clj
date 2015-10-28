(ns open-company.unit.resources.section.section-revision
  (:require [midje.sweet :refer :all]
            [open-company.lib.check :as check]
            [open-company.lib.resources :as r]
            [open-company.resources.company :as c]
            [open-company.resources.section :as s]
            [open-company.db.pool :as pool]))

(reset! pool/rethinkdb-pool nil)
(pool/start 1 1)

(with-state-changes [(before :facts (do (c/delete-company r/slug)
                                        (c/create-company r/open r/coyote)))
                     (after :facts (c/delete-company r/slug))]

  (facts "about revising sections"

    (fact "when a sections is new"
        (s/put-section r/slug "update" r/text-section-1 r/coyote)
        (let [section (s/get-section r/slug "update")
              updated-at (:updated-at section)]
          (:author section) => r/coyote
          (:body section) => (:body r/text-section-1)
          (:title section) => (:title r/text-section-1)
          (check/timestamp? updated-at) => true
          (check/about-now? updated-at) => true
          updated-at => (:created-at section))
        (count (s/get-revisions r/slug "update")) => 1)

    (with-state-changes [(before :facts (s/put-section r/slug "finances" r/finances-section-1 r/coyote))]
    
      (facts "when the prior revision of the section DOESN'T have notes"

        (fact "when the update of the section DOES have a note"
          (s/put-section r/slug "finances" r/finances-notes-section-2 r/coyote)
          (let [section (s/get-section r/slug "finances")
                updated-at (:updated-at section)
                created-at (:created-at section)
                notes (:notes section)]
            (:author section) => r/coyote
            (:data section) => (:data r/finances-notes-section-2)
            (:title section) => (:title r/finances-notes-section-2)
            (check/timestamp? updated-at) => true
            (check/about-now? updated-at) => true
            (= updated-at created-at) => true
            (:body notes) => (get-in r/finances-notes-section-2 [:notes :body])
            (:author notes) => r/coyote
            (= updated-at created-at))
          (count (s/get-revisions r/slug "finances")) => 2)

        (fact "when the update of the section is by a different author"
          (s/put-section r/slug "finances" r/finances-section-2 r/camus)
          (let [section (s/get-section r/slug "finances")
                updated-at (:updated-at section)
                created-at (:created-at section)]
            (:author section) => r/camus
            (:data section) => (:data r/finances-section-2)
            (:title section) => (:title r/finances-section-2)
            (check/timestamp? updated-at) => true
            (check/about-now? updated-at) => true
            (= updated-at created-at) => true)
          (count (s/get-revisions r/slug "finances")) => 2)

        (future-fact "when the update of the section is over the time limit")

        (fact "when the update of the section is by the same author and is under the time limit"
          (check/delay-secs 1)
          (s/put-section r/slug "finances" r/finances-section-2 r/coyote)
          (let [section (s/get-section r/slug "finances")
                updated-at (:updated-at section)
                created-at (:created-at section)]
            (:author section) => r/coyote
            (:data section) => (:data r/finances-section-2)
            (:title section) => (:title r/finances-section-2)
            (check/timestamp? updated-at) => true
            (check/about-now? updated-at) => true
            (check/before? created-at updated-at) => true)
          (count (s/get-revisions r/slug "finances")) => 1)))


    (future-facts "when the prior revision of the section DOES have notes"

      (future-fact "when the update of the section DOESN'T have a note")

      (future-fact "when the update of the section is by a different author")

      (future-fact "when the update of the section is over the time limit")

      (future-fact "when the update of the section's note is by a different author")

      (future-fact "when the update of the section's note is over the time limit")

      (future-fact "when the update of the section & its note were by the same author & under the time limit"))))