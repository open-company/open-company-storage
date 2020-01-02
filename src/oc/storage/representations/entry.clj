(ns oc.storage.representations.entry
  "Resource representations for OpenCompany entries."
  (:require [defun.core :refer (defun defun-)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.urls.board :as board-url]
            [oc.storage.representations.content :as content]
            [oc.storage.lib.sort :as sort]
            [oc.storage.api.access :as access]
            [oc.storage.resources.reaction :as reaction-res]
            [oc.lib.change.resources.read :as read]))

(def org-prop-mapping {:uuid :org-uuid
                       :name :org-name
                       :slug :org-slug
                       :logo-url :org-logo-url
                       :logo-width :org-logo-width
                       :logo-height :org-logo-height})

(def representation-props [:uuid :headline :body :abstract :attachments :status :must-see :sample
                           :org-uuid :org-name :org-slug :org-logo-url :org-logo-width :org-logo-height
                           :board-uuid :board-slug :board-name :board-access
                           :team-id :author :publisher :published-at
                           :video-id :video-processed :video-image :video-duration
                           :created-at :updated-at :revision-id :follow-ups
                           :new-at :new-comments-count])

;; ----- Utility functions -----

(defn comments
  "Return a sequence of just the comments for an entry."
  [{interactions :interactions}]
  (filter :body interactions))

(defn reactions
  "Return a sequence of just the reactions for an entry."
  [{interactions :interactions}]
  (filter :reaction interactions))

(defun- board-of
  ([boards :guard map? entry] 
    (boards (:board-uuid entry)))
  ([board _entry] board))

;; ----- Representation -----

(defun url

  ([org-slug nil]
  (str "/orgs/" org-slug  "/follow-ups"))

  ([org-slug board-slug]
  (str (board-url/url org-slug board-slug) "/entries"))
  
  ([org-slug board-slug entry :guard map?] (url org-slug board-slug (:uuid entry)))

  ([org-slug board-slug entry-uuid]
  (str (url org-slug board-slug) "/" entry-uuid))

  ([org-slug board-slug entry-uuid inbox-action :guard #(and (keyword? %)
                                                             #{:dismiss :follow :unfollow} %)]
  (str (url org-slug board-slug entry-uuid) "/inbox/" (name inbox-action)))

  ([org-slug board-slug entry-uuid follow-up-uuid]
  (str (url org-slug board-slug entry-uuid) "/follow-up/" follow-up-uuid)))

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
    {:content-type mt/entry-media-type
     :accept mt/entry-media-type}))

(defn- react-link [org board entry-uuid]
  (hateoas/link-map "react" hateoas/POST
    (str config/interaction-server-url "/orgs/" (:uuid org) "/boards/" (:uuid board)
                                          "/resources/" entry-uuid "/reactions")
    {:content-type "text/plain"
     :accept mt/reaction-media-type}))

(defn- share-link [org-slug board-slug entry-uuid]
  (hateoas/link-map "share" hateoas/POST (str (url org-slug board-slug entry-uuid) "/share")
    {:content-type mt/share-request-media-type
     :accept mt/entry-media-type}))

(defun- up-link 
  ([org-slug nil]
  (hateoas/up-link (str "/" org-slug) {:accept mt/org-media-type}))
  ([org-slug board-slug]
  (hateoas/up-link (board-url/url org-slug board-slug) {:accept mt/board-media-type})))

(defn- revert-link [org-slug board-slug entry-uuid]
  (hateoas/link-map "revert" hateoas/POST (str (url org-slug board-slug entry-uuid) "/revert")
    {:content-type mt/revert-request-media-type
     :accept mt/entry-media-type}))

(defn- create-follow-up-link [org-slug board-slug entry-uuid]
  (hateoas/link-map "follow-up" hateoas/POST (str (url org-slug board-slug entry-uuid) "/follow-up")
    {:accept mt/entry-media-type
     :content-type mt/follow-up-request-media-type}))

(defn- complete-follow-up-link [org-slug board-slug entry-uuid follow-up-uuid]
  (hateoas/link-map "mark-complete" hateoas/POST (str (url org-slug board-slug entry-uuid follow-up-uuid) "/complete")
    {:accept mt/entry-media-type}))

(defn- inbox-dismiss-link [org-slug board-slug entry-uuid]
  (hateoas/link-map "dismiss" hateoas/POST (url org-slug board-slug entry-uuid :dismiss)
    {:accept mt/entry-media-type
     :content-type "text/plain"}))

(defn- inbox-follow-link [org-slug board-slug entry-uuid]
  (hateoas/link-map "follow" hateoas/POST (url org-slug board-slug entry-uuid :follow)
    {:accept mt/entry-media-type
     :content-type "text/plain"}))

(defn- inbox-unfollow-link [org-slug board-slug entry-uuid]
  (hateoas/link-map "unfollow" hateoas/POST (url org-slug board-slug entry-uuid :unfollow)
    {:accept mt/entry-media-type
     :content-type "text/plain"}))

(defn- include-secure-uuid
  "Include secure UUID property for authors."
  [entry secure-uuid access-level]
  (assoc entry :secure-uuid secure-uuid))

(defn- include-interactions
  "Include interactions only if we have some."
  [entry collection key-name]
  (if (empty? collection)
    entry
    (assoc entry key-name collection)))

