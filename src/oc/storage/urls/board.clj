(ns oc.storage.urls.board
  (:require [defun.core :refer (defun)]
            [oc.storage.urls.org :as org-urls]
            [oc.storage.urls.activity :as activity-urls]))

(defun board
  ([org board-slug :guard string? params :guard map?]
    (activity-urls/parametrize (board org board-slug) params))

  ([org board-slug :guard string?]
   (str (org-urls/boards org) "/" board-slug))

  ([org board-map :guard map?]
    (board org (:slug board-map))))

(defn create
  [org board-type]
  (str (org-urls/boards org)
       "/create-board/"
       (if (keyword? board-type) (name board-type) board-type)))

(defn create-preflight
  [org]
  (str (create org :team) "/preflight"))

(defn viewer
  ([org board-slug]
   (str (board org board-slug) "/viewers"))
  ([org board-slug viewer-uuid]
   (str (viewer org board-slug)
        (when viewer-uuid
          (str "/" (if (keyword? viewer-uuid) (name viewer-uuid) viewer-uuid))))))

(defn author
  ([org board-slug]
   (str (board org board-slug) "/authors"))
  ([org board-slug author-uuid]
   (str (author org board-slug)
        (when author-uuid
          (str "/" (if (keyword? author-uuid) (name author-uuid) author-uuid))))))