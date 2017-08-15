(ns oc.storage.representations.board
  "Resource representations for OpenCompany boards."
  (:require [defun.core :refer (defun)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]))

(def representation-props [:slug :type :name :access :promoted :topics :entries :stories :author :authors :viewers
                          :slack-mirror :created-at :updated-at])

(defun url
  ([org-slug slug :guard string?] (str "/orgs/" org-slug "/boards/" slug))
  ([org-slug board :guard map?] (url org-slug (:slug board))))

(defn- self-link [org-slug slug] (hateoas/self-link (url org-slug slug) {:accept mt/board-media-type}))

(defn- create-link [org-slug slug] (hateoas/create-link (str (url org-slug slug) "/entries/")
                                              {:content-type mt/entry-media-type
                                               :accept mt/entry-media-type}))

(defn- partial-update-link [org-slug slug] (hateoas/partial-update-link (url org-slug slug)
                                              {:content-type mt/board-media-type
                                               :accept mt/board-media-type}))

(defn- add-author-link [org-slug slug] 
  (hateoas/add-link hateoas/POST (str (url org-slug slug ) "/authors/") {:content-type mt/board-author-media-type}))

(defn- add-viewer-link [org-slug slug] 
  (hateoas/add-link hateoas/POST (str (url org-slug slug ) "/viewers/") {:content-type mt/board-viewer-media-type}))

(defn- remove-author-link [org-slug slug user-id]
  (hateoas/remove-link (str (url org-slug slug) "/authors/" user-id)))

(defn- remove-viewer-link [org-slug slug user-id]
  (hateoas/remove-link (str (url org-slug slug) "/viewers/" user-id)))

(defn- up-link [org-slug] (hateoas/up-link (org-rep/url org-slug) {:accept mt/org-media-type}))

(defn- interaction-link [board-uuid]
  (hateoas/link-map "interactions" "GET"
    (str config/interaction-server-ws-url "/interaction-socket/boards/" board-uuid) nil))

(defn- board-collection-links [board org-slug] (assoc board :links [(self-link org-slug (:slug board))]))

(defn- board-links
  [board org-slug board-uuid access-level]
  (let [slug (:slug board)
        ;; Everyone gets these
        links [(self-link org-slug slug) (up-link org-slug)]
        ;; Viewers and authors get a WebSocket link to listen for new interactions
        interaction-links (if (or (= access-level :author)
                                  (= access-level :viewer))
                            (conj links (interaction-link board-uuid))
                            links)
        ;; Authors get board management links
        full-links (if (= access-level :author)
                    (concat interaction-links [
                                   (create-link org-slug slug)
                                   (partial-update-link org-slug slug)
                                   (add-author-link org-slug slug)
                                   (add-viewer-link org-slug slug)])
                    interaction-links)]
    (assoc board :links full-links)))

(defn render-author-for-collection
  "Create a map of the board author for use in a collection in the REST API"
  [org-slug slug user-id]
  {:user-id user-id
   :links [(remove-author-link org-slug slug user-id)]})

(defn render-viewer-for-collection
  "Create a map of the board viewer for use in a collection in the REST API"
  [org-slug slug user-id]
  {:user-id user-id
   :links [(remove-viewer-link org-slug slug user-id)]})

(defn render-board-for-collection
  "Create a map of the board for use in a collection in the REST API"
  [org-slug board]
  (-> board
    (select-keys representation-props)
    (board-collection-links org-slug)))

(defn render-board
  "Create a JSON representation of the board for the REST API"
  [org-slug board access-level]
  (json/generate-string
    (-> board
      (select-keys representation-props)
      (board-links org-slug (:uuid board) access-level))
    {:pretty config/pretty?}))