(defn- entry-and-links
  "
  Given an entry and all the metadata about it, render an access level appropriate rendition of the entry
  for use in an API response.
  "
  ([org board entry comments reactions access-level user-id] 
  (entry-and-links org board entry comments reactions access-level user-id false))

  ([org board entry comments reactions access-level user-id secure-access?]
  (let [entry-uuid (:uuid entry)
        secure-uuid (:secure-uuid entry)
        org-uuid (:org-uuid entry)
        org-slug (:slug org)
        board-uuid (:uuid board)
        board-slug (:slug board)
        board-access (:access board)
        draft? (= :draft (keyword (:status entry)))
        entry-with-comments (assoc entry :interactions comments)
        has-new-comments-count? (contains? entry :new-comments-count)
        entry-read (when-not has-new-comments-count?
                     (read/retrieve-by-user-item config/dynamodb-opts user-id (:uuid entry)))
        full-entry (merge {:board-slug board-slug
                           :board-access board-access
                           :board-name (:name board)
                           :new-comments-count (if has-new-comments-count?
                                                 (:new-comments-count entry)
                                                 (sort/new-comments-count entry-with-comments user-id entry-read))
                           :new-at (-> (sort/entry-new-at user-id entry-with-comments true)
                                    vals
                                    first)} entry)
        reaction-list (if (= access-level :public)
                        []
                        (content/reactions-and-links org-uuid board-uuid entry-uuid reactions user-id))
        comment-list (if (= access-level :public)
                        []
                        (take config/inline-comment-count (reverse (sort-by :created-at comments))))
        follow-ups-list (if (= access-level :public)
                          []
                          (map #(if (= (-> % :assignee :user-id) user-id)
                                  (assoc % :links [(complete-follow-up-link org-slug board-slug entry-uuid (:uuid %))])
                                  %)
                           (:follow-ups entry)))
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
                                   (revert-link org-slug board-slug entry-uuid)
                                   (content/comment-link org-uuid board-uuid entry-uuid)
                                   (content/comments-link org-uuid board-uuid entry-uuid comments)
                                   (content/mark-unread-link entry-uuid)])
                    ;; Access by viewers get comments
                    (= access-level :viewer)
                    (concat links [(content/comment-link org-uuid board-uuid entry-uuid)
                                   (content/comments-link org-uuid board-uuid entry-uuid comments)
                                   (content/mark-unread-link entry-uuid)])
                    ;; Everyone else is read-only
                    :else links)
        react-links (if (and
                          (or (= access-level :author) (= access-level :viewer))
                          (< (count reaction-list) config/max-reaction-count))
                      ;; Authors and viewers need a link to post fresh new reactions, unless we're maxed out
                      (conj more-links (react-link org board entry-uuid))
                      more-links)
        user-visibility (when user-id
                          (some (fn [[k v]] (when (= k (keyword user-id)) v)) (:user-visibility entry)))
        full-links (cond
              ;; Drafts need a publish link
              draft?
              (conj react-links (publish-link org-slug board-slug entry-uuid))
              ;; Indirect access via the board, rather than direct access by the secure ID
              ;; needs a share link
              (and (not secure-access?) (or (= access-level :author) (= access-level :viewer)))
              (conj react-links (share-link org-slug board-slug entry-uuid)
               (create-follow-up-link org-slug board-slug entry-uuid)
               (inbox-dismiss-link org-slug board-slug entry-uuid)
               (if (:unfollow user-visibility)
                 (inbox-follow-link org-slug board-slug entry-uuid)
                 (inbox-unfollow-link org-slug board-slug entry-uuid)))
              ;; Otherwise just the links they already have
              :else react-links)]
    (-> (if secure-access?
          ;; "stand-alone", so include extra props
          (-> org
            (clojure.set/rename-keys org-prop-mapping)
            (merge full-entry))
          full-entry)
      (select-keys representation-props)
      (include-secure-uuid secure-uuid access-level)
      (include-interactions reaction-list :reactions)
      (include-interactions comment-list :comments)
      (assoc :follow-ups follow-ups-list)
      (assoc :links full-links)))))

(defn render-entry-for-collection
  "Create a map of the entry for use in a collection in the API"
  ([org board entry comments reactions access-level user-id]
  (render-entry-for-collection org board entry comments reactions access-level user-id false))

  ([org board entry comments reactions access-level user-id secure-access?]
  (entry-and-links org board entry comments reactions access-level user-id secure-access?)))

(defn render-entry
  "Create a JSON representation of the entry for the API"
  ([org board entry comments reactions access-level user-id]
  (render-entry org board entry comments reactions access-level user-id false))

  ([org board entry comments reactions access-level user-id secure-access?]
  (let [entry-uuid (:uuid entry)]
    (json/generate-string
      (render-entry-for-collection org board entry comments reactions access-level user-id secure-access?)
      {:pretty config/pretty?}))))

(defn render-entry-list
  "
  Given an org and a board or a map of boards, a sequence of entry maps, and access control levels, 
  create a JSON representation of a list of entries for the API.
  "
  [org board-or-boards entries ctx]
  (let [org-slug (:slug org)
        board-slug (:slug board-or-boards)
        collection-url (url org-slug board-slug)
        links [(hateoas/self-link collection-url {:accept mt/entry-collection-media-type})
               (up-link org-slug board-slug)]
        full-links (if (= (:access-level ctx) :author)
                      (conj links (create-link org-slug board-slug))
                      links)
        user (:user ctx)]
    (json/generate-string
      {:collection {:version hateoas/json-collection-version
                    :href collection-url
                    :links full-links
                    :items (map #(let [board (board-of board-or-boards %)
                                       access-level (:access-level (access/access-level-for org board user))]
                                   (entry-and-links org board %
                                    (or (filter :body (:interactions %)) [])  ; comments only
                                    (reaction-res/aggregate-reactions (or (filter :reaction (:interactions %)) [])) ; reactions only
                                    access-level (:user-id user)))
                             entries)}}
      {:pretty config/pretty?})))