(ns open-company.lib.resources
  "Namespace of data fixtures for use in tests.")

;; ----- Names / constants -----

(def ascii-name "test this")
(def unicode-name "私はガラスを食")
(def mixed-name (str "test " unicode-name))
(def long-unicode-name " -tHiS #$is%?-----ελληνικήalso-მივჰხვდემასჩემსაãالزجاجوهذالايؤلمني-slüg♜-♛-☃-✄-✈  - ")
(def names [ascii-name unicode-name mixed-name long-unicode-name])

(def ok "OPEN")
(def too-long "1234546")

(def bad-symbols [nil "" " " 42 42.7 {} [] #{}])
(def bad-names bad-symbols)

(def oc {:symbol ok :name "Transparency, LLC" :url "https://opencompany.io/"})