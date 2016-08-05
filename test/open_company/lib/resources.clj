(ns open-company.lib.resources
  "Namespace of data fixtures for use in tests."
  (:require [clj-time.core :as t]))

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
  :user-id "slack:123456"
  :name "coyote"
  :real-name "Wile E. Coyote"
  :avatar "http://www.emoticonswallpapers.com/avatar/cartoons/Wiley-Coyote-Dazed.jpg"
  :email "wile.e.coyote@acme.com"
  :owner false
  :admin false
  :expire (t/plus (t/now) (t/days 1))
  :org-id "slack:98765"})

(def camus {
  :user-id "slack:1960-01-04"
  :name "camus"
  :real-name "Albert Camus"
  :avatar "http://www.brentonholmes.com/wp-content/uploads/2010/05/albert-camus1.jpg"
  :email "albert@combat.org"
  :owner true
  :admin true
  :expire (t/plus (t/now) (t/days 1))
  :org-id "slack:98765"
  :bot {:id "abc" :token "xyz"}})

(def sartre {
  :user-id "slack:1980-06-21"
  :name "sartre"
  :real-name "Jean-Paul Sartre"
  :avatar "http://existentialismtoday.com/wp-content/uploads/2015/11/sartre_22.jpg"
  :email "sartre@lyceela.org"
  :owner true
  :admin true
  :expire (t/plus (t/now) (t/days 1))
  :org-id "slack:87654"})

;; ----- Companies ----
(def open {:slug slug
           :name "OpenCompany"
           :description "Build trust with stakeholders"
           :currency "USD"
           :logo ""})

(def buffer {:slug "buffer"
             :public true
             :promoted true
             :name "Buffer"
             :description "Social Media LaLaLa"
             :currency "USD"
             :logo "https://open-company-assets.s3.amazonaws.com/buffer.png"})

(def uni {:name "$€¥£ &‼⁇ ∆∰≈ ☃♔☂Ǽ ḈĐĦ, LLC"
          :description "Unicode FTW"
          :public true
          :currency "FKP"}) ; Note: no logo on purpose

;; ----- Sections -----

(def text-section-1 {
  :title "Text Section 1"
  :headline "Headline Section 1"
  :image-url nil
  :image-height 0
  :image-width 0
  :body "<p>This is a body.</p>"})

(def text-section-2 {
  :title "Text Section 2"
  :headline "Headline Section 2"
  :image-url nil
  :image-height 0
  :image-width 0
  :body "<p>This is also a body.</p>"})

(def finances-section-1 {
  :title "Finances Section 1"
  :headline "Headline Finances Section 1"
  :body "<p>Best of times.</p>"
  :data [
    {
      :period "2015-06"
      :cash 42
      :revenue 1
      :costs 1
    }]})

(def finances-section-2 {
  :title "Finances Section 2"
  :headline "Headline Finances Section 2"
  :body "<p>Worst of times.</p>"
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