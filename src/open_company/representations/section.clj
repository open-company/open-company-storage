(ns open-company.representations.section
  (:require [defun.core :refer (defun-)]
            [cheshire.core :as json]
            [open-company.representations.common :as common]
            [open-company.resources.section :as section]))

(def media-type "application/vnd.open-company.section.v1+json")
(def collection-media-type "application/vnd.collection+vnd.open-company.section+json;version=1")

(def ^:private clean-properties [:id :company-slug :section-name])

(defn url
  ([company-slug section-name]
  (str "/companies/" (name company-slug) "/" (name section-name)))
  ([company-slug section-name updated-at]
  (str (url company-slug section-name) "?as-of=" updated-at)))

(defn- revisions-url [company-slug section-name]
  (str (url company-slug section-name) "/revisions"))

(defn- self-link [company-slug section-name]
  (common/self-link (url company-slug section-name) media-type))

(defn- update-link [company-slug section-name]
  (common/update-link (url company-slug section-name) media-type))

(defn- partial-update-link [company-slug section-name]
  (common/partial-update-link (url company-slug section-name) media-type))

(defn- revisions-link [company-slug section-name]
  (common/link-map "revisions" common/GET (revisions-url company-slug section-name) collection-media-type))

(defn- revision-link [company-slug section-name updated-at]
  (common/revision-link (url company-slug section-name updated-at) updated-at media-type))

(defun- section-links
  "Add the HATEAOS links to the section"
  ([section authorized] (section-links (:company-slug section) (:section-name section) section authorized))

  ; read/only links
  ([company-slug section-name section false]
  (assoc section :links [(self-link company-slug section-name)
                         (revisions-link company-slug section-name)]))

  ; read/write links
  ([company-slug section-name section true]
  (assoc section :links (flatten [
    (self-link company-slug section-name)
    (revisions-link company-slug section-name)
    (update-link company-slug section-name)]))))

(defn section-for-rendering
  "Get a representation of the section for the REST API"
  [conn {:keys [company-slug section-name] :as section} authorized]
  (-> section
    (assoc :revisions (section/list-revisions conn company-slug section-name))
    (update :revisions #(map (fn [rev] (revision-link company-slug section-name (:updated-at rev))) %))
    (section-links authorized)
    (common/clean clean-properties)))

(defn render-section
  "Create a JSON representation of the section for the REST API"
  ([conn section]
    (render-section conn section true false))
  ([conn section authorized]
    (render-section conn section authorized false))
  ([conn section authorized read-only]
    (json/generate-string (section-for-rendering conn section (and authorized (not read-only))) {:pretty true})))

(defn render-revision-list
  "Create a JSON representation of a list of section revisions for the REST API"
  [company-slug section-name revisions]
  (json/generate-string
   {:collection {:version common/json-collection-version
                 :href (revisions-url company-slug section-name)
                 :links [(common/self-link (revisions-url company-slug section-name) collection-media-type)]
                 :revisions (->> revisions
                                (map #(common/clean % clean-properties)))}}
                                 ;(map #(revision-links % [:self])))}}
   {:pretty true}))