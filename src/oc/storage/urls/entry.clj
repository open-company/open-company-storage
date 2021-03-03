(ns oc.storage.urls.entry
  (:require [defun.core :refer (defun)]
            [cuerdas.core :as s]
            [oc.storage.config :as config]
            [oc.storage.urls.org :as org-urls]
            [oc.storage.urls.board :as board-urls]))

(defn entries
  ([org-slug]
   (org-urls/entries org-slug))
  ([org-slug board-slug]
   (s/join "/" [(board-urls/board org-slug board-slug) "entries"])))

(defun entry
  ([org-slug board-slug entry-map :guard map?]
   (entry org-slug board-slug (:uuid entry-map)))

  ([org-slug board-slug entry-uuid]
   (s/join "/" [(entries org-slug board-slug) entry-uuid])))

(defun ui-entry
  ([org board entry-map :guard map?]
   (ui-entry org board (:uuid entry-map)))

  ([org board-map :guard map? entry]
   (ui-entry org (or (:slug board-map) (:uuid board-map)) entry))

  ([org :guard map? board entry]
   (ui-entry (or (:slug org) (:uuid org)) board entry))

  ([org-slug board-slug entry-uuid]
   (s/join "/" [config/ui-server-url org-slug board-slug "post" entry-uuid])))

(defun secure-entry
  ([org-slug secure-uuid :guard string?] (s/join "/" [(entries org-slug) secure-uuid]))
  ([org-slug entry-map :guard :secure-uuid] (secure-entry org-slug (:secure-uuid entry-map))))

(defn bookmark
   [org-slug board-slug entry-uuid]
   (s/join "/" [(entry org-slug board-slug entry-uuid) "bookmark"]))

(defn revert
  [org-slug board-slug entry-uuid]
  (s/join "/" [(entry org-slug board-slug entry-uuid) "revert"]))

(defun inbox-action
  [org-slug board-slug entry-uuid inbox-action :guard #{:dismiss :unread :follow :unfollow}]
  (s/join "/" [(entry org-slug board-slug entry-uuid) (name inbox-action)]))

(defn inbox-follow [org-slug board-slug entry-uuid]
  (inbox-action org-slug board-slug entry-uuid :follow))

(defn inbox-unfollow [org-slug board-slug entry-uuid]
  (inbox-action org-slug board-slug entry-uuid :unfollow))

(defn publish
  [org-slug board-slug entry-uuid]
  (s/join "/" [(entry org-slug board-slug entry-uuid) "publish"]))

(defn share
  [org-slug board-slug entry-uuid]
  (s/join "/" [(entry org-slug board-slug entry-uuid) "share"]))


;; Polls

(defn polls [org-slug board-slug entry-uuid]
  (s/join "/" [(entry org-slug board-slug entry-uuid) "polls"]))

(defn poll [org-slug board-slug entry-uuid poll-uuid]
  (s/join "/" [(polls org-slug board-slug entry-uuid) poll-uuid]))

(defn poll-replies [org-slug board-slug entry-uuid poll-uuid]
  (s/join "/" [(poll org-slug board-slug entry-uuid poll-uuid) "replies"]))

(defn poll-reply [org-slug board-slug entry-uuid poll-uuid reply-id]
  (s/join "/" [(poll-replies org-slug board-slug entry-uuid poll-uuid) reply-id]))

(defn poll-reply-vote [org-slug board-slug entry-uuid poll-uuid reply-id]
  (s/join "/" [(poll-reply org-slug board-slug entry-uuid poll-uuid reply-id) "vote"]))