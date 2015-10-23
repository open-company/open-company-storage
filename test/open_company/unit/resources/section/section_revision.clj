(ns open-company.unit.resources.section.section-revision
  (:require [midje.sweet :refer :all]
            [open-company.lib.check :as check]
            [open-company.lib.resources :as r]
            [open-company.resources.company :as c]))

(future-facts "about revising sections"

  (future-fact "when a sections is new")

  (future-facts "when the update DOESN'T have notes"

    (future-fact "when the prior revision of the section DID have a note")

    (future-fact "when the prior revision of the section was by a different author")

    (future-fact "when the prior revision of the section was over the time limit ago")

    (future-fact "when the prior revision of the section was by the same author and under the time limit ago"))


  (future-facts "when the update DOES have notes"

    (future-fact "when the prior revision of the section DIDN'T have a note")

    (future-fact "when the prior revision of the section was by a different author")

    (future-fact "when the prior revision of the section was over the time limit")

    (future-fact "when the prior revision of the section's note was by a different author")

    (future-fact "when the prior revision of the section's note was over the time limit ago")

    (future-fact "when the prior revision of the section & its note were by the same author & under the time limit ago")))