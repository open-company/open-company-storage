(ns oc.storage.util.sort
  (:require [clojure.pprint :as pp]
            [clj-time.core :as t]
            [clj-time.format :as f]))

(defn- max-comment-timestamp
  "Return a map {entry-uuid most-recent-comment-timestap} excluding the user authored comments"
  [user-id start order entry]
  (let [all-comments (filterv #(and (:body %) (not= (-> % :author :user-id) user-id)) (:interactions entry))
        sorted-comments (sort-by :updated-at all-comments)]
    (when (seq sorted-comments)
      (hash-map (:uuid entry) (-> sorted-comments last :updated-at)))))

(defn sort-activity
  "Given a set of entries, return up to the default limit of them, intermixed and sorted."
  [entries sorting start order limit user-id]
  (let [;; Get all the posts published at
        all-published-at (apply merge (map #(hash-map (:uuid %) (:published-at %)) entries))
        sorting-by-recent-activity? (= sorting :recent-activity)
        ;; if sorting by recent activity
        all-comments (if sorting-by-recent-activity?
                     ;; get the last comment timestamp for those that have one
                       (apply merge (remove nil? (map (partial max-comment-timestamp user-id start order) entries)))
                       {})
        ;; merge the published-at and the last comment timestamp results into a usique map
        to-sort-map (merge all-published-at all-comments)
        order-flip (if (= order :desc) -1 1)
        ;; exclude the posts that have a timestamp exceeding the current start filter (depending on the direction)
        filtered-to-sort-map (filter #(pos? (* order-flip (compare (second %) start))) to-sort-map)
        ;; get a sorted map of the posts using the timestamp and the correct order/direction
        sorted-map (into (sorted-map-by (fn [key1 key2]
                          (* order-flip (compare [(get to-sort-map key1) key1]
                                                 [(get to-sort-map key2) key2]))))
                    filtered-to-sort-map)
        ;; filter only the first limit results
        limited-uuids (take limit (keys sorted-map))
        ;; Create a map where each uuid correspond to the max btw published-at and the last comment updated-at
        last-comment-timestamps (apply merge (map #(hash-map (:uuid %)
                                                    (first (vals (max-comment-timestamp user-id start order %))))
                                              entries))
        ;; Add the new-at field used in the client to show the NEW comment badge
        return-entries (map (fn [entry-uuid]
                              (-> (filter #(= (:uuid %) entry-uuid) entries)
                                first
                                (assoc :new-at (last-comment-timestamps entry-uuid)))) limited-uuids)]
    ;; Clean out empty results
    (remove nil? return-entries)))
