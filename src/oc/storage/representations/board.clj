(ns oc.storage.representations.board
  "Resource representations for OpenCompany boards."
  (:require [defun.core :refer (defun)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.resources.board :as board-res]))

(def public-representation-props [:uuid :slug :name :access :promoted :topics :entries :created-at :updated-at])
(def representation-props (concat public-representation-props [:slack-mirror :author :authors :viewers]))

(defun url
  ([org-slug slug :guard string?] (str "/orgs/" org-slug "/boards/" slug))
  ([org-slug board :guard map?] (url org-slug (:slug board))))

(defn- self-link 
  ([org-slug slug] (hateoas/self-link (url org-slug slug) {:accept mt/board-media-type}))

  ([org-slug slug options] (hateoas/self-link (url org-slug slug) {:accept mt/board-media-type} options)))


(defn- create-entry-link [org-slug slug] (hateoas/create-link (str (url org-slug slug) "/entries/")
                                                {:content-type mt/entry-media-type
                                                 :accept mt/entry-media-type}))

(defn- partial-update-link [org-slug slug] (hateoas/partial-update-link (url org-slug slug)
                                              {:content-type mt/board-media-type
                                               :accept mt/board-media-type}))

(defn- delete-link [org-slug slug]
  (hateoas/delete-link (url org-slug slug)))

(defn- add-author-link [org-slug slug] 
  (hateoas/add-link hateoas/POST (str (url org-slug slug ) "/authors/") {:content-type mt/board-author-media-type}))

(defn- add-viewer-link [org-slug slug] 
  (hateoas/add-link hateoas/POST (str (url org-slug slug ) "/viewers/") {:content-type mt/board-viewer-media-type}))

(defn- remove-author-link [org-slug slug user-id]
  (hateoas/remove-link (str (url org-slug slug) "/authors/" user-id)))

(defn- remove-viewer-link [org-slug slug user-id]
  (hateoas/remove-link (str (url org-slug slug) "/viewers/" user-id)))

(defn- up-link [org-slug] (hateoas/up-link (org-rep/url org-slug) {:accept mt/org-media-type}))

(defn- board-collection-links [board org-slug draft-count]
  (let [board-slug (:slug board)
        options (if (zero? draft-count) {} {:count draft-count})
        links [(self-link org-slug board-slug options)]
        full-links (if (or (= :author (:access-level board))
                           (= board-slug "drafts"))
          ;; Author gets create link
          (conj links (create-entry-link org-slug board-slug))
          ;; No create link
          links)]
    (assoc board :links full-links)))

(defn- board-links
  [board org-slug board-uuid access-level]
  (let [slug (:slug board)
        ;; Everyone gets these
        links [(self-link org-slug slug) (up-link org-slug)]
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
  [org-slug board access-level]
  (let [rep-props (if (or (= :author access-level) (= :viemer access-level))
                      representation-props
                      public-representation-props)]
    (json/generate-string
      (-> board
        (select-keys rep-props)
        (board-links org-slug (:uuid board) access-level))
      {:pretty config/pretty?})))