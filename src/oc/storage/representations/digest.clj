(ns oc.storage.representations.digest
  "Resource representations for OpenCompany digest."
  (:require [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.urls.org :as org-urls]
            [oc.storage.representations.entry :as entry-rep]
            [oc.storage.config :as config]
            [oc.storage.representations.content :as content]))

(defn- entry-for-digest
  "
  Given an entry and all the metadata about it, render it with only the data needed by the digest.
  Return a subset of the response of oc.storage.representations.entry/entry-and-links
  "
  [org {:keys [access-level] :as board} entry comments user-id]
  (let [entry-uuid (:uuid entry)
        secure-uuid (:secure-uuid entry)
        org-uuid (:org-uuid entry)
        org-slug (:slug org)
        board-uuid (:uuid board)
        board-slug (:slug board)
        board-access (:access board)
        entry-with-comments (assoc entry :interactions comments)
        full-entry (merge {:board-slug board-slug
                           :board-access board-access
                           :board-name (:name board)
                           :new-comments-count (entry-rep/new-comments-count entry-with-comments user-id (:last-read-at entry))
                           :last-activity-at (entry-rep/entry-last-activity-at user-id entry-with-comments)}
                          entry)
        comment-list (if (= access-level :public)
                        []
                        (take config/inline-comment-count (reverse (sort-by :created-at comments))))
        links [(entry-rep/self-link org-slug board-slug entry-uuid)
               (entry-rep/up-link org-slug board-slug)
               (content/comments-link org-uuid board-uuid entry-uuid comments)]]
    (-> full-entry
      (select-keys entry-rep/representation-props)
      (entry-rep/include-secure-uuid secure-uuid access-level)
      (entry-rep/include-interactions comment-list :comments)
      (assoc :links links))))

(defn render-digest
  ""
  [params org _collection-type {:keys [following total-following-count unfollowing total-unfollowing-count]} boards user]
  (let [links [(hateoas/up-link (org-urls/org org) {:accept mt/org-media-type})]
        total-count (+ total-following-count total-unfollowing-count)
        total-entries (+ (count following) (count unfollowing))]
    (json/generate-string
      {:collection {:version hateoas/json-collection-version
                    :links links
                    :direction (:direction params)
                    :start (:start params)
                    :has-more (> total-count total-entries)
                    :total-count total-count
                    :total-following-count total-following-count
                    :following (map (fn [entry]
                                     (let [board (get boards (:board-uuid entry))]
                                       (entry-for-digest org board entry (entry-rep/comments entry) (:user-id user))))
                                following)
                    :total-unfollowing-count total-unfollowing-count
                    :unfollowing (map (fn [entry]
                                     (let [board (get boards (:board-uuid entry))]
                                       (entry-for-digest org board entry (entry-rep/comments entry) (:user-id user))))
                                unfollowing)}}
      {:pretty config/pretty?})))