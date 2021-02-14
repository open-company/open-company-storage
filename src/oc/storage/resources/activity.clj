(ns oc.storage.resources.activity
  (:require [clojure.set :as clj-set]
            [schema.core :as schema]
            [taoensso.timbre :as timbre]
            [oc.lib.schema :as lib-schema]
            [oc.lib.db.common :as db-common]
            [oc.storage.db.common :as storage-db-common]
            [oc.storage.resources.common :as common]
            [oc.storage.resources.entry :as entry-res]))


(schema/defn ^:always-validate paginated-entries-for-digest
  "
  Given the UUID of the org, an order, one of `:asc` or `:desc`, a start date as an ISO8601 timestamp,
  and a number of results, return the published entries for the org with any interactions.
  "
  [conn org-uuid :- lib-schema/UniqueID order start :- common/SortValue direction limit
   allowed-boards :- (schema/maybe [common/AllowedBoard]) {count? :count unseen :unseen :or {count? false unseen false}}]
  {:pre [(db-common/conn? conn)
         (#{:desc :asc} order)
         (#{:before :after} direction)
         (integer? limit)]}
  (timbre/info "entry-res/pagineted-entries-for-digest")
  (let [index-name (if allowed-boards
                     :status-board-uuid
                     :status-org-uuid)
        index-value (if allowed-boards
                      (map #(vec [:published (:uuid %)]) allowed-boards)
                      [[:published org-uuid]])]
    (time
     (storage-db-common/read-digest-entries conn entry-res/table-name index-name index-value order start direction
                                            limit {:count count? :unseen unseen}))))


(schema/defn ^:always-validate paginated-recently-posted-entries-by-org
  "
  Given the UUID of the org, an order, one of `:asc` or `:desc`, a start date as an ISO8601 timestamp,
  and a number of results, return the published entries for the org with any interactions.
  "
  ([conn org-uuid :- lib-schema/UniqueID order start :- common/SortValue direction limit allowed-boards :- (schema/maybe [common/AllowedBoard])
    follow-data container-last-seen-at {count? :count unseen :unseen container-id :container-id :or {count? false unseen false container-id nil}}]
   {:pre [(db-common/conn? conn)
          (#{:desc :asc} order)
          (#{:before :after} direction)
          (integer? limit)]}
   (timbre/info "entry-res/paginated-recently-posted-entries-by-org")
   (let [index-name (if allowed-boards
                      :status-board-uuid
                      :status-org-uuid)
         allowed-board-uuids (set (map :uuid allowed-boards))
         filtered-board-uuids (cond (:following follow-data)
                                    (clj-set/difference allowed-board-uuids (:unfollow-board-uuids follow-data))
                                    (:unfollowing follow-data)
                                    (clj-set/intersection allowed-board-uuids (:unfollow-board-uuids follow-data))
                                    :else
                                    allowed-board-uuids)
         index-value (if allowed-boards
                       (map #(vec [:published %]) filtered-board-uuids)
                       [[:published org-uuid]])]
     (time
      (storage-db-common/read-paginated-recently-posted-entries conn entry-res/table-name index-name index-value order start direction limit
                                                                common/interaction-table-name entry-res/list-comment-properties allowed-boards
                                                                container-last-seen-at {:count count? :unseen unseen :container-id container-id})))))


(schema/defn ^:always-validate list-entries-for-user-replies
  "List all activities with at least one comment where the user-id has been active part."
  [conn org-uuid :- lib-schema/UniqueID allowed-boards :- (schema/maybe [common/AllowedBoard]) user-id :- lib-schema/UniqueID
   order start :- common/SortValue direction limit container-last-seen-at {count? :count unseen :unseen :or {count? false unseen false}}]
  {:pre [(db-common/conn? conn)
         (#{:desc :asc} order)
         (#{:before :after} direction)
         (integer? limit)]}
  (let [index-name (if allowed-boards
                     :comment-board-uuid-org-uuid
                     :comment-org-uuid)
        index-value (if allowed-boards
                      (map #(vec [true (:uuid %) org-uuid]) allowed-boards)
                      [[true org-uuid]])]
    (time (storage-db-common/read-paginated-replies-entries conn index-name index-value user-id order start
                                                            direction limit container-last-seen-at
                                                            entry-res/list-comment-properties {:count count? :unseen unseen}))))

;; ----- Entry Bookmarks manipulation -----

(schema/defn ^:always-validate list-all-bookmarked-entries
  "Given the UUID of the user, return all the published entries with a bookmark for the given user."
  ([conn org-uuid :- lib-schema/UniqueID user-id :- lib-schema/UniqueID allowed-boards :- (schema/maybe [common/AllowedBoard]) order start :- common/SortValue direction limit]
   (list-all-bookmarked-entries conn org-uuid user-id allowed-boards order start direction limit {:count false}))
  ([conn org-uuid :- lib-schema/UniqueID user-id :- lib-schema/UniqueID allowed-boards :- (schema/maybe [common/AllowedBoard]) order start :- common/SortValue direction limit {count? :count :or {count? false}}]
   {:pre [(db-common/conn? conn)
          (#{:desc :asc} order)
          (#{:before :after} direction)]}
   (timbre/info "entry-res/list-all-bookmarked-entries")
   (let [index-name (if allowed-boards ;; empty array means user has 0 boards access
                      :status-board-uuid-bookmark-user-id-multi
                      :org-uuid-status-bookmark-user-id-map-multi)
         index-value (if allowed-boards
                       (map #(vec [:published (:uuid %) user-id]) allowed-boards)
                       [[:published org-uuid user-id]])]
     (time
      (storage-db-common/read-paginated-bookmarked-entries conn entry-res/table-name index-name index-value order
                                                           start direction limit common/interaction-table-name
                                                           entry-res/list-comment-properties user-id {:count count?})))))


(schema/defn ^:always-validate list-entries-by-org-author

  ([conn org-uuid :- lib-schema/UniqueID author-uuid :- lib-schema/UniqueID order start :- common/SortValue
    direction limit allowed-boards :- (schema/maybe [common/AllowedBoard]) {count? :count :or {count? false}}]
   {:pre [(db-common/conn? conn)
          (#{:desc :asc} order)
          (#{:before :after} direction)
          (integer? limit)]}
   (timbre/info "entry-res/list-entries-by-org-author")
   (let [index-name (if allowed-boards
                      :status-board-uuid-author-id
                      :status-org-uuid-author-id)
         index-value (if allowed-boards
                       (map #(vec [:published (:uuid %) author-uuid]) allowed-boards)
                       [[:published org-uuid author-uuid]])]
     (time
      (storage-db-common/read-paginated-contributions-entries conn entry-res/table-name index-name index-value order start direction
                                                              limit common/interaction-table-name entry-res/list-comment-properties {:count count?})))))

(schema/defn ^:always-validate list-latest-published-entries
  "Retrive the list of the latest posts ordered by publish date."
  ([conn :- lib-schema/Conn org-uuid :- lib-schema/UniqueID allowed-boards :- [common/AllowedBoard] days :- schema/Num]
   (list-latest-published-entries conn org-uuid allowed-boards days {}))
  ([conn :- lib-schema/Conn org-uuid :- lib-schema/UniqueID allowed-boards :- [common/AllowedBoard] days :- schema/Num {count? :count}]
   (timbre/info "list-latest-published-entries")
   (let [index-name (if allowed-boards
                      :status-board-uuid
                      :status-org-uuid)
         index-value (if allowed-boards
                       (map #(vec [:published (:uuid %)]) allowed-boards)
                       [[:published org-uuid]])]
     (time
      (storage-db-common/list-latest-published-entries conn index-name index-value days {:count count?})))))