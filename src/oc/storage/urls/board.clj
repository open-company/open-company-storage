(ns oc.storage.urls.board
  (:require [defun.core :refer (defun)]))

(defun url
  ([org-slug board-slug :guard string? sort-type :guard keyword? params :guard map?]
    (let [concat-str (if (= sort-type :recent-activity) "&" "?")]
      (str (url org-slug board-slug sort-type) concat-str "start=" (:start params) "&direction=" (name (:direction params)))))
  ([org-slug board :guard map?]
    (url org-slug (:slug board) :recently-posted))
  ([org-slug slug :guard string?]
    (url org-slug slug :recently-posted))
  ([org-slug slug :guard string? sort-type :guard keyword?]
    (let [sort-path (when (= sort-type :recent-activity) "?sort=activity")]
      (str "/orgs/" org-slug "/boards/" slug sort-path))))

(defn create-url
  ([org-slug]
   (url org-slug "create-board"))
  ([org-slug board-type]
   (str (create-url org-slug)
        (when board-type
          (str "/" (if (keyword? board-type) (name board-type) board-type))))))

(defn viewer-url
  ([org-slug board-slug]
   (str (url org-slug board-slug) "/viewers"))
  ([org-slug board-slug viewer]
   (str (viewer-url org-slug board-slug)
        (when viewer
          (str "/" (if (keyword? viewer) (name viewer) viewer))))))

(defn author-url
  ([org-slug board-slug]
   (str (url org-slug board-slug) "/authors"))
  ([org-slug board-slug author]
   (str (author-url org-slug board-slug)
        (when author
          (str "/" (if (keyword? author) (name author) author))))))