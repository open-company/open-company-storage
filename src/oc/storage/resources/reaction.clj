(ns oc.storage.resources.reaction
  (:require [clojure.core.cache :as cache]
            [oc.lib.db.common :as db-common]
            [oc.storage.config :as config]))

;; ----- Favorite Reaction cache -----

(defonce empty-cache (cache/ttl-cache-factory {} :ttl 10000))
(defonce FavoriteReactionCache (atom empty-cache))

;; ----- Favorite Reactions -----

(defn org-favorites
  "Return the most favorite reactions (by number of times used) for the specified org.

  Results will be used from cache for up to 10m w/ a TTL cache."
  [conn org-id]
  (vec (map first 
    (db-common/grouped-resources-by-most-common conn "interactions" "org-uuid"
                                                org-id "reaction" config/max-favorite-reaction-count))))

(defn user-favorites
  "Return the most favorite reactions (by number of times used) for the specified user.

  Results will be used from cache for up to 10m w/ a TTL cache."
  [conn user-id]
  (vec (map first 
    (db-common/grouped-resources-by-most-common conn "interactions" "author-uuid"
                                                user-id "reaction" config/max-favorite-reaction-count))))