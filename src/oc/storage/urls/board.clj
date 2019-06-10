(ns oc.storage.urls.board
  (:require [defun.core :refer (defun)]))

(defun url
  ([org-slug board-slug :guard string? sort-type :guard keyword? params :guard map?]
    (str (url org-slug board-slug sort-type) "?start=" (:start params) "&direction=" (name (:direction params))))
  ([org-slug board :guard map?]
    (url org-slug (:slug board) :recently-posted))
  ([org-slug slug :guard string?]
    (url org-slug slug :recently-posted))
  ([org-slug slug :guard string? sort-type :guard keyword?]
    (let [sort-path (if (= sort-type :recently-posted) "" "/recent")]
      (str "/orgs/" org-slug "/boards/" slug sort-path))))