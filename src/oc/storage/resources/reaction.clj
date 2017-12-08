(ns oc.storage.resources.reaction
  (:require [clojure.core.cache :as cache]
            [taoensso.timbre :as timbre]
            [oc.lib.db.common :as db-common]
            [oc.storage.config :as config]))

;; ----- Favorite Reaction cache -----

(defonce empty-cache (cache/ttl-cache-factory {} :ttl (* 5 60 1000))) ; TTL of 5 minutes
(defonce FavoriteReactionCache (atom empty-cache))

;; ----- Favorite Reactions -----

(defn- favorites
  [conn uuid field]
  (if-let [cached-response (cache/lookup @FavoriteReactionCache uuid)]
    (do
      (timbre/trace "Favorites cache hit for" (str field ":") uuid)
      cached-response)
    (do
      (timbre/trace "Favorites cache miss for" (str field ":") uuid)
      (let [favs (vec (map first
                  (db-common/grouped-resources-by-most-common conn "interactions" field
                                                              uuid "reaction" config/max-favorite-reaction-count)))]
        (reset! FavoriteReactionCache (cache/miss @FavoriteReactionCache uuid favs))
        favs))))

(defn- org-favorites
  "
  Return the most favorite reactions (by number of times used) for the specified org.

  Results will be used from a TTL cache.
  "
  [conn org-id] (favorites conn org-id "org-uuid"))

(defn- user-favorites
  "
  Return the most favorite reactions (by number of times used) for the specified user.

  Results will be used from a TTL cache.
  "
  [conn user-id] (favorites conn user-id "author-uuid"))

(defn- reaction-collapse
  "Reducer function that collapses reactions into count and sequence of author based on common reaction unicode."
  [reactions reaction]
  (let [unicode (:reaction reaction)
        author (-> reaction :author :name)
        author-id (-> reaction :author :user-id)]
    (if-let [existing-reaction (reactions unicode)]
      ;; have this unicode already, so add this reaction to it
      (assoc reactions unicode (-> existing-reaction
                                  (update :count inc)
                                  (update :authors #(conj % author))
                                  (update :author-ids #(conj % author-id))))
      ;; haven't seen this reaction unicode before, so init a new one
      (assoc reactions unicode {:reaction unicode :count 1 :authors [author] :author-ids [author-id]}))))

(defn reactions-with-favorites
  "
  Given a sequence of individual reaction interactions, collapse it down to a set of distinct reactions
  that include the count of how many times reacted and the list of author names and IDs that reacted, and
  supplement this list with user favorites (at count 0) if needed, and org favorites (at count 0) if needed.
  "
  [conn org-id user-id entry-reactions]
  (let [reactions (or (vals (reduce reaction-collapse {} (map #(assoc % :count 1) entry-reactions))) [])
        reaction-unicodes (set (map :reaction reactions))
        needed-user-favs (- config/max-favorite-reaction-count (count reactions))
        ;; user's favorite reactions if we need them
        user-favs (when (and user-id (pos? needed-user-favs))
                      (user-favorites conn user-id))
        filtered-user-favs (remove reaction-unicodes user-favs)
        reactions-and-user-favs (concat reactions
                                         (map #(hash-map :reaction % :count 0 :authors [] :author-ids [])
                                            (take needed-user-favs filtered-user-favs)))
        ; org's favorite reactions if we need them
        reaction-unicodes (set (map :reaction reactions-and-user-favs))
        needed-org-favs (- config/max-favorite-reaction-count (count reactions-and-user-favs))
        org-favs (when (and org-id (pos? needed-org-favs))
                    (org-favorites conn org-id))
        filtered-org-favs (remove reaction-unicodes org-favs)]
    (concat reactions-and-user-favs (map #(hash-map :reaction % :count 0 :authors [] :author-ids[])
                                        (take needed-org-favs filtered-org-favs)))))