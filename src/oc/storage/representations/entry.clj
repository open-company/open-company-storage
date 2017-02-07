(ns oc.storage.representations.entry
  "Resource representations for OpenCompany entries."
  (:require [defun.core :refer (defun)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.representations.media-types :as mt]))

(def representation-props [:topic-slug :title :headline :body :image-url :image-height :image-width
                           :author :created-at :updated-at])

(defun url
  
  ([org-slug board-slug slug :guard string?] (str "/orgs/" org-slug "/boards/" board-slug "/topics/" (name slug)))
  
  ([org-slug board-slug entry :guard map?] (url org-slug board-slug (:topic-slug entry)))

  ([org-slug board-slug slug :guard string? timestamp]
    (str "/orgs/" org-slug "/boards/" board-slug "/topics/" (name slug) "?as-of=" timestamp))

  ([org-slug board-slug entry :guard map? timestamp] (url org-slug board-slug (:topic-slug entry) timestamp)))

(defn- item-link [org-slug board-slug entry timestamp]
  (hateoas/item-link (url org-slug board-slug entry timestamp) {:accept mt/entry-media-type}))

(defn- collection-link [org-slug board-slug slug]
  (hateoas/collection-link (url org-slug board-slug slug) {:accept mt/entry-collection-media-type}))

(defn- entry-collection-links
  [entry board-slug org-slug]
  (assoc entry :links [
    (collection-link org-slug board-slug entry)
    (item-link org-slug board-slug entry (:created-at entry))]))

(defn render-entry-for-collection
  "Create a map of the entry for use in a collection in the REST API"
  [org-slug board-slug entry]
  (println "HERE")
  (println entry)
  (-> entry
    (select-keys representation-props)
    ;; TODO data props
    (entry-collection-links board-slug org-slug)))