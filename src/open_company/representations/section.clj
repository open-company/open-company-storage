(ns open-company.representations.section
  (:require [defun :refer (defun-)]
            [cheshire.core :as json]
            [open-company.representations.common :as common]
            [open-company.resources.section :as section]))

(def media-type "application/vnd.open-company.section.v1+json")

(defn url
  ([company-slug section-name]
  (str "/companies/" (name company-slug) "/" (name section-name)))
  ([company-slug section-name updated-at]
  (str (url company-slug section-name) "?as-of=" updated-at)))

(defn- self-link [company-slug section-name]
  (common/self-link (url company-slug section-name) media-type))

(defn- update-link [company-slug section-name]
  (common/update-link (url company-slug section-name) media-type))

(defn- partial-update-link [company-slug section-name]
  (common/partial-update-link (url company-slug section-name) media-type))

(defn- revision-link [company-slug section-name updated-at]
  (common/revision-link (url company-slug section-name updated-at) updated-at media-type))

(defun- section-links
  "Add the HATEAOS links to the section"
  ([section authorized] (section-links (:company-slug section) (:section-name section) section authorized))

  ; read/only links
  ([company-slug section-name section false]
  (assoc section :links [(self-link company-slug section-name)]))

  ; read/write links
  ([company-slug section-name section true]
  (assoc section :links (flatten [
    (self-link company-slug section-name)
    (update-link company-slug section-name)
    (partial-update-link company-slug section-name)]))))

(defn revision-links
  "Add the HATEAOS revision links to the section"
  ([section] (revision-links (:company-slug section) (:section-name section) section))

  ([company-slug section-name section]
  (assoc section :revisions (flatten
    (map #(revision-link company-slug section-name (:updated-at %))
      (section/list-revisions company-slug (name section-name)))))))

(defn- clean
  "Remove properties of the section that aren't needed in the REST API representation"
  [section]
  (-> section
    (dissoc :id)
    (dissoc :company-slug)
    (dissoc :section-name)))

(defn section-for-rendering
  "Get a representation of the section for the REST API"
  [section authorized]
  (-> section
    (revision-links)
    (section-links authorized)
    (clean)))

(defn render-section
  "Create a JSON representation of the section for the REST API"
  [section]
  (json/generate-string (section-for-rendering section true) {:pretty true}))