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

(defn- map-kv
  "Utility function to do an operation on the value of every key in a map."
  [f coll]
  (reduce-kv (fn [m k v] (assoc m k (f v))) (empty coll) coll))

(defn- reaction-and-link
  "Given the parts of a reaction URL, return a map representation of the reaction for use in the API."
  [org-uuid board-uuid resource-uuid reaction user?]
  (-> reaction
    (dissoc :author-ids)
    (assoc :reacted (if user? true false))
    (assoc :links [(if user?
                    (unreact-link org-uuid board-uuid resource-uuid reaction)
                    (react-link org-uuid board-uuid resource-uuid reaction))])))

(defn- reaction-selection-sort
  "
  Sort order to select the most used reactions, with a tie breaker for default reactions in their default reaction
  order.
  "
  [reaction]
  (let [index-of (inc (.indexOf config/default-reactions (first reaction)))] ; order in the defaults
    (if (zero? index-of) ; is it in the defaults, or legacy?
      ;; it's legacy, so by reaction count
      (* (last reaction) -1) ; more reactions gives a lower (negative) #, so it has a higher sort
      ;; it's in the defaults, so by reaction count, but with a little extra for tie breaking with other reactions
      (- (* (last reaction) -1) (- 1 (* index-of 0.25)))))) ; the earlier it is in the defaults the bigger the tie breaker

(defn- reaction-order-sort
  "
  Keep the reaction order stable by sorting on the order they are provided, and if they are legacy
  reactions, by the order of how many reactions they have (which while not definitively stable, will
  effectively be fairly stable for legacy reactions).
  "
  [reaction]
  (let [index-of (.indexOf config/default-reactions (first reaction))] ; order in the defaults
    (if (= index-of -1) ; is it in the defaults, or legacy?
      (* (last reaction) 10) ; it's legacy, so by reaction count
      index-of))) ; by order in the defaults

(defn reactions-and-links
  "
  Given a sequence of reactions and the parts of a reaction URL, return a representation of the reactions
  for use in the API.
  "
  [org-uuid board-uuid resource-uuid reactions user-id]
  (let [limited-reactions (take config/max-reaction-count reactions)]
    (map #(reaction-and-link org-uuid board-uuid resource-uuid %
            ; the user left one of these reactions?
            (some (fn [author-id] (= user-id author-id)) (:author-ids %)))
      limited-reactions)))
