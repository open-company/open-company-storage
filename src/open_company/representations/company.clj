(ns open-company.representations.company
  (:require [defun :refer (defun)]
            [cheshire.core :as json]
            [open-company.representations.common :as common]
            [open-company.representations.report :as report-rep]
            [open-company.resources.report :as report]))

(def media-type "application/vnd.open-company.company.v1+json")
(def collection-media-type "application/vnd.collection+vnd.open-company.company+json;version=1")

(defun url 
  ([ticker :guard string?] (str "/companies/" ticker))
  ([company :guard map?] (url (:symbol company))))

(defn- self-link [company]
  (common/self-link (url company) media-type))

(defn- update-link [company]
  (common/update-link (url company) media-type))

(defn- delete-link [company]
  (common/delete-link (url company)))

(defn- report-links [company]
  (let [ticker (:symbol company)]
    (map
      #(common/link-map "report"
                        common/GET
                        (report-rep/url ticker (:year %) (:period %))
                        report-rep/media-type
                        :year
                        (:year %)
                        :period
                        (:period %))
      (report/list-reports ticker))))

(defn- company-link
  "Add just a single self HATEAOS link to the company"
  [company]
  (apply array-map (concat (flatten (vec company))
    [:links (flatten [(self-link company)])])))

(defn- company-links
  "Add the HATEAOS links to the company"
  [company]
  (apply array-map (concat (flatten (vec company))
    [:links (flatten [
      (self-link company)
      (update-link company)
      (delete-link company)
      (report-links company)])])))

(defn- company-to-json-map [company]
  ;; Generate JSON from the sorted array map that results from:
  ;; 1) removing unneeded :id key
  ;; 2) render timestamps as strings
  ;; 3) adding the HATEAOS links to the array hash
  (let [company-props (dissoc company :id)]
    (-> company-props
      company-links)))

(defn render-company
  "Create a JSON representation of a company for the REST API"
  [company]
  (json/generate-string (company-to-json-map company) {:pretty true}))

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