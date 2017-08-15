(ns oc.storage.representations.story
  "Resource representations for OpenCompany stories."
  (:require [defun.core :refer (defun)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.board :as board-rep]
            [oc.storage.representations.content :as content]))

(def representation-props [:slug 
                           :org-name :org-logo-url :org-logo-width :org-logo-height
                           :title :banner-url :banner-width :banner-height :body 
                           :author :created-at])

(defun url
  ([org-slug board-slug slug :guard string?] (str "/orgs/" org-slug "/boards/" board-slug "/stories/" slug))
  ([org-slug board-slug story :guard map?] (url org-slug board-slug (:slug story))))

(defn- self-link [org-slug board-slug slug] (hateoas/self-link (url org-slug board-slug slug) {:accept mt/story-media-type}))

(defn- up-link [org-slug board-slug]
  (hateoas/up-link (board-rep/url org-slug board-slug) {:accept mt/board-media-type}))

; (defn- create-link [org-slug] (hateoas/create-link (str (org-rep/url org-slug) "/stories/")
;                                 {:content-type mt/share-request-media-type
;                                  :accept mt/story-media-type}))

(defn- partial-update-link [org-slug board-slug story-uuid]
  (hateoas/partial-update-link (url org-slug board-slug story-uuid) {:content-type mt/story-media-type
                                                                     :accept mt/story-media-type}))

(defn- delete-link [org-slug board-slug story-uuid]
  (hateoas/delete-link (url org-slug board-slug story-uuid)))

; (defn- story-links
;   [story org-slug]
;   (let [slug (:slug story)]
;     (assoc story :links [(self-link org-slug slug) (up-link org-slug)])))

; (defn render-story
;  "Create a JSON representation of the story for the REST API"
;    [org-slug story]
;   (json/generate-string
;     (-> story
;       (select-keys representation-props)
;       (story-links org-slug))
;     {:pretty config/pretty?}))

(defn- story-and-links
  "
  Given an story and all the metadata about it, render an access level appropriate rendition of the story
  for use in an API response.
  "
  [story story-uuid board-slug org-slug comments reactions access-level user-id]
  (let [org-uuid (:org-uuid story)
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
    (-> (select-keys story representation-props)
      (assoc :reactions reactions)
      (assoc :links full-links))))

(defn render-story-for-collection
  "Create a map of the story for use in a collection in the API"
  [org-slug board-slug story comments reactions access-level user-id]
  (let [story-uuid (:uuid story)]
    (story-and-links story story-uuid board-slug org-slug comments reactions access-level user-id)))

; (defn render-story-list
;   "
;   Given a org slug and a sequence of story maps, create a JSON representation of a list of
;   stories for the REST API.
;   "
;   [org-slug stories]
;   (let [collection-url (str (org-rep/url org-slug) "/stories")]
;     (json/generate-string
;       {:collection {:version hateoas/json-collection-version
;                     :href collection-url
;                     :links [(hateoas/self-link collection-url {:accept mt/story-collection-media-type})
;                             (create-link story-slug)]
;                     :items (map #(story-links % org-slug)
;                               (map #(select-keys % list-props) stories))}}
;       {:pretty config/pretty?})))