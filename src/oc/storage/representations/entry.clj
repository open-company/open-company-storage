(ns oc.storage.representations.entry
  "Resource representations for OpenCompany entries."
  (:require [defun.core :refer (defun defun-)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.board :as board-rep]))

(def representation-props [:topic-slug :title :headline :body :body-placeholder :image-url :image-height :image-width
                           :chart-url :attachments :author :created-at :updated-at])

(defun url
  
  ([org-slug board-slug topic-slug :guard string?]
  (str "/orgs/" org-slug "/boards/" board-slug "/topics/" (name topic-slug)))
  
  ([org-slug board-slug entry :guard map?] (url org-slug board-slug (name (:topic-slug entry))))

  ([org-slug board-slug topic-slug :guard string? entry-uuid]
  (str "/orgs/" org-slug "/boards/" board-slug "/topics/" (name topic-slug) "/entry/" entry-uuid))

  ([org-slug board-slug entry :guard map? entry-uuid] (url org-slug board-slug (name (:topic-slug entry)) entry-uuid)))

(defn- self-link [org-slug board-slug entry entry-uuid]
  (hateoas/self-link (url org-slug board-slug entry entry-uuid) {:accept mt/entry-media-type}))

(defn- item-link [org-slug board-slug entry entry-uuid]
  (hateoas/item-link (url org-slug board-slug entry entry-uuid) {:accept mt/entry-media-type}))

(defn- create-link [org-slug board-slug topic-slug]
  (hateoas/create-link (str (url org-slug board-slug topic-slug) "/") {:content-type mt/entry-media-type
                                                                       :accept mt/entry-media-type}))

(defn- partial-update-link [org-slug board-slug topic-slug entry-uuid]
  (hateoas/partial-update-link (url org-slug board-slug topic-slug entry-uuid) {:content-type mt/entry-media-type
                                                                                :accept mt/entry-media-type}))

(defn- delete-link [org-slug board-slug topic-slug entry-uuid]
  (hateoas/delete-link (url org-slug board-slug topic-slug entry-uuid)))

(defn- archive-link [org-slug board-slug topic-slug]
  (hateoas/archive-link (url org-slug board-slug topic-slug)))

(defn- collection-link [org-slug board-slug topic-slug entry-count]
  (hateoas/collection-link (url org-slug board-slug topic-slug) {:accept mt/entry-collection-media-type}
                                                                {:count (or entry-count 1)}))

(defn- up-link [org-slug board-slug topic-slug] (hateoas/up-link 
                                        (url org-slug board-slug topic-slug) {:accept mt/entry-collection-media-type}))

(defn- entry-collection-links
  [entry entry-count entry-uuid board-slug org-slug access-level]
  (let [topic-slug (name (:topic-slug entry))
        links [(collection-link org-slug board-slug entry entry-count)
               (item-link org-slug board-slug entry entry-uuid)]
        full-links (if (= access-level :author)
                      (concat links [(partial-update-link org-slug board-slug topic-slug entry-uuid)
                                     (delete-link org-slug board-slug topic-slug entry-uuid)
                                     (create-link org-slug board-slug topic-slug)
                                     (archive-link org-slug board-slug topic-slug)])
                      links)]
    (assoc entry :links full-links)))

(defn- entry-links
  [entry entry-uuid board-slug org-slug access-level]
  (let [topic-slug (name (:topic-slug entry))
        links [(self-link org-slug board-slug (name topic-slug) entry-uuid)
               (up-link org-slug board-slug topic-slug)]
        full-links (if (= access-level :author)
                      (concat links [(partial-update-link org-slug board-slug entry entry-uuid)
                                     (delete-link org-slug board-slug entry entry-uuid)
                                     (create-link org-slug board-slug topic-slug)
                                     (archive-link org-slug board-slug topic-slug)])
                      links)]
    (assoc entry :links full-links)))

(defn render-entry-for-collection
  "Create a map of the entry for use in a collection in the REST API"
  [org-slug board-slug entry entry-count access-level]
  (let [entry-uuid (:uuid entry)]
    (-> entry
      (select-keys representation-props)
      (entry-collection-links entry-count entry-uuid board-slug org-slug access-level))))

(defn render-entry
  "Create a JSON representation of the entry for the REST API"
  [org-slug board-slug entry access-level]
  (let [entry-uuid (:uuid entry)]
    (json/generate-string
      (-> entry
        (select-keys representation-props)
        (entry-links entry-uuid board-slug org-slug access-level))
      {:pretty config/pretty?})))

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
                    :items (map #(entry-links (select-keys % representation-props) (:uuid %) board-slug org-slug access-level) entries)}}
      {:pretty config/pretty?})))