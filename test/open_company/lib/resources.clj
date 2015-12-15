(ns open-company.lib.resources
  "Namespace of data fixtures for use in tests.")

;; ----- Names / constants -----

(def ascii "test this")
(def unicode "私はガラスを食")
(def mixed-ascii-unicode (str "test " unicode))
(def long-unicode " -tHiS #$is%?-----ελληνικήalso-მივჰხვდემასჩემსაãالزجاجوهذالايؤلمني-slüg♜-♛-☃-✄-✈  - ")
(def names [ascii unicode mixed-ascii-unicode long-unicode])

(def slug "open")
(def too-long "1234546")

(def bad-symbols [nil "" " " 42 42.7 {} [] #{}])
(def bad-names bad-symbols)

;; ----- Authors -----

(def coyote {
  :user-id "123456"
  :image "http://www.emoticonswallpapers.com/avatar/cartoons/Wiley-Coyote-Dazed.jpg"
  :name "Wile E. Coyote"
  :org-id "98765"})

(def camus {
  :user-id "1960-01-04"
  :image "http://www.brentonholmes.com/wp-content/uploads/2010/05/albert-camus1.jpg"
  :name "Albert Camus"
  :org-id "98765"})

(def sartre {
  :user-id "1980-06-21"
  :image "http://existentialismtoday.com/wp-content/uploads/2015/11/sartre_22.jpg"
  :name "Jean-Paul Sartre"
  :org-id "87654"})

;; ----- Companies -----

(def open {
  :slug slug
  :name "Transparency, LLC"
  :currency "USD"
  :org-id "98765"})

(def UNI {
  :symbol slug
  :name "$€¥£ &‼⁇ ∆∰≈ ☃♔☂Ǽ ḈĐĦ, LLC"
  :currency "FKP"
  :org-id "98765"})

;; ----- Sections -----

(def text-section-1 {
  :title "Text Section 1"
  :body "<p>This is a test.</p>"})

(def text-section-2 {
  :title "Text Section 2"
  :body "<p>This is also a test.</p>"})

(def finances-section-1 {
  :title "Finances Section 1"
  :data [
    {
      :period "2015-06"
      :cash 42
      :revenue 1
      :costs 1
    }]})

(def finances-section-2 {
  :title "Finances Section 2"
  :data [
    {
      :period "2015-06"
      :cash 42
      :revenue 1
      :costs 1
    }
    {
      :period "2015-07"
      :cash 42
      :revenue 7
      :costs 7
    }]})

(def finances-notes-section-1 {
  :title "Finances with Notes Section 1"
  :data [
    {
      :period "2015-06"
      :cash 42
      :revenue 1
      :costs 1
    }]
    :notes {
      :body "1 dollar."}})

(def finances-notes-section-2 {
  :title "Finances with Notes Section 2"
  :data [
    {
      :period "2015-06"
      :cash 42
      :revenue 1
      :costs 1
    }
    {
      :period "2015-07"
      :cash 42
      :revenue 7
      :costs 7
    }]
    :notes {
      :body "7 dollars."}})