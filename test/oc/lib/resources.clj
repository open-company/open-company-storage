(ns oc.lib.resources
  "Namespace of data fixtures for use in tests."
  (:require [clj-time.core :as t]
            [clj-time.format :as format]
            [oc.lib.db.common :as db-common]))

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
  :user-id "1234-5678-1234"
  :name "Wile E. Coyote"
  :avatar-url "http://www.emoticonswallpapers.com/avatar/cartoons/Wiley-Coyote-Dazed.jpg"})

(def camus {
  :user-id "1960-0104-dead"
  :name "Albert Camus"
  :avatar-url "http://www.brentonholmes.com/wp-content/uploads/2010/05/albert-camus1.jpg"})

(def sartre {
  :user-id "slack:1980-0621-dead"
  :name "Jean-Paul Sartre"
  :avatar-url "http://existentialismtoday.com/wp-content/uploads/2015/11/sartre_22.jpg"})

;; ----- Orgs ----

(def open {:slug slug
           :name "OpenCompany"
           :team-id "1234-5678-1234"})

(def buffer {:name "Buffer"
             :team-id "5678-1234-5678"})

(def uni {:name "$€¥£ &‼⁇ ∆∰≈ ☃♔☂Ǽ ḈĐĦ, LLC"
          :team-id "abcd-1234-abcd"})

;; ----- Entries -----

(def entry-1 {
  :headline "Headline 1"
  :body "<p>This is a body.</p>"})

(def entry-2 {
  :headline "Headline 2"
  :body "<p>This is also a body.</p>"})