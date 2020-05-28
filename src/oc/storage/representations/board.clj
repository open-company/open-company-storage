(ns oc.storage.representations.board
  "Resource representations for OpenCompany boards."
  (:require [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.resources.board :as board-res]
            [oc.storage.urls.board :as board-url]
            [oc.storage.representations.entry :as entry-rep]
            [oc.storage.resources.reaction :as reaction-res]))

(defn drafts-board? [board]
  (or (= (:uuid board) (:uuid board-res/default-drafts-board))
      (= (:slug board) (:slug board-res/default-drafts-board))))

(def public-representation-props [:uuid :slug :name :access :promoted :entries :created-at :updated-at :links :description :last-entry-at])
(def representation-props (concat public-representation-props [:slack-mirror :author :authors :viewers :draft :publisher-board :total-count]))
(def drafts-board-representation-props (conj public-representation-props :total-count))

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

(defn- pagination-link
  "Add `next` links for pagination as needed."
  [org board {:keys [start direction sort-type]} data]
  (let [activity (:entries data)
        activity? (not-empty activity)
        last-activity (last activity)
        last-activity-date (when activity? (:last-activity-at last-activity))
        next? (= (:next-count data) config/default-activity-limit)
        next-url (when next? (board-url/url org board sort-type {:start last-activity-date :direction direction}))
        next-link (when next-url (hateoas/link-map "next" hateoas/GET next-url {:accept mt/board-media-type}))]
    next-link))

(defn- board-collection-links [board org-slug draft-count]
  (let [board-slug (:slug board)
        options (if (zero? draft-count) {} {:count draft-count})
        is-drafts-board? (drafts-board? board)
        links (remove nil?
               [(self-link org-slug board-slug :recently-posted options)
                (when-not is-drafts-board?
                  (self-link org-slug board-slug :recent-activity options))])
        full-links (if (and (= :author (:access-level board))
                            (not is-drafts-board?))
          ;; Author gets create link
          (conj links (create-entry-link org-slug board-slug))
          ;; No create link
          links)]
    (assoc board :links full-links)))

(defn- board-links
  [board org-slug access-level params]
  (let [slug (:slug board)
        is-drafts-board? (drafts-board? board)
        page-link (when-not is-drafts-board? (pagination-link org-slug slug params board))
        ;; Everyone gets these
        links (remove nil? [page-link
                            (self-link org-slug slug :recently-posted)
                            (when-not is-drafts-board?
                              (self-link org-slug slug :recent-activity))
                            (up-link org-slug)])
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
  (entry-rep/render-entry-for-collection org board entry (entry-rep/comments entry) (reaction-res/aggregate-reactions (entry-rep/reactions entry)) access-level user-id))

(defn render-board-for-collection
  "Create a map of the board for use in a collection in the REST API"
  ([org-slug board] (render-board-for-collection org-slug board 0))

  ([org-slug board draft-entry-count]
  (let [this-board-count (if (drafts-board? board) draft-entry-count 0)]
    (-> board
      (select-keys (conj representation-props :access-level))
      (board-collection-links org-slug this-board-count)
      (dissoc :access-level)))))

(defn render-board
  "Create a JSON representation of the board for the REST API"
  [org board ctx params]
  (let [access-level (:access-level ctx)
        viewer-or-author? (or (= :author access-level) (= :viewer access-level))
        is-drafts-board? (drafts-board? board)
        rep-props (cond viewer-or-author?
                        representation-props
                        is-drafts-board?
                        drafts-board-representation-props
                        :else
                        public-representation-props)
        boards-map (:existing-org-boards ctx)
        authors (:authors board)
        author-reps (map #(render-author-for-collection (:slug org) (:slug board) % access-level) authors)
        viewers (:viewers board)
        viewer-reps (map #(render-viewer-for-collection (:slug org) (:slug board) % access-level) viewers)]
    (json/generate-string
      (-> board
        (assoc :authors author-reps)
        (assoc :viewers viewer-reps)
        (board-links (:slug org) access-level params)
        (assoc :entries (map #(let [entry-board (if is-drafts-board? (boards-map (:board-uuid %)) board)]
                                (render-entry-for-collection org entry-board % access-level (-> ctx :user :user-id)))
                         (:entries board)))
        (select-keys rep-props))
      {:pretty config/pretty?})))