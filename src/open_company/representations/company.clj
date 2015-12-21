(ns open-company.representations.company
  (:require [defun :refer (defun defun-)]
            [cheshire.core :as json]
            [open-company.representations.common :as common]
            [open-company.representations.section :as section-rep]
            [open-company.resources.company :as company]))

(def media-type "application/vnd.open-company.company.v1+json")
(def collection-media-type "application/vnd.collection+vnd.open-company.company+json;version=1")
(def section-list-media-type "application/vnd.open-company.section-list.v1+json")

(defun url
  ([slug :guard string?] (str "/companies/" (name slug)))
  ([company :guard map?] (url (name (:slug company))))
  ([company :section-list] (str (url company) "/section/new"))
  ([company updated-at] (str (url company) "?as-of=" updated-at)))

(defn- self-link [company]
  (common/self-link (url company) media-type))

(defn- update-link [company]
  (common/update-link (url company) media-type))

(defn- partial-update-link [company]
  (common/partial-update-link (url company) media-type))

(defn- delete-link [company]
  (common/delete-link (url company)))

(defn- revision-link [company updated-at]
  (common/revision-link (url company updated-at) updated-at media-type))

(defn- section-list-link [company]
  (common/link-map "section-list" common/GET (url company :section-list) section-list-media-type))

(defn- company-link
  "Add just a single self HATEAOS link to the company"
  [company]
  (apply array-map (concat (flatten (vec company))
    [:links (flatten [(self-link company)])])))

(defun- company-links
  "Add the HATEAOS links to the company"

  ; read/only links
  ([company false] (assoc company :links [(self-link company)]))

  ; read/write links
  ([company true]
  (assoc company :links (flatten [
      (self-link company)
      (update-link company)
      (partial-update-link company)
      (delete-link company)
      (section-list-link company)]))))

(defn- clean
  "Remove any properties that shouldn't be returned in the JSON representation"
  [company]
  (dissoc company :org-id))

(defun- sections
  "Get a representation of each section for the REST API"
  ([company authorized] (sections company (flatten (vals (:sections company))) authorized))
  ([company _sections :guard empty? _authorized] company)
  ([company sections authorized]
    (let [company-slug (:slug company)
          section-name (keyword (first sections))
          section (-> (company section-name)
                    (assoc :company-slug company-slug)
                    (assoc :section-name section-name))]
      (recur
        (assoc company section-name (section-rep/section-for-rendering section authorized))
        (rest sections)
        authorized))))

(defn- revision-links
  "Add the HATEAOS revision links to the company"
  [company]
  (let [company-slug (:slug company)
        revisions (company/list-revisions company-slug)]
    (assoc company :revisions (flatten
      (map #(revision-link company-slug (:updated-at %)) revisions)))))

(defn- company-for-rendering
  "Get a representation of the company for the REST API"
  [company authorized]
  (-> company
    (clean)
    (revision-links)
    (company-links authorized)
    (sections authorized)))

(defn render-company
  "Create a JSON representation of a company for the REST API"
  ([company] (render-company company true))
  ([company authorized]
  (json/generate-string (company-for-rendering company authorized) {:pretty true})))

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