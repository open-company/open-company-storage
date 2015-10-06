(ns open-company.representations.company
  (:require [defun :refer (defun)]
            [cheshire.core :as json]
            [open-company.representations.common :as common]))

(def media-type "application/vnd.open-company.company.v1+json")
(def collection-media-type "application/vnd.collection+vnd.open-company.company+json;version=1")

(defun url 
  ([slug :guard string?] (str "/companies/" (name slug)))
  ([company :guard map?] (url (:slug company))))

(defn- self-link [company]
  (common/self-link (url company) media-type))

(defn- update-link [company]
  (common/update-link (url company) media-type))

(defn- partial-update-link [company]
  (common/partial-update-link (url company) media-type))

(defn- delete-link [company]
  (common/delete-link (url company)))

(defn- company-link
  "Add just a single self HATEAOS link to the company"
  [company]
  (apply array-map (concat (flatten (vec company))
    [:links (flatten [(self-link company)])])))

(defn- company-links
  "Add the HATEAOS links to the company"
  [company]
  (assoc company :links (flatten [
      (self-link company)
      (update-link company)
      (partial-update-link company)
      (delete-link company)])))

(defn render-company
  "Create a JSON representation of a company for the REST API"
  [company]
  (json/generate-string (company-links company) {:pretty true}))

(defn render-company-list
  "Create a JSON representation of a group of companies for the REST API"
  [companies]
  (json/generate-string {
    :collection (array-map
      :version common/json-collection-version
      :href "/companies"
      :links [(common/self-link (str "/companies") collection-media-type)]
      :companies (map company-link companies))}
    {:pretty true}))