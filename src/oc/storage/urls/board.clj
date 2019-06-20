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