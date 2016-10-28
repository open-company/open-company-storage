(ns open-company.unit.resources.stakeholder-update.distinct-updates
  (:require [midje.sweet :refer :all]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [open-company.lib.resources :as res]
            [open-company.resources.common :as common]
            [open-company.resources.stakeholder-update :as su]))

;; ----- Utility Functions -----

(defn- update-for [{:keys [title medium created-at author]}]
  {:title (or title "")
   :medium (or medium :link)
   :created-at (if created-at (f/unparse common/timestamp-format created-at) (common/current-timestamp))
   :author (or author res/coyote)})

(defn- in-order [updates] (reverse (sort-by :created-at updates)))

(defn- ago [interval] (t/minus (t/now) interval))

;; ----- Tests -----

(facts "about the distinct update hueristic"

  (facts "and degenerate cases"
    (su/distinct-updates nil) => (throws java.lang.AssertionError)
    (su/distinct-updates []) => []
    (let [one-update [(update-for {})]]
      (su/distinct-updates one-update) => one-update))

  (facts "about different causes of distinction"

    (fact "different title"
      (let [two-updates [(update-for {:title "One"}) (update-for {:title "Two" :created-at (ago (t/hours 1))})]]
        ; 2 -> 2 distinct
        (su/distinct-updates two-updates) => (in-order two-updates)))

    (facts "different medium"
      (let [two-updates [(update-for {}) (update-for {:medium :email :created-at (ago (t/hours 1))})]]
        ; 2 -> 2 distinct
        (su/distinct-updates two-updates) => (in-order two-updates))
      (let [two-updates [(update-for {:medium :slack}) (update-for {:medium :email :created-at (ago (t/hours 1))})]]
        ; 2 -> 2 distinct
        (su/distinct-updates two-updates) => (in-order two-updates)))

    (fact "different author"
      (let [two-updates [(update-for {}) (update-for {:author res/camus :created-at (ago (t/hours 1))})]]
        ; 2 -> 2 distinct
        (su/distinct-updates two-updates) => (in-order two-updates)))

    (fact "different day"
      (let [two-updates [(update-for {}) (update-for {:created-at (ago (t/hours 25))})]]
        ; 2 -> 2 distinct
        (su/distinct-updates two-updates) => (in-order two-updates))))

  (facts "and de-duplication"

    (facts "because there is the same title, medium and author, and within a day"
      (let [two-updates [(update-for {}) (update-for {:created-at (ago (t/hours 1))})]]
        ; 2 -> 1 distinct
        (su/distinct-updates two-updates) => [(first (in-order two-updates))])
      (let [two-updates [(update-for {}) (update-for {:created-at (ago (t/hours 23))})]]
        ; 2 -> 1 distinct
        (su/distinct-updates two-updates) => [(first (in-order two-updates))]))

    (facts "about complex scenarios"
      (let [
            ; 3 non-distinct links
            link1 (update-for {})
            link2 (update-for {:created-at (ago (t/hours 1))})
            link3 (update-for {:created-at (ago (t/hours 2))})
            ; 2 non-distinct links by a different author
            link4 (update-for {:author res/camus :created-at (ago (t/hours 3))})
            link5 (update-for {:author res/camus :created-at (ago (t/hours 4))})]
        (su/distinct-updates [link1 link2 link3 link4 link5]) => (in-order [link1 link4]))

      (let [
            ; 4 non-distinct emails
            email1 (update-for {:medium :email :created-at (ago (t/hours 5))})
            email2 (update-for {:medium :email :created-at (ago (t/hours 6))})
            email3 (update-for {:medium :email :created-at (ago (t/hours 7))})
            email4 (update-for {:medium :email :created-at (ago (t/hours 8))})
            ; 2 distinct emails by a different author
            email5 (update-for {:medium :email :author res/camus :created-at (ago (t/hours 9))})
            email6 (update-for {:medium :email :author res/camus :created-at (t/minus (t/now) (t/hours 34))})]
        (su/distinct-updates [email1 email2 email3 email4 email5 email6]) => (in-order [email1 email5 email6]))

      (let [
            ; 2 non-distinct slacks
            slack1 (update-for {:medium :slack :created-at (ago (t/hours 10))})
            slack2 (update-for {:medium :slack :created-at (ago (t/hours 11))})
            ; 3 slacks, different titles
            slack3 (update-for {:medium :slack :title "1" :created-at (ago (t/hours 12))})
            slack4 (update-for {:medium :slack :title "2" :created-at (ago (t/hours 13))})
            slack5 (update-for {:medium :slack :title "3" :created-at (ago (t/hours 14))})]
        (su/distinct-updates [slack1 slack2 slack3 slack4 slack5]) => (in-order [slack1 slack3 slack4 slack5]))

      (let [
            ; 3 non-distinct links
            link1 (update-for {})
            link2 (update-for {:created-at (ago (t/hours 1))})
            link3 (update-for {:created-at (ago (t/hours 2))})
            ; 2 non-distinct links by a different author
            link4 (update-for {:author res/camus :created-at (ago (t/hours 3))})
            link5 (update-for {:author res/camus :created-at (ago (t/hours 4))})
            ; 4 non-distinct emails
            email1 (update-for {:medium :email :created-at (ago (t/hours 5))})
            email2 (update-for {:medium :email :created-at (ago (t/hours 6))})
            email3 (update-for {:medium :email :created-at (ago (t/hours 7))})
            email4 (update-for {:medium :email :created-at (ago (t/hours 8))})
            ; 2 distinct emails by a different author
            email5 (update-for {:medium :email :author res/camus :created-at (ago (t/hours 9))})
            email6 (update-for {:medium :email :author res/camus :created-at (t/minus (t/now) (t/hours 34))})
            ; 2 non-distinct slacks
            slack1 (update-for {:medium :slack :created-at (ago (t/hours 10))})
            slack2 (update-for {:medium :slack :created-at (ago (t/hours 11))})
            ; 3 slacks, different titles
            slack3 (update-for {:medium :slack :title "1" :created-at (ago (t/hours 12))})
            slack4 (update-for {:medium :slack :title "2" :created-at (ago (t/hours 13))})
            slack5 (update-for {:medium :slack :title "3" :created-at (ago (t/hours 14))})]
        (su/distinct-updates [link1 link2 link3 link4 link5
                              email1 email2 email3 email4 email5 email6
                              slack1 slack2 slack3 slack4 slack5]) => (in-order [link1 link4
                                                                                 email1 email5
                                                                                 slack1 slack3 slack4 slack5
                                                                                 email6])))))