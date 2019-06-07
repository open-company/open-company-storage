(ns oc.storage.util.sort
  (:require [clojure.pprint :as pp]
            [clj-time.core :as t]
            [clj-time.format :as f]))

(defn- max-comment-timestamp
  "Return a map {entry-uuid most-recent-comment-timestap} excluding the user authored comments"
  [user-id entry]
  (let [all-comments (filterv #(and (:body %) (not= (-> % :author :user-id) user-id)) (:interactions entry))
        sorted-comments (sort-by :updated-at all-comments)]
    (when (seq sorted-comments)
      (hash-map (:uuid entry) (-> sorted-comments last :updated-at)))))

(defn sort-activity
  "Given a set of entries, return up to the default limit of them, intermixed and sorted."
  [entries sorting order limit user-id]
  (let [all-published-at (apply merge (map #(hash-map (:uuid %) (:published-at %)) entries))
        sorting-by-most-recent? (= sorting :most-recent)
        all-comments (if sorting-by-most-recent?
                       (apply merge (remove nil? (map (partial max-comment-timestamp user-id) entries)))
                       {})
        to-sort-map (merge all-published-at all-comments)
        order-flip (if (= order :desc) -1 1)
        sorted-map (into (sorted-map-by (fn [key1 key2]
                          (* order-flip (compare [(get to-sort-map key1) key1]
                                                 [(get to-sort-map key2) key2]))))
                    to-sort-map)
        limited-uuids (take limit (keys sorted-map))]
    (remove nil? (map (fn [entry-uuid] (first (filter #(= (:uuid %) entry-uuid) entries))) limited-uuids))))