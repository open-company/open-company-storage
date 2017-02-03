(ns oc.storage.representations.board
  "Resource representations for OpenCompany boards."
  (:require [defun.core :refer (defun)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.representations.media-types :as mt]))

(def representation-props [:slug :name :access :promoted :update-template
                           :author :created-at :updated-at])

(defn- board-collection-links
  [board org-slug]
  (assoc board :links []))

(defn render-board-for-collection
  "Create a map of the board for use in a collection in the REST API"
  [org-slug board]
  (println org-slug)
  (println board)
  (let [slug (:slug board)]
    (-> board
      (select-keys representation-props)
      (board-collection-links org-slug))))