(ns oc.storage.db.common
  "CRUD function to retrieve entries from RethinkDB with pagination."
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [rethinkdb.query :as r]
            [oc.lib.time :as lib-time]
            [oc.storage.config :as config]
            [oc.lib.db.common :as db-common]))

(defn read-paginated-entries
 ([conn table-name index-name index-value order start direction limit sort-type relation-table-name allowed-boards
   relation-fields {:keys [count] :or {count false}}]
 (read-paginated-entries conn table-name index-name index-value order start direction limit sort-type relation-table-name allowed-boards
  nil relation-fields nil {:count count}))

 ([conn table-name index-name index-value order start direction limit sort-type relation-table-name allowed-boards
   relation-fields user-id {:keys [count] :or {count false}}]
  (read-paginated-entries conn table-name index-name index-value order start direction limit sort-type relation-table-name allowed-boards
  nil relation-fields user-id {:count count}))

 ([conn table-name index-name index-value order start direction limit sort-type relation-table-name allowed-boards
  follow-data relation-fields user-id {:keys [count] :or {count false}}]
 {:pre [(db-common/conn? conn)
        (db-common/s-or-k? table-name)
        (db-common/s-or-k? index-name)
        (or (string? index-value) (sequential? index-value))
        (db-common/s-or-k? relation-table-name)
        (#{:desc :asc} order)
        (not (nil? start))
        (#{:after :before} direction)
        (integer? limit)
        (or (#{:recent-activity :recently-posted} sort-type)
            (and (= sort-type :bookmarked-at)
                 (seq user-id)))
        (sequential? relation-fields)
        (every? db-common/s-or-k? relation-fields)]}
  (let [index-values (if (sequential? index-value) index-value [index-value])
        order-fn (if (= order :desc) r/desc r/asc)]
    (db-common/with-timeout db-common/default-timeout
      (as-> (r/table table-name) query
            (r/get-all query index-values {:index index-name})
            (r/filter query (r/fn [post-row]
              (r/and ;; Filter on allowed-boards if necessary (if not ISequential means no filter needed)
                     (r/or (not (sequential? allowed-boards))
                           (r/contains allowed-boards (r/get-field post-row :board-uuid)))
                     ;; and filter on follow data:
                     (r/or ;; no filter if it's nil
                           (not (map? follow-data))
                           ;; filter on followed authors and on not unfollowed boards
                           (r/and (:following follow-data)
                                  (r/or (r/contains (vec (:follow-publisher-uuids follow-data)) (r/get-field post-row [:publisher :user-id]))
                                        (r/eq (:user-id follow-data) (r/get-field post-row [:publisher :user-id]))
                                        (r/not (r/contains (vec (:unfollow-board-uuids follow-data)) (r/get-field post-row :board-uuid)))))
                           ;; filter on not followed authors and on unfollowed boards
                           (r/and (:unfollowing follow-data)
                                  (r/not (r/contains (vec (:follow-publisher-uuids follow-data)) (r/get-field post-row [:publisher :user-id])))
                                  (r/ne (:user-id follow-data) (r/get-field post-row [:publisher :user-id]))
                                  (r/contains (vec (:unfollow-board-uuids follow-data)) (r/get-field post-row :board-uuid)))))))
            ;; Merge in a last-activity-at date for each post, which is the
            ;; last comment created-at, with fallback to published-at or created-at for published entries
            ;; the entry created-at in all the other cases.
            (r/merge query (r/fn [post-row]
              (cond
                (= sort-type :recent-activity)
                {:last-activity-at (-> (r/table relation-table-name)
                                       (r/get-all [[(r/get-field post-row :uuid) true]] {:index :resource-uuid-comment})
                                       (r/max :created-at)
                                       (r/get-field :created-at)
                                       (r/default (r/get-field post-row :published-at))
                                       (r/default (r/get-field post-row :created-at)))}
                (= sort-type :bookmarked-at)
                {:last-activity-at (r/default
                                    (-> (r/get-field post-row [:bookmarks])
                                        (r/filter {:user-id user-id})
                                        (r/nth 0)
                                        (r/get-field :bookmarked-at))
                                    (r/get-field post-row :published-at))}
                :else
                {:last-activity-at (r/default
                                    (r/get-field post-row :published-at)
                                    (r/get-field post-row :created-at))})))
            ;; Filter out:
            (r/filter query (r/fn [post-row]
              ;; All records after/before the start
              (if (= direction :before)
                (r/gt start (r/get-field post-row :last-activity-at))
                (r/le start (r/get-field post-row :last-activity-at)))))
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
              query))))))

(defn read-all-inbox-for-user

 ([conn table-name index-name index-value order start limit relation-table-name allowed-boards user-id
   relation-fields {:keys [count] :or {count false}}]
  (read-all-inbox-for-user conn table-name index-name index-value order start limit relation-table-name allowed-boards nil
   user-id relation-fields {:count count}))

 ([conn table-name index-name index-value order start limit relation-table-name allowed-boards follow-data
   user-id relation-fields {:keys [count] :or {count false}}]
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
            ;; Filter out:
            (r/filter query (r/fn [post-row]
              (r/and ;; All records in boards the user has no access
                     (r/or (not (sequential? allowed-boards))
                           (r/contains allowed-boards (r/get-field post-row :board-uuid)))
                     ;; that have unfollow (or :unfollow is not specified)
                     (r/not (r/default (r/get-field post-row [:user-visibility user-id :unfollow]) false))

                     (r/or ;; No follow data are passed
                           (not (map? follow-data))
                           ;; or we are requesting the unfollowed posts
                           (r/and (:unfollowing follow-data)
                                  (r/not (r/contains (vec (:follow-publisher-uuids follow-data)) (r/get-field post-row [:publisher :user-id])))
                                  (r/ne (:user-id follow-data) (r/get-field post-row [:publisher :user-id]))
                                  (r/contains (vec (:unfollow-board-uuids follow-data)) (r/get-field post-row :board-uuid)))
                           (r/and (:following follow-data)
                                  (r/or (r/contains (vec (:follow-publisher-uuids follow-data)) (r/get-field post-row [:publisher :user-id]))
                                        (r/eq (:user-id follow-data) (r/get-field post-row [:publisher :user-id]))
                                        (r/not (r/contains (vec (:unfollow-board-uuids follow-data)) (r/get-field post-row :board-uuid)))))))))
            ;; Merge in a last-activity-at date for each post (last comment created-at, fallback to published-at)
            (r/merge query (r/fn [post-row]
              {:last-activity-at (-> (r/table relation-table-name)
                                     (r/get-all [[(r/get-field post-row :uuid) true]] {:index :resource-uuid-comment})
                                     (r/filter (r/fn [interaction-row]
                                       (r/ne (r/get-field interaction-row [:author :user-id]) user-id)))
                                     (r/max :created-at)
                                     (r/get-field [:created-at])
                                     (r/default (r/get-field post-row :published-at))
                                     (r/default (r/get-field post-row :created-at)))}))
            ;; Filter out:
            (r/filter query (r/fn [post-row]
              (r/and ;; Leave in only posts whose last activity is within a certain amount of time
                     (r/gt (r/get-field post-row :last-activity-at) minimum-date-timestamp)
                     ;; All records that have a dismiss-at later or equal than the last activity
                     (r/gt (r/get-field post-row :last-activity-at)
                           (r/default (r/get-field post-row [:user-visibility user-id :dismiss-at]) "")))))
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
              query))))))

