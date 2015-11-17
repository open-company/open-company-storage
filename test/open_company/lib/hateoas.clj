(ns open-company.lib.hateoas
  "Utility functions for testing HATEOAS https://en.wikipedia.org/wiki/HATEOAS"
  (:require [open-company.lib.check :refer (check)]
            [open-company.representations.common :refer (GET POST PUT PATCH DELETE)]
            [open-company.representations.company :as company-rep]
            [open-company.representations.section :as section-rep]))

(defn- find-link [rel links]
  (some (fn [link] (if (= rel (:rel link)) link nil)) links))

(defn verify-link [rel method href type links]
  (if-let [link (find-link rel links)]
    (do
      (check (= method (:method link)))
      (check (= href (:href link)))
      (if (= :no type)
        (check (nil? (:type link)))
        (check (= type (:type link)))))
    (check (= rel :link_not_present))))

(defn verify-company-links [slug links]
  (check (= (count links) 5))
  (let [url (company-rep/url slug)]
    (verify-link "self" GET url company-rep/media-type links)
    (verify-link "update" PUT url company-rep/media-type links)
    (verify-link "partial-update" PATCH url company-rep/media-type links)
    (verify-link "delete" DELETE url :no links)
    (verify-link "section-list" GET (company-rep/url slug :section-list) company-rep/section-list-media-type links)))

(defn verify-section-links [company-slug section-name links]
  (check (= (count links) 3))
  (let [url (section-rep/url company-slug section-name)]
    (verify-link "self" GET url section-rep/media-type links)
    (verify-link "update" PUT url section-rep/media-type links)
    (verify-link "partial-update" PATCH url section-rep/media-type links)))