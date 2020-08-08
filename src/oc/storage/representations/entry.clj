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
                           :board-uuid :board-slug :board-name :board-access :publisher-board
                           :team-id :author :publisher :published-at :video-id :video-processed
                           :video-image :video-duration :created-at :updated-at :revision-id
                           :new-comments-count :bookmarked-at :polls :last-read-at :last-activity-at :sort-value
                           :unseen-comments])

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
  (str "/orgs/" org-slug  "/bookmarks"))

  ([org-slug board-slug]
  (str (board-url/url org-slug board-slug) "/entries"))
  
  ([org-slug board-slug entry :guard map?] (url org-slug board-slug (:uuid entry)))

  ([org-slug board-slug entry-uuid]
  (str (url org-slug board-slug) "/" entry-uuid))

  ([org-slug board-slug entry-uuid inbox-action :guard #(and (keyword? %)
                                                             #{:dismiss :unread :follow :unfollow} %)]
  (str (url org-slug board-slug entry-uuid) "/" (name inbox-action)))

  ([org-slug board-slug entry-uuid _bookmark? :guard true?]
  (str (url org-slug board-slug entry-uuid) "/bookmark/")))

(defn self-link [org-slug board-slug entry-uuid]
  (hateoas/self-link (url org-slug board-slug entry-uuid) {:accept mt/entry-media-type}))

(defn- secure-self-link [org-slug entry-uuid]
  (hateoas/self-link (org-rep/secure-url org-slug entry-uuid) {:accept mt/entry-media-type}))

(defn secure-link [org-slug secure-uuid]
  (hateoas/link-map "secure" hateoas/GET (org-rep/secure-url org-slug secure-uuid) {:accept mt/entry-media-type}))

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

(defun up-link 
  ([org-slug nil]
  (hateoas/up-link (str "/" org-slug) {:accept mt/org-media-type}))
  ([org-slug board-slug]
  (hateoas/up-link (board-url/url org-slug board-slug) {:accept mt/board-media-type})))

(defn- revert-link [org-slug board-slug entry-uuid]
  (hateoas/link-map "revert" hateoas/POST (str (url org-slug board-slug entry-uuid) "/revert")
    {:content-type mt/revert-request-media-type
     :accept mt/entry-media-type}))

(defn- add-bookmark-link [org-slug board-slug entry-uuid]
  (hateoas/link-map "bookmark" hateoas/POST (url org-slug board-slug entry-uuid true)
    {:accept mt/entry-media-type}))

(defn- remove-bookmark-link [org-slug board-slug entry-uuid]
  (hateoas/link-map "bookmark" hateoas/DELETE (url org-slug board-slug entry-uuid true)
    {:accept mt/entry-media-type}))

(defn- inbox-dismiss-link [org-slug board-slug entry-uuid]
  (hateoas/link-map "dismiss" hateoas/POST (url org-slug board-slug entry-uuid :dismiss)
    {:accept mt/entry-media-type
     :content-type "text/plain"}))

