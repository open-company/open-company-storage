(ns oc.storage.lib.sort)

(defn- max-comment-timestamp
  "Return a map {entry-uuid most-recent-comment-timestap} excluding the user authored comments"
  [user-id entry comments]
  (let [sorted-comments (sort-by :created-at comments)]
    (when (seq sorted-comments)
      (hash-map (:uuid entry) (-> sorted-comments last :created-at)))))

(defn- new-comments-count [entry user-id seen-at]
  (let [all-comments (filterv :body (:interactions entry))
        filtered-comments (filterv #(not= (-> % :author :user-id) user-id) all-comments)]
    (if seen-at
      (count (filter #(> (:created-at %) seen-at) filtered-comments))
      (count filtered-comments))))

(defn entry-new-at [user-id entry filter-comments?]
  (let [all-comments (filterv :body (:interactions entry))
        filtered-comments (if filter-comments?
                            (filterv #(not= (-> % :author :user-id) user-id) all-comments)
                            all-comments)]
    (max-comment-timestamp user-id entry filtered-comments)))

(defn sort-activity
  "Given a set of entries, return up to the default limit of them, intermixed and sorted."
  [entries sorting start order limit user-id user-seens]
  (let [;; Get all the posts published at
        all-published-at (apply merge (map #(hash-map (:uuid %) (:published-at %)) entries))
        sorting-by-recent-activity? (= sorting :recent-activity)
        ;; if sorting by recent activity
        all-comments (if sorting-by-recent-activity?
                     ;; get the last comment timestamp for those that have one
                       (apply merge (remove nil? (map #(entry-new-at user-id % false) entries)))
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
                                                    (first (vals (entry-new-at user-id % true))))
                                              entries))
        ;; Add the new-at field used in the client to show the NEW comment badge and
        ;; the new-comments-count field user in the client
        return-entries (map (fn [entry-uuid]
                              (let [entry-seen (some #(when (= (:item-id %) entry-uuid) %) user-seens)
                                    entry-data (first (filter #(= (:uuid %) entry-uuid) entries))]
                                (-> entry-data
                                  (assoc :new-at (last-comment-timestamps entry-uuid))
                                  (assoc :new-comments-count (new-comments-count user-id entry-data entry-seen)))))
                        limited-uuids)]
    ;; Clean out empty results
    (remove nil? return-entries)))

(defn sort-draft-board-entries [drafts-board-entries]
  (reverse (sort-by :updated-at drafts-board-entries)))