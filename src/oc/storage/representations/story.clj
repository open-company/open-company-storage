(ns oc.storage.representations.story
  "Resource representations for OpenCompany stories."
  (:require [defun.core :refer (defun)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.board :as board-rep]
            [oc.storage.representations.content :as content]))

(def org-prop-mapping {:name :org-name
                       :logo-url :org-logo-url
                       :logo-width :org-logo-width
                       :logo-height :org-logo-height})

(def representation-props [:uuid :title :banner-url :banner-width :banner-height :body 
                           :org-name :org-logo-url :org-logo-width :org-logo-height
                           :author :created-at])

(defun url

  ([org-slug board-slug]
  (str (board-rep/url org-slug board-slug) "/stories"))

  ([org-slug board-slug story :guard map?] (url org-slug board-slug (:uuid story)))

  ([org-slug board-slug story-uuid :guard string?] (str (url org-slug board-slug) "/" story-uuid)))

(defn- self-link [org-slug board-slug story-uuid]
  (hateoas/self-link (url org-slug board-slug story-uuid) {:accept mt/story-media-type}))

(defn- create-link [org-slug board-slug]
  (hateoas/create-link (str (url org-slug board-slug) "/") {:content-type mt/story-media-type
                                                            :accept mt/story-media-type}))

(defn- partial-update-link [org-slug board-slug story-uuid]
  (hateoas/partial-update-link (url org-slug board-slug story-uuid) {:content-type mt/story-media-type
                                                                     :accept mt/story-media-type}))

(defn- delete-link [org-slug board-slug story-uuid]
  (hateoas/delete-link (url org-slug board-slug story-uuid)))

(defn- up-link [org-slug board-slug]
  (hateoas/up-link (board-rep/url org-slug board-slug) {:accept mt/board-media-type}))

(defn- story-and-links
  "
  Given an story and all the metadata about it, render an access level appropriate rendition of the story
  for use in an API response.
  "
  [org board-slug story comments reactions access-level user-id]
  (let [story-uuid (:uuid story)
        org-slug (:slug org)
        org-uuid (:uuid org)
        board-uuid (:board-uuid story)
        reactions (if (= access-level :public)
                    []
                    (content/reactions-and-links org-uuid board-uuid story-uuid reactions user-id))
        links [(self-link org-slug board-slug story-uuid)
               (up-link org-slug board-slug)]
        full-links (cond 
                    (= access-level :author)
                    (concat links [(partial-update-link org-slug board-slug story-uuid)
                                   (delete-link org-slug board-slug story-uuid)
                                   (content/comment-link org-uuid board-uuid story-uuid)
                                   (content/comments-link org-uuid board-uuid story-uuid comments)])

                    (= access-level :viewer)
                    (concat links [(content/comment-link org-uuid board-uuid story-uuid)
                                   (content/comments-link org-uuid board-uuid story-uuid comments)])

                    :else links)]
    (-> (merge org story)
      (clojure.set/rename-keys org-prop-mapping)
      (select-keys  representation-props)
      (assoc :reactions reactions)
      (assoc :links full-links))))

(defn render-story-for-collection
  "Create a map of the story for use in a collection in the API"
  [org board-slug story comments reactions access-level user-id]
  (story-and-links org board-slug story comments reactions access-level user-id))

(defn render-story
 "Create a JSON representation of the story for the REST API"
  [org board-slug story comments reactions access-level user-id]
  (let [story-uuid (:uuid story)]
    (json/generate-string
      (render-story-for-collection story board-slug org comments reactions access-level user-id)      
      {:pretty config/pretty?})))

(defn render-story-list
  "
  Given an org and board slug, a sequence of story maps, and access control levels,
  create a JSON representation of a list of stories for the REST API.
  "
  [org board-slug stories access-level user-id]
  (let [org-slug (:slug org)
        collection-url (url org-slug board-slug)
        links [(hateoas/self-link collection-url {:accept mt/story-collection-media-type})
               (up-link org-slug board-slug)]
        full-links (if (= access-level :author)
                      (conj links (create-link org-slug board-slug))
                      links)]
    (json/generate-string
      {:collection {:version hateoas/json-collection-version
                    :href collection-url
                    :links full-links
                    :items (map #(story-and-links org board-slug %
                                    (or (filter :body (:interactions %)) [])  ; comments only
                                    (or (filter :reaction (:interactions %)) []) ; reactions only
                                    access-level user-id)
                              stories)}}
      {:pretty config/pretty?})))