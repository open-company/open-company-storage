(ns oc.storage.urls.board
  (:require [defun.core :refer (defun)]))

(defun url
  ([org-slug board-slug :guard string? params :guard map?]
    (str (url org-slug board-slug) "?start=" (:start params) "&direction=" (name (:direction params))))
  ([org-slug board :guard map?] (url org-slug (:slug board)))
  ([org-slug slug :guard string?]
    (str "/orgs/" org-slug "/boards/" slug)))