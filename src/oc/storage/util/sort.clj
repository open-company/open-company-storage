(ns oc.storage.util.sort)

(defn- activity-sort
  "
  Compare function to sort 2 entries and/or activity by their `created-at` or `published-at` order respectively,
  in the order (:asc or :desc) provided.
  "
  [order x y]
  (let [order-flip (if (= order :desc) -1 1)]
    (* order-flip (compare (or (:published-at x) (:created-at x))
                           (or (:published-at y) (:created-at y))))))

(defn sort-activity
  "Given a set of entries, return up to the default limit of them, intermixed and sorted."
  [entries order limit]
  (take limit (sort (partial activity-sort order) entries)))