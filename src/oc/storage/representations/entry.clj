(ns oc.storage.representations.entry
  "Resource representations for OpenCompany entries."
  (:require [defun.core :refer (defun defun-)]
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

(def representation-props [:uuid :topic-name :topic-slug :headline :body :status
                           :org-name :org-slug :org-logo-url :org-logo-width :org-logo-height
                           :board-uuid :board-slug :board-name
                           :team-id :author :publisher :published-at :created-at :updated-at])

(defun url

  ([org-slug board-slug]
  (str (board-rep/url org-slug board-slug) "/entries"))
  
  ([org-slug board-slug entry :guard map?] (url org-slug board-slug (:uuid entry)))

  ([org-slug board-slug entry-uuid]
  (str (url org-slug board-slug) "/" entry-uuid)))

(defn- self-link [org-slug board-slug entry-uuid]
  (hateoas/self-link (url org-slug board-slug entry-uuid) {:accept mt/entry-media-type}))

(defn- secure-url [org-slug secure-uuid] (str (org-rep/url org-slug) "/entries/" secure-uuid))

(defn- secure-self-link [org-slug entry-uuid]
  (hateoas/self-link (secure-url org-slug entry-uuid) {:accept mt/entry-media-type}))

(defn- secure-link [org-slug secure-uuid]
  (hateoas/link-map "secure" hateoas/GET (secure-url org-slug secure-uuid) {:accept mt/entry-media-type}))

(defn- create-link [org-slug board-slug]
  (hateoas/create-link (str (url org-slug board-slug) "/") {:content-type mt/entry-media-type
                                                            :accept mt/entry-media-type}))

(defn- partial-update-link [org-slug board-slug entry-uuid]
  (hateoas/partial-update-link (url org-slug board-slug entry-uuid) {:content-type mt/entry-media-type
                                                                     :accept mt/entry-media-type}))

(defn- delete-link [org-slug board-slug entry-uuid]
  (hateoas/delete-link (url org-slug board-slug entry-uuid)))

(defn- publish-link [org-slug board-slug entry-uuid]
  (hateoas/link-map "publish" hateoas/POST (str (url org-slug board-slug entry-uuid) "/publish")
    {:content-type mt/share-request-media-type
     :accept mt/entry-media-type}))

(defn- share-link [org-slug board-slug entry-uuid]
  (hateoas/link-map "share" hateoas/POST (str (url org-slug board-slug entry-uuid) "/share")
    {:content-type mt/share-request-media-type
     :accept mt/entry-media-type}))

(defn- up-link [org-slug board-slug]
  (hateoas/up-link (board-rep/url org-slug board-slug) {:accept mt/board-media-type}))


(defun- clean-blank-topic
  "Remove a blank topic slug/name from an entry representation."

  ([entry :guard #(and (contains? % :topic-slug) (clojure.string/blank? (:topic-slug %)))]
    (clean-blank-topic (dissoc entry :topic-slug)))

  ([entry :guard #(and (contains? % :topic-name) (clojure.string/blank? (:topic-name %)))]
    (clean-blank-topic (dissoc entry :topic-name)))

  ([entry] entry))

(defn- include-secure-uuid
  "Include secure UUID property for authors."
  [entry secure-uuid access-level]
  (if (= access-level :author)
    (assoc entry :secure-uuid secure-uuid)
    entry))

(defun- entry-and-links
  "
  Given an entry and all the metadata about it, render an access level appropriate rendition of the entry
  for use in an API response.
  "
  ([entry board-slug org :guard map? comments reactions access-level user-id secure-access?]
  (let [entry-uuid (:uuid entry)
        secure-uuid (:secure-uuid entry)
        org-uuid (:org-uuid entry)
        org-slug (:slug org)
        board-uuid (:board-uuid entry)
        draft? (= :draft (keyword (:status entry)))
        reactions (if (= access-level :public)
                    []
                    (content/reactions-and-links org-uuid board-uuid entry-uuid reactions user-id))
        links (if secure-access?
                ;; secure UUID access
                [(secure-self-link org-slug secure-uuid)]
                ;; normal access
                [(self-link org-slug board-slug entry-uuid)
                 (up-link org-slug board-slug)])
        more-links (cond 
                    ;; Accessing their drafts, or access by an author, both get editing links                    
                    (or (and draft? (not secure-access?)) (= access-level :author))
                    (concat links [(partial-update-link org-slug board-slug entry-uuid)
                                   (delete-link org-slug board-slug entry-uuid)
                                   (secure-link org-slug secure-uuid)                                   
                                   (content/comment-link org-uuid board-uuid entry-uuid)
                                   (content/comments-link org-uuid board-uuid entry-uuid comments)
                                   (board-rep/interaction-link board-uuid)])
                    ;; Access by viewers get comments
                    (= access-level :viewer)
                    (concat links [(content/comment-link org-uuid board-uuid entry-uuid)
                                   (content/comments-link org-uuid board-uuid entry-uuid comments)
                                   (board-rep/interaction-link board-uuid)])
                    ;; Everyone else is read-only
                    :else links)
        full-links (cond
              ;; Drafts need a publish link
              draft?
              (conj more-links (publish-link org-slug board-slug entry-uuid))
              ;; Indirect access via the board, rather than direct access by the secure ID
              ;; needs a share link
              (and (not secure-access?) (or (= access-level :author) (= access-level :viewer)))
              (conj more-links (share-link org-slug board-slug entry-uuid))
              ;; Otherwise just the links they already have
              :else more-links)]

    (-> (if secure-access?
          ;; "stand-alone", so include extra props
          (-> org
            (clojure.set/rename-keys  {:slug :org-slug})
            (merge entry)
            (assoc :board-slug board-slug))
          ;; don't need the extra props
          (dissoc entry :board-uuid))
      (clojure.set/rename-keys org-prop-mapping)
      (select-keys representation-props)
      (clean-blank-topic)
      (include-secure-uuid secure-uuid access-level)
      (assoc :reactions reactions)
      (assoc :links full-links))))

  ([entry board-slug org-slug comments reactions access-level user-id secure-access?]
  (entry-and-links entry board-slug {:slug org-slug} comments reactions access-level user-id secure-access?)))


(defn render-entry-for-collection
  "Create a map of the entry for use in a collection in the API"
  ([org board-slug entry comments reactions access-level user-id]
  (render-entry-for-collection org board-slug entry comments reactions access-level user-id false))

  ([org board-slug entry comments reactions access-level user-id secure-access?]
  (entry-and-links entry board-slug org comments reactions access-level user-id secure-access?)))

(defn render-entry
  "Create a JSON representation of the entry for the API"
  ([org board-slug entry comments reactions access-level user-id]
  (render-entry org board-slug entry comments reactions access-level user-id false))

  ([org board-slug entry comments reactions access-level user-id secure-access?]
  (let [entry-uuid (:uuid entry)]
    (json/generate-string
      (render-entry-for-collection org board-slug entry comments reactions access-level user-id secure-access?)
      {:pretty config/pretty?}))))

(defn render-entry-list
  "
  Given an org and board slug, a sequence of entry maps, and access control levels, 
  create a JSON representation of a list of entries for the API.
  "
  [org-slug board-slug entries access-level user-id]
  (let [collection-url (url org-slug board-slug)
        links [(hateoas/self-link collection-url {:accept mt/entry-collection-media-type})
               (up-link org-slug board-slug)]
        full-links (if (= access-level :author)
                      (conj links (create-link org-slug board-slug))
                      links)]
    (json/generate-string
      {:collection {:version hateoas/json-collection-version
                    :href collection-url
                    :links full-links
                    :items (map #(entry-and-links % board-slug org-slug
                                    (or (filter :body (:interactions %)) [])  ; comments only
                                    (or (filter :reaction (:interactions %)) []) ; reactions only
                                    access-level user-id)
                             entries)}}
      {:pretty config/pretty?})))