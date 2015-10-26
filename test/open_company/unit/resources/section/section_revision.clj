(ns open-company.unit.resources.section.section-revision
  (:require [midje.sweet :refer :all]
            [juxt.iota :refer (given)]
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
        (s/put-section r/slug "update" r/text-section-1 r/coyote )
        (let [section (s/get-section r/slug "update")
              updated-at (:updated-at section)]
          (given section
            :author := r/coyote
            :body := (:body r/text-section-1)
            :title := (:title r/text-section-1))
          (check/timestamp? updated-at) => true
          (check/about-now? updated-at) => true
          updated-at => (:created-at section))
        (count (s/get-revisions r/slug "update")) => 1)

    (future-facts "when the prior revision of the section DOESN'T have notes"

      (future-fact "when the update of the section DOES have a note")

      (future-fact "when the update of the section is by a different author")

      (future-fact "when the update of the section is over the time limit")

      (future-fact "when the update of the section is by the same author and is under the time limit"))


    (future-facts "when the prior revision of the section DOES have notes"

      (future-fact "when the update of the section DOESN'T have a note")

      (future-fact "when the update of the section is by a different author")

      (future-fact "when the update of the section is over the time limit")

      (future-fact "when the update of the section's note is by a different author")

      (future-fact "when the update of the section's note is over the time limit")

      (future-fact "when the update of the section & its note were by the same author & under the time limit"))))