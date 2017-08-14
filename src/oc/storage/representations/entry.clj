(ns oc.storage.representations.entry
  "Resource representations for OpenCompany entries."
  (:require [defun.core :refer (defun defun-)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.board :as board-rep]
            [oc.storage.representations.content :as content]))

(def representation-props [:uuid :topic-name :topic-slug :headline :body :chart-url :attachments :author 
                           :board-slug :board-name :created-at :updated-at])

(defun url

  ([org-slug board-slug]
  (str "/orgs/" org-slug "/boards/" board-slug "/entries"))
  
  ([org-slug board-slug entry :guard map?] (url org-slug board-slug (:uuid entry)))

  ([org-slug board-slug entry-uuid]
  (str (url org-slug board-slug) "/" entry-uuid)))

(defn- self-link [org-slug board-slug entry-uuid]
  (hateoas/self-link (url org-slug board-slug entry-uuid) {:accept mt/entry-media-type}))

(defn- create-link [org-slug board-slug]
  (hateoas/create-link (str (board-rep/url org-slug board-slug) "/entries/") {:content-type mt/entry-media-type
                                                                              :accept mt/entry-media-type}))

(defn- partial-update-link [org-slug board-slug entry-uuid]
  (hateoas/partial-update-link (url org-slug board-slug entry-uuid) {:content-type mt/entry-media-type
                                                                     :accept mt/entry-media-type}))

(defn- delete-link [org-slug board-slug entry-uuid]
  (hateoas/delete-link (url org-slug board-slug entry-uuid)))

(defn- up-link [org-slug board-slug]
  (hateoas/up-link (board-rep/url org-slug board-slug) {:accept mt/board-media-type}))


(defun- clean-blank-topic
  "Remove a blank topic slug/name from an entry representation."

  ([entry :guard #(and (contains? % :topic-slug) (clojure.string/blank? (:topic-slug %)))]
    (clean-blank-topic (dissoc entry :topic-slug)))

  ([entry :guard #(and (contains? % :topic-name) (clojure.string/blank? (:topic-name %)))]
    (clean-blank-topic (dissoc entry :topic-name)))

  ([entry] entry))

(defn- entry-and-links
  "
  Given an entry and all the metadata about it, render an access level appropriate rendition of the entry
  for use in an API response.
  "
  [entry entry-uuid board-slug org-slug comments reactions access-level user-id]
  (let [org-uuid (:org-uuid entry)
        board-uuid (:board-uuid entry)
        reactions (if (= access-level :public)
                    []
                    (content/reactions-and-links org-uuid board-uuid entry-uuid reactions user-id))
        links [(self-link org-slug board-slug entry-uuid)
               (up-link org-slug board-slug)]
        full-links (cond 
                    (= access-level :author)
                    (concat links [(partial-update-link org-slug board-slug entry-uuid)
                                   (delete-link org-slug board-slug entry-uuid)
                                   (content/comment-link org-uuid board-uuid entry-uuid)
                                   (content/comments-link org-uuid board-uuid entry-uuid comments)])

                    (= access-level :viewer)
                    (concat links [(content/comment-link org-uuid board-uuid entry-uuid)
                                   (content/comments-link org-uuid board-uuid entry-uuid comments)])

                    :else links)]
    (-> (select-keys entry representation-props)
      (clean-blank-topic)
      (assoc :reactions reactions)
      (assoc :links full-links))))

(defn render-entry-for-collection
  "Create a map of the entry for use in a collection in the API"
  [org-slug board-slug entry comments reactions access-level user-id]
  (let [entry-uuid (:uuid entry)]
    (entry-and-links entry entry-uuid board-slug org-slug comments reactions access-level user-id)))

(defn render-entry
  "Create a JSON representation of the entry for the API"
  [org-slug board-slug entry comments reactions access-level user-id]
  (let [entry-uuid (:uuid entry)]
    (json/generate-string
      (render-entry-for-collection org-slug board-slug entry comments reactions access-level user-id)      
      {:pretty config/pretty?})))

(defn render-entry-list
  "
  Given a org and board slug and a sequence of entry maps, create a JSON representation of a list of
  entries for the API.
  "
  [org-slug board-slug entries access-level user-id]
  (let [collection-url (url org-slug board-slug)
        links [(hateoas/self-link collection-url {:accept mt/entry-collection-media-type})
               (hateoas/up-link (board-rep/url org-slug board-slug) {:accept mt/board-media-type})]
        full-links (if (= access-level :author)
                      (concat links [(create-link org-slug board-slug)])
                      links)]
    (json/generate-string
      {:collection {:version hateoas/json-collection-version
                    :href collection-url
                    :links full-links
                    :items (map #(entry-and-links % (:uuid %) board-slug org-slug
                                    (or (filter :body (:interactions %)) [])  ; comments only
                                    (or (filter :reaction (:interactions %)) []) ; reactions only
                                    access-level user-id)
                             entries)}}
      {:pretty config/pretty?})))