(defn update-poll-vote
  "
  Atomic update of poll vote to avoid race conditions while multiple
  users are voting together.
  `add-vote?` can be true if the user is casting his vote or false if he's
  removing it.
  "
  [conn table-name entry-uuid poll-uuid reply-id user-id add-vote?]
  {:pre [(db-common/conn? conn)
         (db-common/s-or-k? table-name)
         (boolean? add-vote?)]}
  (let [ts (db-common/current-timestamp)
        set-operation (if add-vote? r/set-insert r/set-difference)
        user-id-value (if add-vote? user-id [user-id])
        update (db-common/with-timeout db-common/default-timeout
                  (-> (r/table table-name)
                      (r/get entry-uuid)
                      (r/update (r/fn [entry]
                       {:polls {poll-uuid {:replies
                        (-> entry
                         (r/get-field [:polls poll-uuid :replies])
                         (r/values)
                         (r/map (r/fn [reply-data]
                          (r/branch
                           (r/eq (r/get-field reply-data [:reply-id]) reply-id)
                           (r/object (r/get-field reply-data :reply-id)
                            (r/merge reply-data
                             {:votes (-> reply-data
                                      (r/get-field [:votes])
                                      (r/default [])
                                      (set-operation user-id-value))}))
                           (r/object (r/get-field reply-data :reply-id)
                            (r/merge reply-data
                             {:votes (-> reply-data
                                      (r/get-field [:votes])
                                      (r/default [])
                                      (r/set-difference [user-id]))})))))
                         (r/reduce (r/fn [a b]
                          (r/merge a b))))}}}))
                      (r/run conn)))]
    (if (or (= 1 (:replaced update)) (= 1 (:unchanged update)))
      (db-common/read-resource conn table-name entry-uuid)
      (throw (RuntimeException. (str "RethinkDB update failure: " update))))))