(defn- inbox-unread-link [org-slug board-slug entry-uuid]
  (hateoas/link-map "unread" hateoas/POST (url org-slug board-slug entry-uuid :unread)
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

(defn- poll-url [org-slug board-slug entry-uuid poll-uuid]
  (str (url org-slug board-slug entry-uuid) "/polls/" poll-uuid))

(defn- poll-replies-url [org-slug board-slug entry-uuid poll-uuid]
  (str (poll-url org-slug board-slug entry-uuid poll-uuid) "/replies"))

(defn- poll-reply-url [org-slug board-slug entry-uuid poll-uuid reply-id]
  (str (poll-replies-url org-slug board-slug entry-uuid poll-uuid) "/" reply-id))

(defn- poll-reply-vote-url [org-slug board-slug entry-uuid poll-uuid reply-id]
  (str (poll-reply-url org-slug board-slug entry-uuid poll-uuid reply-id) "/vote"))

(defn- poll-add-reply-link [org-slug board-slug entry-uuid poll-uuid]
  (hateoas/link-map "reply" hateoas/POST (poll-replies-url org-slug board-slug entry-uuid poll-uuid)
    {:accept mt/poll-reply-media-type
     :content-type "text/plain"}))

(defn- poll-delete-reply-link [org-slug board-slug entry-uuid poll-uuid reply-id]
  (hateoas/link-map "delete" hateoas/DELETE (poll-reply-url org-slug board-slug entry-uuid poll-uuid reply-id)
    {:accept mt/poll-reply-media-type}))

(defn- poll-vote-link [org-slug board-slug entry-uuid poll-uuid reply-id]
  (hateoas/link-map "vote" hateoas/POST (poll-reply-vote-url org-slug board-slug entry-uuid poll-uuid reply-id)
    {:accept mt/poll-media-type}))

(defn- poll-unvote-link [org-slug board-slug entry-uuid poll-uuid reply-id]
  (hateoas/link-map "unvote" hateoas/DELETE (poll-reply-vote-url org-slug board-slug entry-uuid poll-uuid reply-id)
    {:accept mt/poll-media-type}))

(defn include-secure-uuid
  "Include secure UUID property for authors."
  [entry secure-uuid access-level]
  (assoc entry :secure-uuid secure-uuid))

(defn include-interactions
  "Include interactions only if we have some."
  [entry collection key-name]
  (if (empty? collection)
    entry
    (assoc entry key-name collection)))

(defun new-comments-count

  ([entry user-id entry-read :guard map?]
   (new-comments-count (:read-at entry-read)))

  ([entry user-id entry-read-at :guard #(or (nil? %) (string? %))]
  (let [all-comments (filterv :body (:interactions entry))
        filtered-comments (filterv #(not= (-> % :author :user-id) user-id) all-comments)]
    (if (and filtered-comments
             entry-read-at)
      (count (filter #(pos? (compare (:created-at %) entry-read-at)) filtered-comments))
      (count filtered-comments)))))

(defn- unseen-comments? [entry user-id container-seen-at]
  (let [all-comments (filter :body (:interactions entry))
        filtered-comments (filter #(not= (-> % :author :user-id) user-id) all-comments)
        all-unseens (if (and filtered-comments
                             (seq container-seen-at))
                      (filter #(pos? (compare (:created-at %) container-seen-at)) filtered-comments)
                      filtered-comments)]
    (pos? (count all-unseens))))

(defn entry-last-activity-at
  "Return the most recent created-at of the comments, exclude comments from current user if needed."
  [user-id entry]
  (let [all-comments (filterv :body (:interactions entry))
        filtered-comments (filterv #(not= (-> % :author :user-id) user-id) all-comments)
        sorted-comments (sort-by :created-at filtered-comments)]
    (when (seq sorted-comments)
      (-> sorted-comments last :created-at))))

(defn- poll-replies-with-links [poll-replies org-slug board-slug entry-uuid poll-uuid user-id]
  (zipmap
   (mapv (comp keyword :reply-id) (vals poll-replies))
   (mapv (fn [reply]
          (let [user-voted? (and user-id
                                 (seq (filterv #(= % user-id) (:votes reply))))]
            (assoc reply :links (remove nil?
             [(if user-voted?
                (poll-unvote-link org-slug board-slug entry-uuid poll-uuid (:reply-id reply))
                (poll-vote-link org-slug board-slug entry-uuid poll-uuid (:reply-id reply)))
              (when (= user-id (-> reply :author :user-id))
                (poll-delete-reply-link org-slug board-slug entry-uuid poll-uuid (:reply-id reply)))]))))
     (vals poll-replies))))

(defn- polls-with-links [polls org-slug board-slug entry-uuid user-id]
  (zipmap
   (mapv (comp keyword :poll-uuid) (vals polls))
   (mapv (fn [poll] (-> poll
          (update :replies #(poll-replies-with-links % org-slug board-slug entry-uuid (:poll-uuid poll) user-id))
          (assoc :links
           (remove nil?
            [(when (:can-add-reply poll)
               (poll-add-reply-link org-slug board-slug entry-uuid (:poll-uuid poll)))]))))
     (vals polls))))

(defn- entry-and-links
  "
  Given an entry and all the metadata about it, render an access level appropriate rendition of the entry
  for use in an API response.
  "
  ([org board entry comments reactions access-level user-id] 
  (entry-and-links org board entry comments reactions access-level user-id false))

  ([org board entry comments reactions {:keys [access-level role] :as access} user-id secure-access?]
  (let [entry-uuid (:uuid entry)
        secure-uuid (:secure-uuid entry)
        org-uuid (:org-uuid entry)
        org-slug (:slug org)
        board-uuid (:uuid board)
        board-slug (:slug board)
        board-access (:access board)
        draft? (= :draft (keyword (:status entry)))
        entry-with-comments (assoc entry :interactions comments)
        bookmark (some #(when (= (:user-id %) user-id) %) (:bookmarks entry))
        enrich-entry? (and (not draft?)
                           user-id)
        entry-read (when enrich-entry?
                     (read/retrieve-by-user-item config/dynamodb-opts user-id (:uuid entry)))
        rendered-polls (when (seq (:polls entry))
                         (polls-with-links (:polls entry) org-slug board-slug entry-uuid user-id))
        full-entry (merge {:board-slug board-slug
                           :board-access board-access
                           :board-name (:name board)
                           :publisher-board (:publisher-board board)
                           :bookmarked-at (:bookmarked-at bookmark)
                           :last-read-at (:read-at entry-read)
                           :new-comments-count (when enrich-entry?
                                                 (new-comments-count entry-with-comments user-id (:read-at entry-read)))
                           :unseen-comments (when enrich-entry?
                                               (unseen-comments? entry-with-comments user-id (:container-seen-at entry)))
                           :last-activity-at (when enrich-entry?
                                               (entry-last-activity-at user-id entry-with-comments))}
                          entry)
        reaction-list (if (= access-level :public)
                        []
                        (content/reactions-and-links org-uuid board-uuid entry-uuid reactions user-id))
        comment-list (if (= access-level :public)
                        []
                        (take config/inline-comment-count (reverse (sort-by :created-at comments))))
        bookmarks-link (when (not= access-level :public)
                         (if bookmark
                           (remove-bookmark-link org-slug board-slug entry-uuid)
                           (add-bookmark-link org-slug board-slug entry-uuid)))
        user-visibility (when user-id
                          (some (fn [[k v]] (when (= k (keyword user-id)) v)) (:user-visibility entry)))
        inbox-link (if (:unfollow user-visibility)
                     (inbox-follow-link org-slug board-slug entry-uuid)
                     (inbox-unfollow-link org-slug board-slug entry-uuid))
        links (cond-> []
               ;; secure UUID access
               secure-access?
               (conj (secure-self-link org-slug secure-uuid))
               ;; normal access
               (not secure-access?)
               (concat [(self-link org-slug board-slug entry-uuid)
                        (up-link org-slug board-slug)])
               ;; Generic links for all team members
               (and (not secure-access?)
                    (or (= access-level :author)
                        (= access-level :viewer)))
               (concat [(share-link org-slug board-slug entry-uuid)
                         bookmarks-link
                        (inbox-unread-link org-slug board-slug entry-uuid)
                        (inbox-dismiss-link org-slug board-slug entry-uuid)
                        inbox-link])
               ;; Only admins and the owner can edit or delete the entry
               (and (not secure-access?)
                    (or draft?
                        (= role :admin)
                        (and (= access-level :author)
                             (= user-id (:user-id (first (:author entry)))))))
               (concat [(partial-update-link org-slug board-slug entry-uuid)
                        (delete-link org-slug board-slug entry-uuid)
                        (revert-link org-slug board-slug entry-uuid)])
               ;; Secure link only for authors
               (and (not draft?)
                    (not secure-access?)
                    (= access-level :author))
               (conj (secure-link org-slug secure-uuid))
               ;; Accessing their drafts, or access by an author, both get interaction links
               (or (and draft?
                        (not secure-access?))
                   (= access-level :author)
                   (= access-level :viewer))
               (concat [(content/comment-link org-uuid board-uuid entry-uuid)
                        (content/comments-link org-uuid board-uuid entry-uuid comments)
                        (content/mark-unread-link entry-uuid)])
               ;; Access by viewers get comments
               (= access-level :viewer)
               (concat [(content/comment-link org-uuid board-uuid entry-uuid)
                       (content/comments-link org-uuid board-uuid entry-uuid comments)
                       (content/mark-unread-link entry-uuid)])
               ;; All memebers can react but only if there are less than x reactions
               (and (or (= access-level :author) (= access-level :viewer))
                    (< (count reaction-list) config/max-reaction-count))
               (conj (react-link org board entry-uuid))
               ;; Drafts need a publish link
               draft?
               (conj (publish-link org-slug board-slug entry-uuid)))]
    (-> (if secure-access?
          ;; "stand-alone", so include extra props
          (-> org
            (clojure.set/rename-keys org-prop-mapping)
            (merge full-entry))
          full-entry)
      (assoc :polls rendered-polls)
      (select-keys representation-props)
      (include-secure-uuid secure-uuid access-level)
      (include-interactions reaction-list :reactions)
      (include-interactions comment-list :comments)
      (assoc :links links)))))

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
                                    (select-keys ctx [:access-level :role]) (:user-id user)))
                             entries)}}
      {:pretty config/pretty?})))

(defn render-entry-poll [entry poll ct]
  (json/generate-string
    poll
    {:pretty config/pretty?}))