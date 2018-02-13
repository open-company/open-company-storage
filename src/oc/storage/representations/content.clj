(ns oc.storage.representations.content
  "Resource representations for OpenCompany content resources (which receive comments and reactions)."
  (:require [defun.core :refer (defun defun-)]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]))

(defun- interaction-url

  ([org-uuid board-uuid resource-uuid]
  (str config/interaction-server-url "/orgs/" org-uuid "/boards/" board-uuid "/resources/" resource-uuid "/comments"))

  ([org-uuid board-uuid resource-uuid reaction]
  (str config/interaction-server-url
       "/orgs/" org-uuid "/boards/" board-uuid "/resources/" resource-uuid "/reactions/" reaction "/on")))

(defn comment-link [org-uuid board-uuid resource-uuid]
  (let [comment-url (str (interaction-url org-uuid board-uuid resource-uuid) "/")]
    (hateoas/create-link comment-url {:content-type mt/comment-media-type
                                      :accept mt/comment-media-type})))

(defn- comment-authors
  "Return the latest, up to four, distinct authors."
  [comments]
  (let [authors (map #(assoc (:author %) :created-at (:created-at %)) comments) ; only authors from the comments
        grouped-authors (group-by :user-id authors) ; grouped by each author
        ; select newest comment for each author
        newest-authors (map #(last (sort-by :created-at (get grouped-authors %))) (keys grouped-authors))
        sorted-authors (reverse (sort-by :created-at newest-authors))] ; sort authors
    (take 4 sorted-authors))) ; last 4

(defn comments-link [org-uuid board-uuid resource-uuid comments]
  (let [comment-url (interaction-url org-uuid board-uuid resource-uuid)]
    (hateoas/link-map "comments" hateoas/GET comment-url {:accept mt/comment-collection-media-type}
                                                          {:count (count comments)
                                                           :authors (comment-authors comments)})))

(defn- react-link [org-uuid board-uuid resource-uuid reaction]
  (let [react-url (interaction-url org-uuid board-uuid resource-uuid reaction)]
    (hateoas/link-map "react" hateoas/PUT react-url {})))

(defn- unreact-link [org-uuid board-uuid resource-uuid reaction]
  (let [react-url (interaction-url org-uuid board-uuid resource-uuid reaction)]
    (hateoas/link-map "react" hateoas/DELETE react-url {})))

(defn- reaction-and-link
  "Given the parts of a reaction URL, return a map representation of the reaction for use in the API."
  [org-uuid board-uuid resource-uuid reaction user?]
  (-> reaction
    (assoc :reacted (if user? true false))
    (assoc :links [(if user?
                    (unreact-link org-uuid board-uuid resource-uuid (:reaction reaction))
                    (react-link org-uuid board-uuid resource-uuid (:reaction reaction)))])))

(defn- comment-and-link
  "Given a comment, return a map representation of the comment for use in the API."
  [org-uuid board-uuid resource-uuid comment-res user?]
  (-> comment-res
    (assoc :links (conj (if user?
                    [
                      ; edit link
                      ; delete link
                    ]
                    []) (comment-link org-uuid board-uuid resource-uuid)))))

(defn reactions-and-links
  "
  Given a sequence of reactions and the parts of a interaction URL, return a representation of the reactions
  for use in the API.
  "
  [org-uuid board-uuid resource-uuid reactions user-id]
  (let [limited-reactions (take config/max-reaction-count reactions)]
    (map #(reaction-and-link org-uuid board-uuid resource-uuid %
            ; the user left one of these reactions?
            (some (fn [author-id] (= user-id author-id)) (:author-ids %)))
      limited-reactions)))

(defn comments-and-links
  "
  Given a sequence of comments and the parts of an interaction URL, return a representation of the comments
  for use in the API.
  "
  [org-uuid board-uuid resource-uuid comments user-id]
  (let [limited-comments (take config/inline-comment-count comments)]
    (map #(comment-and-link org-uuid board-uuid resource-uuid % false) limited-comments)))