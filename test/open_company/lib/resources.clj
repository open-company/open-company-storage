(ns open-company.lib.resources
  "Namespace of data fixtures for use in tests.")

;; ----- Names / constants -----

(def ascii "test this")
(def unicode "私はガラスを食")
(def mixed-ascii-unicode (str "test " unicode))
(def long-unicode " -tHiS #$is%?-----ελληνικήalso-მივჰხვდემასჩემსაãالزجاجوهذالايؤلمني-slüg♜-♛-☃-✄-✈  - ")
(def names [ascii unicode mixed-ascii-unicode long-unicode])

(def TICKER "OPEN")
(def too-long "1234546")

(def bad-symbols [nil "" " " 42 42.7 {} [] #{}])
(def bad-names bad-symbols)

;; ----- Companies -----

(def OPEN {
  :symbol TICKER
  :name "Transparency, LLC"
  :currency "USD"
  :web {:company "https://opencompany.io/"}})

(def UNI {
  :symbol TICKER
  :name "$€¥£ &‼⁇ ∆∰≈ ☃♔☂Ǽ ḈĐĦ, LLC"
  :currency "USD"
  :web {:company "https://opencompany.io/"}})

;; ----- Reports -----

(def OPEN-2015-Q2 {
  :symbol "OPEN"
  :year 2015
  :period "Q2"
  :finances {
    :cash 173228
    :revenue 2767
    :costs 22184
    :comment "This is a comment."
  }
  :headcount {
    :founders 2
    :ft-employees 3
    :pt-employees 0
    :contractors 1
    :comment "This is another comment."
  }
  :compensation {
    :percentage false
    :founders 6357
    :employees 5899
    :contractors 2582
    :comment "More comments for you."
  }
})