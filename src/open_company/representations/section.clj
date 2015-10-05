(ns open-company.representations.section
  (:require [cheshire.core :as json]
            [open-company.representations.common :as common]))

(def media-type "application/vnd.open-company.section.v1+json")

(defn url 
  [company-slug section-name]
  (str "/companies/" company-slug "/" section-name))

(defn- self-link [company-slug section-name]
  (common/self-link (url company-slug section-name) media-type))

(defn- update-link [company-slug section-name]
  (common/update-link (url company-slug section-name) media-type))

(defn- partial-update-link [company-slug section-name]
  (common/partial-update-link (url company-slug section-name) media-type))

(defn- clean
  "Remove properties of the section that aren't needed in the REST API representation."
  [section]
  (-> section
    (dissoc :id)
    (dissoc :company-slug)
    (dissoc :section-name)))

(defn- section-links
  "Add the HATEAOS links to the company"
  [company-slug section-name section]
  (assoc section :links (flatten [
      (self-link company-slug section-name)
      (update-link company-slug section-name)
      (partial-update-link company-slug section-name)])))

(defn render-section
  "Create a JSON representation of the section for the REST API"
  [section]
  (json/generate-string
    (section-links (:company-slug section) (:section-name section) (clean section))
    {:pretty true}))