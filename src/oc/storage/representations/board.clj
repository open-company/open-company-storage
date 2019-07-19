(ns oc.storage.representations.board
  "Resource representations for OpenCompany boards."
  (:require [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.resources.board :as board-res]
            [oc.storage.urls.board :as board-url]
            [oc.storage.representations.entry :as entry-rep]))

(def public-representation-props [:uuid :slug :name :access :promoted :entries :created-at :updated-at :links])
(def representation-props (concat public-representation-props [:slack-mirror :author :authors :viewers :draft]))

(defn- self-link 
  ([org-slug slug sort-type]
    (self-link org-slug slug sort-type {}))
  ([org-slug slug sort-type options]
  (let [rel (if (= sort-type :recent-activity) "activity" "self")
        board-url (board-url/url org-slug slug sort-type)]
    (hateoas/link-map rel hateoas/GET board-url {:accept mt/board-media-type} options))))


(defn- create-entry-link [org-slug slug] (hateoas/create-link (str (board-url/url org-slug slug) "/entries/")
                                                {:content-type mt/entry-media-type
                                                 :accept mt/entry-media-type}))

(defn- partial-update-link [org-slug slug] (hateoas/partial-update-link (board-url/url org-slug slug)
                                              {:content-type mt/board-media-type
                                               :accept mt/board-media-type}))

(defn- delete-link [org-slug slug]
  (hateoas/delete-link (board-url/url org-slug slug)))

(defn- add-author-link [org-slug slug] 
  (hateoas/add-link hateoas/POST (str (board-url/url org-slug slug) "/authors/") {:content-type mt/board-author-media-type}))

(defn- add-viewer-link [org-slug slug] 
  (hateoas/add-link hateoas/POST (str (board-url/url org-slug slug) "/viewers/") {:content-type mt/board-viewer-media-type}))

(defn- remove-author-link [org-slug slug user-id]
  (hateoas/remove-link (str (board-url/url org-slug slug) "/authors/" user-id)))

(defn- remove-viewer-link [org-slug slug user-id]
  (hateoas/remove-link (str (board-url/url org-slug slug) "/viewers/" user-id)))

(defn- up-link [org-slug] (hateoas/up-link (org-rep/url org-slug) {:accept mt/org-media-type}))

(defn- pagination-links
  "Add `next` and/or `prior` links for pagination as needed."
  [org board sort-type {:keys [start start? direction]} data]
  (let [activity (:entries data)
        activity? (not-empty activity)
        last-activity (last activity)
        first-activity (first activity)
        last-activity-date (when activity? (or (:published-at last-activity) (:created-at last-activity)))
        first-activity-date (when activity? (or (:published-at first-activity) (:created-at first-activity)))
        next? (or (= (:direction data) :previous)
                  (= (:next-count data) config/default-activity-limit))
        next-url (when next? (board-url/url org board sort-type {:start last-activity-date :direction :before}))
        next-link (when next-url (hateoas/link-map "next" hateoas/GET next-url {:accept mt/board-media-type}))
        prior? (and start?
                    (or (= (:direction data) :next)
                        (= (:previous-count data) config/default-activity-limit)))
        prior-url (when prior? (board-url/url org board sort-type {:start first-activity-date :direction :after}))
        prior-link (when prior-url (hateoas/link-map "previous" hateoas/GET prior-url {:accept mt/board-media-type}))]
    (remove nil? [next-link prior-link])))

(defn- board-collection-links [board org-slug draft-count]
  (let [board-slug (:slug board)
        options (if (zero? draft-count) {} {:count draft-count})
        is-draft-board? (= board-slug "drafts")
        links (remove nil?
               [(self-link org-slug board-slug :recently-posted options)
                (when-not is-draft-board?
                   (self-link org-slug board-slug :recent-activity options))])
        full-links (if (and (= :author (:access-level board))
                            (not is-draft-board?))
          ;; Author gets create link
          (conj links (create-entry-link org-slug board-slug))
          ;; No create link
          links)]
    (assoc board :links full-links)))

(defn- board-links
  [board org-slug sort-type access-level params]
  (let [slug (:slug board)
        is-drafts-board? (= slug "drafts")
        pagination-links (if is-drafts-board? [] (pagination-links org-slug slug sort-type params board))
        ;; Everyone gets these
        links (remove nil?
               (concat pagination-links [(self-link org-slug slug :recently-posted)
                                         (when-not is-drafts-board?
                                            (self-link org-slug slug :recent-activity))
                                        (up-link org-slug)]))
        ;; Authors get board management links
        full-links (if (= access-level :author)
                     (concat links [(create-entry-link org-slug slug)
                                    (partial-update-link org-slug slug)
                                    (delete-link org-slug slug)
                                    (add-author-link org-slug slug)
                                    (add-viewer-link org-slug slug)])
                     links)]
    (assoc board :links full-links)))

(defn render-author-for-collection
  "Create a map of the board author for use in a collection in the REST API"
  [org-slug slug user-id access-level]
  {:user-id user-id
   :links (if (= access-level :author) [(remove-author-link org-slug slug user-id)] [])})

(defn render-viewer-for-collection
  "Create a map of the board viewer for use in a collection in the REST API"
  [org-slug slug user-id access-level]
  {:user-id user-id
   :links (if (= access-level :author) [(remove-viewer-link org-slug slug user-id)] [])})

(defn render-entry-for-collection
  "Create a map of the activity for use in a collection in the API"
  [org board entry access-level user-id]
  (entry-rep/render-entry-for-collection org board entry (entry-rep/comments entry) (entry-rep/reactions entry) access-level user-id))

(defn render-board-for-collection
  "Create a map of the board for use in a collection in the REST API"
  ([org-slug board] (render-board-for-collection org-slug board 0))

  ([org-slug board draft-entry-count]
  (let [this-board-count (if (= (:uuid board) (:uuid board-res/default-drafts-board)) draft-entry-count 0)]
    (-> board
      (select-keys (conj representation-props :access-level))
      (board-collection-links org-slug this-board-count)
      (dissoc :access-level)))))

(defn render-board
  "Create a JSON representation of the board for the REST API"
  [org sort-type board ctx params]
  (let [access-level (:access-level ctx)
        rep-props (if (or (= :author access-level) (= :viewer access-level))
                      representation-props
                      public-representation-props)]
    (json/generate-string
      (-> board
        (board-links (:slug org) sort-type access-level params)
        (select-keys rep-props))
      {:pretty config/pretty?})))