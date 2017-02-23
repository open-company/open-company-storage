(ns oc.storage.representations.entry
  "Resource representations for OpenCompany entries."
  (:require [defun.core :refer (defun defun-)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.board :as board-rep]))

(def representation-props [:topic-slug :title :headline :body :body-placeholder :image-url :image-height :image-width
                           :author :created-at :updated-at])

(def data-props [:prompt :data :metrics :intervals :units])

(defun url
  
  ([org-slug board-slug topic-slug :guard string?]
  (str "/orgs/" org-slug "/boards/" board-slug "/topics/" (name topic-slug)))
  
  ([org-slug board-slug entry :guard map?] (url org-slug board-slug (name (:topic-slug entry))))

  ([org-slug board-slug topic-slug :guard string? timestamp]
  (str "/orgs/" org-slug "/boards/" board-slug "/topics/" (name topic-slug) "?as-of=" timestamp))

  ([org-slug board-slug entry :guard map? timestamp] (url org-slug board-slug (name (:topic-slug entry)) timestamp)))

(defn- self-link [org-slug board-slug entry timestamp]
  (hateoas/self-link (url org-slug board-slug entry timestamp) {:accept mt/entry-media-type}))

(defn- item-link [org-slug board-slug entry timestamp]
  (hateoas/item-link (url org-slug board-slug entry timestamp) {:accept mt/entry-media-type}))

(defn- create-link [org-slug board-slug topic-slug]
  (hateoas/create-link (str (url org-slug board-slug topic-slug) "/") {:content-type mt/entry-media-type
                                                                       :accept mt/entry-media-type}))

(defn- partial-update-link [org-slug board-slug topic-slug timestamp]
  (hateoas/partial-update-link (url org-slug board-slug topic-slug timestamp) {:content-type mt/entry-media-type
                                                                               :accept mt/entry-media-type}))

(defn- delete-link [org-slug board-slug topic-slug timestamp]
  (hateoas/delete-link (url org-slug board-slug topic-slug timestamp)))

(defn- archive-link [org-slug board-slug topic-slug]
  (hateoas/archive-link (url org-slug board-slug topic-slug)))

(defn- collection-link [org-slug board-slug topic-slug]
  (hateoas/collection-link (url org-slug board-slug topic-slug) {:accept mt/entry-collection-media-type}))

(defn- up-link [org-slug board-slug topic-slug] (hateoas/up-link 
                                        (url org-slug board-slug topic-slug) {:accept mt/entry-collection-media-type}))

(defn- entry-collection-links
  [entry board-slug org-slug access-level]
  (let [timestamp (:created-at entry)
        topic-slug (name (:topic-slug entry))
        links [(collection-link org-slug board-slug entry)
               (item-link org-slug board-slug entry timestamp)]
        full-links (if (= access-level :author)
                      (concat links [(partial-update-link org-slug board-slug topic-slug timestamp)
                                     (delete-link org-slug board-slug topic-slug timestamp)
                                     (create-link org-slug board-slug topic-slug)
                                     (archive-link org-slug board-slug topic-slug)])
                      links)]
    (assoc entry :links full-links)))

(defn- entry-links
  [entry board-slug org-slug access-level]
  (let [topic-slug (name (:topic-slug entry))
        timestamp (:created-at entry)
        links [(self-link org-slug board-slug (name topic-slug) timestamp)
               (up-link org-slug board-slug topic-slug)]
        full-links (if (= access-level :author)
                      (concat links [(partial-update-link org-slug board-slug entry timestamp)
                                     (delete-link org-slug board-slug entry timestamp)
                                     (create-link org-slug board-slug topic-slug)])
                      links)]
    (assoc entry :links full-links)))

(defun- select-data-props
  ([entry data []] entry) ; all done

  ([entry data data-props]
  (let [data-prop (first data-props)
        data-value (data-prop data)
        updated-entry (if data-value (assoc entry data-prop data-value) entry)]
    (select-data-props updated-entry data (vec (rest data-props)))))) ; recurse

(defn render-entry-for-collection
  "Create a map of the entry for use in a collection in the REST API"
  [org-slug board-slug entry access-level]
  (-> entry
    (select-keys representation-props)
    (select-data-props entry data-props)
    (entry-collection-links board-slug org-slug access-level)))

(defn render-entry
  "Create a JSON representation of the board for the REST API"
  [org-slug board-slug entry access-level]
  (json/generate-string
    (-> entry
      (select-keys representation-props)
      (select-data-props entry data-props)
      (entry-links board-slug org-slug access-level))
    {:pretty true}))

(defn render-entry-list
  "
  Given a org and board slug and a sequence of entry maps, create a JSON representation of a list of
  entries for the REST API.
  "
  [org-slug board-slug topic-slug entries access-level]
  (let [collection-url (url org-slug board-slug topic-slug)
        links [(hateoas/self-link collection-url {:accept mt/entry-collection-media-type})
               (hateoas/up-link (board-rep/url org-slug board-slug) {:accept mt/board-media-type})]
        full-links (if (= access-level :author)
                      (concat links [(create-link org-slug board-slug topic-slug)
                                     (archive-link org-slug board-slug topic-slug)])
                      links)]
    (json/generate-string
      {:collection {:version hateoas/json-collection-version
                    :href collection-url
                    :links full-links
                    :items (map #(entry-links % board-slug org-slug access-level) entries)}}
      {:pretty true})))