(ns oc.storage.representations.board
  "Resource representations for OpenCompany boards."
  (:require [defun.core :refer (defun)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.resources.common :as common]))

(def representation-props [:slug :name :access :promoted :topics :update-template
                           :author :created-at :updated-at])

(defun url
  ([org-slug slug :guard string?] (str "/orgs/" org-slug "/boards/" slug))
  ([org-slug board :guard map?] (url org-slug (:slug board))))

(defn- self-link [org-slug slug] (hateoas/self-link (url org-slug slug) {:accept mt/board-media-type}))

(defn- item-link [org-slug slug] (hateoas/item-link (url org-slug slug) {:accept mt/board-media-type}))

(defn- up-link [org-slug] (hateoas/up-link (org-rep/url org-slug) {:accept mt/org-media-type}))

(defn- select-topics
  "Get all the keys that represent topics in the board and merge the resulting map into the result."
  [result board]
  (merge result (select-keys board (filter common/topic-slug? (keys board)))))

(defn- board-collection-links
  [board org-slug]
  (assoc board :links [
    (item-link org-slug (:slug board))]))

(defn- board-links
  [board org-slug]
  (let [slug (:slug board)]
    (assoc board :links [
      (self-link org-slug slug)
      (up-link org-slug)])))

(defn render-board-for-collection
  "Create a map of the board for use in a collection in the REST API"
  [org-slug board]
  (let [slug (:slug board)]
    (-> board
      (select-keys representation-props)
      (board-collection-links org-slug))))

(defn render-board
  "Create a JSON representation of the board for the REST API"
  [org-slug board]
  (json/generate-string
    (-> board
      (select-keys representation-props)
      (select-topics board)
      (board-links org-slug))
    {:pretty true}))