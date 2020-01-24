(ns oc.storage.db.common
  "CRUD function to retrieve entries from RethinkDB with pagination."
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [rethinkdb.query :as r]
            [oc.lib.time :as lib-time]
            [oc.storage.config :as config]
            [oc.lib.db.common :as db-common]))

(defn read-paginated-entries
 [conn table-name index-name index-value order start direction limit sort-type relation-table-name allowed-boards
  relation-fields {:keys [count] :or {count false}}]
 {:pre [(db-common/conn? conn)
        (db-common/s-or-k? table-name)
        (db-common/s-or-k? index-name)
        (or (string? index-value) (sequential? index-value))
        (db-common/s-or-k? relation-table-name)
        (#{:desc :asc} order)
        (not (nil? start))
        (#{:after :before} direction)
        (integer? limit)
        (#{:recent-activity :recently-posted} sort-type)
        (sequential? relation-fields)
        (every? db-common/s-or-k? relation-fields)]}
  (let [index-values (if (sequential? index-value) index-value [index-value])
        order-fn (if (= order :desc) r/desc r/asc)]
    (db-common/with-timeout db-common/default-timeout
      (as-> (r/table table-name) query
            (r/get-all query index-values {:index index-name})
            ;; Merge in a last-activity-at date for each post, which is the
            ;; last comment created-at, with fallback to published-at or created-at for published entries
            ;; the entry created-at in all the other cases.
            (r/merge query (r/fn [post-row]
              (if (= sort-type :recent-activity)
                {:last-activity-at (-> (r/table relation-table-name)
                                       (r/get-all [(r/get-field post-row :uuid)] {:index :resource-uuid})
                                       (r/filter (r/fn [interaction-row]
                                        ;; Filter out reactions
                                        (r/ge (r/get-field interaction-row :body) "")))
                                       (r/order-by (r/desc :created-at))
                                       (r/coerce-to :array)
                                       (r/nth 0)
                                       (r/default (r/fn [_err]
                                        {:created-at (r/default
                                                       (r/get-field post-row :published-at)
                                                       (r/get-field post-row :created-at))}))
                                       (r/do (r/fn [activity-row]
                                         (r/get-field activity-row :created-at))))}
                {:last-activity-at (r/default
                                    (r/get-field post-row :published-at)
                                    (r/get-field post-row :created-at))})))
            (if (sequential? allowed-boards)
              ;; Filter out:
              (r/filter query (r/fn [post-row]
                (r/and ;; All records in boards the user has no access
                       (r/contains allowed-boards (r/get-field post-row :board-uuid))
                       ;; All records after/before the start
                       (if (= direction :before)
                         (r/gt start (r/get-field post-row :last-activity-at))
                         (r/le start (r/get-field post-row :last-activity-at))))))
              ;; Filter out only based on the date
              (r/filter query (r/fn [post-row]
                (if (= direction :before)
                  (r/gt start (r/get-field post-row :last-activity-at))
                  (r/le start (r/get-field post-row :last-activity-at))))))
            ;; Merge in all the interactions
            (if-not count
              (r/merge query (r/fn [post-row]
                {:interactions (-> (r/table relation-table-name)
                                   (r/get-all [(r/get-field post-row :uuid)] {:index :resource-uuid})
                                   (r/pluck relation-fields)
                                   (r/coerce-to :array))}))
              query)
            (if-not count (r/order-by query (order-fn :last-activity-at)) query)
            ;; Apply count if needed
            (if count (r/count query) query)
            ;; Apply limit
            (if (and (pos? limit)
                     (not count))
              (r/limit query limit)
              query)
            ;; Run!
            (r/run query conn)
            (if (= (type query) rethinkdb.net.Cursor)
              (seq query)
              query)))))

(defn read-all-inbox-for-user
 [conn table-name index-name index-value order start limit relation-table-name allowed-boards user-id
  relation-fields {:keys [count] :or {count false}}]
 {:pre [(db-common/conn? conn)
        (db-common/s-or-k? table-name)
        (db-common/s-or-k? index-name)
        (or (string? index-value) (sequential? index-value))
        (db-common/s-or-k? relation-table-name)
        (#{:desc :asc} order)
        (not (nil? start))
        (integer? limit)
        (string? user-id)
        (sequential? relation-fields)
        (every? db-common/s-or-k? relation-fields)]}
  (let [index-values (if (sequential? index-value) index-value [index-value])
        order-fn (if (= order :desc) r/desc r/asc)
        minimum-date-timestamp (f/unparse lib-time/timestamp-format (t/minus (t/now) (t/days config/unread-days-limit)))]
    (db-common/with-timeout db-common/default-timeout
      (as-> (r/table table-name) query
            (r/get-all query index-values {:index index-name})
            ;; Merge in a last-activity-at date for each post (last comment created-at, fallback to published-at)
            (r/merge query (r/fn [post-row]
              {:last-activity-at (-> (r/table relation-table-name)
                                     (r/get-all [(r/get-field post-row :uuid)] {:index :resource-uuid})
                                     (r/filter (r/fn [interaction-row]
                                      ;; Filter out reactions and comments from the current user
                                      (r/and
                                       (r/ge (r/get-field interaction-row :body) "")
                                       (r/ne (r/get-field (r/get-field interaction-row :author) :user-id) user-id))))
                                     (r/order-by (r/desc :created-at))
                                     (r/coerce-to :array)
                                     (r/nth 0)
                                     (r/default {:created-at (r/default
                                                               (r/get-field post-row :published-at)
                                                               (r/get-field post-row :created-at))})
                                     (r/do (r/fn [activity-row]
                                       (r/get-field activity-row :created-at))))}))
            ;; Filter out:
            (r/filter query (r/fn [post-row]
              (r/and ;; All records in boards the user has no access
                     (r/contains allowed-boards (r/get-field post-row :board-uuid))
                     ;; Leave in only posts whose last activity is within a certain amount of time
                     (r/gt (r/get-field post-row :last-activity-at) minimum-date-timestamp)
                     ;; All records with follow true
                     (r/not (r/default (r/get-field (r/get-field (r/get-field post-row :user-visibility) user-id) :unfollow) false))
                     ;; All records that have a dismiss-at later or equal than the last activity
                     (r/gt (r/get-field post-row :last-activity-at)
                           (r/default (r/get-field (r/get-field (r/get-field post-row :user-visibility) user-id) :dismiss-at) "")))))
            ;; Merge in all the interactions
            (if-not count
              (r/merge query (r/fn [post-row]
                {:interactions (-> (r/table relation-table-name)
                                   (r/get-all [(r/get-field post-row :uuid)] {:index :resource-uuid})
                                   (r/pluck relation-fields)
                                   (r/coerce-to :array))}))
              query)
            ;; Apply a filter on the last-activity-at date 
            (r/filter query (r/fn [row]
             (r/gt start (r/get-field row :last-activity-at))))
            ;; Sort records when not counting
            (if-not count (r/order-by query (order-fn :last-activity-at)) query)
            ;; Apply count if needed
            (if count (r/count query) query)
            ;; Apply limit
            (if (and (pos? limit)
                     (not count))
              (r/limit query limit)
              query)
            ;; Run!
            (r/run query conn)
            (if (= (type query) rethinkdb.net.Cursor)
              (seq query)
              query)))))