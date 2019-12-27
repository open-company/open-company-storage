(ns oc.storage.lib.inbox
  "CRUD function to retrieve posts filtered for Inbox from RethinkDB."
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [rethinkdb.query :as r]
            [oc.lib.schema :as lib-schema]
            [oc.lib.time :as lib-time]
            [oc.lib.db.common :as db-common]
            [oc.storage.config :as config]
            [oc.lib.time :as oc-time]))

(defn read-all-inbox-for-user
 [conn table-name index-name index-value order start direction relation-table-name allowed-boards user-id
  relation-fields {:keys [count] :or {count false}}]
 {:pre [(db-common/conn? conn)
        (db-common/s-or-k? table-name)
        (db-common/s-or-k? index-name)
        (or (string? index-value) (sequential? index-value))
        (db-common/s-or-k? relation-table-name)
        (#{:desc :asc} order)
        (not (nil? start))
        (#{:before :after} direction)
        (string? user-id)
        (sequential? relation-fields)
        (every? db-common/s-or-k? relation-fields)]}
  (let [index-values (if (sequential? index-value) index-value [index-value])
        order-fn (if (= order :desc) r/desc r/asc)
        filter-fn (if (= direction :before) r/gt r/lt)
        minimum-date-timestamp (f/unparse lib-time/timestamp-format (t/minus (t/now) (t/days config/inbox-days-limit)))]
    (db-common/with-timeout db-common/default-timeout
      (as-> (r/table table-name) query
            (r/get-all query index-values {:index index-name})
            ;; Merge in a last-activity-at date for each post (last comment created-at, fallback to published-at)
            (r/merge query (r/fn [res]
              {:last-activity-at (-> (r/table relation-table-name)
                                     (r/get-all [(r/get-field res :uuid)] {:index :resource-uuid})
                                     (r/coerce-to :array)
                                     (r/reduce (r/fn [left right]
                                       (if (r/ge (r/get-field left "created-at") (r/get-field right "created-at"))
                                         left
                                         right)))
                                     (r/default {"created-at" (r/get-field res "published-at")})
                                     (r/do (r/fn [res-int]
                                       (r/get-field res-int "created-at"))))}))
            ;; Filter out:
            (r/filter query (r/fn [res]
              (r/and ;; All records in boards the user has no access
                     (r/contains allowed-boards (r/get-field res :board-uuid))
                     (r/gt (r/get-field res :last-activity-at) minimum-date-timestamp)
                     ;; All records with follow true
                     (r/get-field (r/get-field (r/get-field res :user-visibility) user-id) :follow)
                     ;; All records that have a dismiss-at later or equal than the last activity
                     (r/gt (r/get-field res :last-activity-at)
                           (r/get-field (r/get-field (r/get-field res :user-visibility) user-id) :dismiss-at)))))
            ;; Merge in all the interactions
            (if-not count
              (r/merge query (r/fn [res]
                {:interactions (-> (r/table relation-table-name)
                                   (r/get-all [(r/get-field res :uuid)] {:index :resource-uuid})
                                   (r/pluck relation-fields)
                                   (r/coerce-to :array))}))
              query)
            ;; Apply a filter on the published-at date 
            (r/filter query (r/fn [row]
                                  (filter-fn start (r/get-field row :published-at))))

            (if-not count (r/order-by query (order-fn :published-at)) query)
            ;; Apply count if needed
            (if count (r/count query) query)
            ;; Run!
            (r/run query conn)
            (if (= (type query) rethinkdb.net.Cursor)
              (seq query)
              query)))))








