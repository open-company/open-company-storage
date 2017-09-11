(ns oc.storage.representations.story
  "Resource representations for OpenCompany stories."
  (:require [defun.core :refer (defun)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.representations.board :as board-rep]
            [oc.storage.representations.content :as content]))

(def org-prop-mapping {:name :org-name
                       :logo-url :org-logo-url
                       :logo-width :org-logo-width
                       :logo-height :org-logo-height})

(def representation-props [:uuid :title :banner-url :banner-width :banner-height :body 
                           :org-name :org-logo-url :org-logo-width :org-logo-height
                           :storyboard-name :storyboard-slug :status
                           :author :published-at :created-at :updated-at])

(defun url

  ([org-slug board-slug]
  (str (board-rep/url org-slug board-slug) "/stories"))

  ([org-slug board-slug story :guard map?] (url org-slug board-slug (:uuid story)))

  ([org-slug board-slug story-uuid :guard string?] (str (url org-slug board-slug) "/" story-uuid)))

(defn- secure-url [org-slug secure-uuid] (str (org-rep/url org-slug) "/stories/" secure-uuid))

(defn- self-link [org-slug board-slug story-uuid]
  (hateoas/self-link (url org-slug board-slug story-uuid) {:accept mt/story-media-type}))

(defn- secure-self-link [org-slug story-uuid]
  (hateoas/self-link (secure-url org-slug story-uuid) {:accept mt/story-media-type}))

(defn- create-link [org-slug board-slug]
  (hateoas/create-link (str (url org-slug board-slug) "/") {:content-type mt/story-media-type
                                                            :accept mt/story-media-type}))

(defn- partial-update-link [org-slug board-slug story-uuid]
  (hateoas/partial-update-link (url org-slug board-slug story-uuid) {:content-type mt/story-media-type
                                                                     :accept mt/story-media-type}))

(defn- delete-link [org-slug board-slug story-uuid]
  (hateoas/delete-link (url org-slug board-slug story-uuid)))

(defn- publish-link [org-slug board-slug story-uuid]
  (hateoas/link-map "publish" hateoas/POST (str (url org-slug board-slug story-uuid) "/publish")
    {:content-type mt/share-request-media-type
     :accept mt/story-media-type}))

(defn- share-link [org-slug board-slug story-uuid]
  (hateoas/link-map "share" hateoas/POST (str (url org-slug board-slug story-uuid) "/share")
    {:content-type mt/share-request-media-type
     :accept mt/story-media-type}))

(defn- secure-link [org-slug secure-uuid]
  (hateoas/link-map "secure" hateoas/GET (secure-url org-slug secure-uuid) {:accept mt/story-media-type}))

(defn- up-link [org-slug board-slug]
  (hateoas/up-link (board-rep/url org-slug board-slug) {:accept mt/board-media-type}))

(defn include-secure-uuid
  "Include secure UUID property for authors."
  [story secure-uuid access-level]
  (if (= access-level :author)
    (assoc story :secure-uuid secure-uuid)
    story))

(defn- story-and-links
  "
  Given an story and all the metadata about it, render an access level appropriate rendition of the story
  for use in an API response.
  "
  [org board story comments reactions access-level user-id]
  (let [secure-access? (= user-id :secure)
        story-uuid (:uuid story)
        secure-uuid (:secure-uuid story)
        org-slug (:slug org)
        org-uuid (:uuid org)
        board-slug (:slug board)
        board-uuid (:uuid board)
        draft? (= :draft (keyword (:status story)))
        reaction-rep (if (or draft? (not (or (= access-level :author) (= access-level :viewer))))
                    []
                    (content/reactions-and-links org-uuid board-uuid story-uuid reactions user-id))
        links (if secure-access?
                ;; secure UUID access
                [(secure-self-link org-slug secure-uuid)]
                ;; normal access
                [(self-link org-slug board-slug story-uuid)
                 (up-link org-slug board-slug)])
        more-links (cond
                    ;; Accessing drafts, or access by authors get editing links
                    (or (and draft? (not secure-access?)) (= access-level :author))
                    (concat links [(partial-update-link org-slug board-slug story-uuid)
                                   (delete-link org-slug board-slug story-uuid)
                                   (secure-link org-slug secure-uuid)
                                   (content/comment-link org-uuid board-uuid story-uuid)
                                   (content/comments-link org-uuid board-uuid story-uuid comments)
                                   (board-rep/interaction-link board-uuid)])
                    ;; Access by viewers get comments
                    (= access-level :viewer)
                    (concat links [(content/comment-link org-uuid board-uuid story-uuid)
                                   (content/comments-link org-uuid board-uuid story-uuid comments)
                                   (board-rep/interaction-link board-uuid)])
                    ;; Everyone else is read-only
                    :else links)
        full-links (cond
                      ;; Drafts need a publish link
                      draft?
                      (conj more-links (publish-link org-slug board-slug story-uuid))
                      ;; Indirect access via the storyboard, rather than direct access by the secure ID
                      ;; needs a share link
                      (not secure-access?)
                      (conj more-links (share-link org-slug board-slug story-uuid))
                      ;; Otherwise just the links they already have
                      :else more-links)]
    (-> (merge org story)
      (clojure.set/rename-keys org-prop-mapping)
      (select-keys  representation-props)
      (include-secure-uuid (:secure-uuid story) access-level)
      (assoc :storyboard-name (:name board))
      (assoc :storyboard-slug (:slug board))
      (assoc :reactions reaction-rep)
      (assoc :links full-links))))

(defn render-story-for-collection
  "Create a map of the story for use in a collection in the API"
  [org board story comments reactions access-level user-id]
  (story-and-links org board story comments reactions access-level user-id))

(defn render-story
 "Create a JSON representation of the story for the REST API"
  ([org board story comments reactions related access-level user-id]
  (json/generate-string
    (assoc (render-story-for-collection org board story comments reactions access-level user-id)
      :related related)
    {:pretty config/pretty?}))

  ([org board story comments reactions access-level user-id]
  (json/generate-string
    (render-story-for-collection org board story comments reactions access-level user-id)      
    {:pretty config/pretty?})))

(defn render-story-list
  "
  Given an org and a board, a sequence of story maps, and access control levels,
  create a JSON representation of a list of stories for the REST API.
  "
  [org board stories access-level user-id]
  (let [org-slug (:slug org)
        board-slug (:slug board)
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
                    :items (map #(story-and-links org board %
                                    (or (filter :body (:interactions %)) [])  ; comments only
                                    (or (filter :reaction (:interactions %)) []) ; reactions only
                                    access-level user-id)
                              stories)}}
      {:pretty config/pretty?})))