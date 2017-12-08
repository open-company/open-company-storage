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
  Given a sequence of inidividual reaction interactions, collapse it down to a set of distinct reactions
  that include the count of how many times reacted and the list of author names and IDs that reacted, and
  supplement this list with user favorites (at count 0) if needed, and org favorites (at count 0) if needed.
  "
  [conn org-id user-id entry-reactions]
  (let [reactions (or (vals (reduce reaction-collapse {} (map #(assoc % :count 1) entry-reactions))) [])
        reaction-unicodes (set (map :reaction reactions))
        ;; user's favorite reactions if we need them
        user-faves (when (and user-id (< (count reactions) config/max-favorite-reaction-count))
                      (user-favorites conn user-id))
        filtered-user-faves (remove reaction-unicodes user-faves)
        reactions-and-user-faves (concat reactions
                                         (map #(hash-map :reaction % :count 0 :authors [] :author-ids [])
                                            filtered-user-faves))
        ; org's favorite reactions if we need them
        reaction-unicodes (set (map :reaction reactions-and-user-faves))
        org-faves (when (< (count reactions-and-user-faves) config/max-favorite-reaction-count)
                    (org-favorites conn org-id))
        filtered-org-faves (remove reaction-unicodes org-faves)]
    (concat reactions-and-user-faves (map #(hash-map :reaction % :count 0 :authors [] :author-ids[])
                                        filtered-org-faves))))