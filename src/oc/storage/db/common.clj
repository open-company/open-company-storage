(ns oc.storage.db.common
  "CRUD function to retrieve entries from RethinkDB with pagination."
  (:require [clj-time.core :as t]
            [rethinkdb.query :as r]
            [oc.lib.schema :as lib-schema]
            [oc.lib.time :as lib-time]
            [oc.storage.config :as config]
            [oc.lib.db.common :as db-common]))

(defn- date-millis [v]
  (-> v
      (r/to-epoch-time)
      (r/mul 1000)
      (r/round)))

(defn- r-millis [v]
  (date-millis (r/iso8601 v)))

(defn- now-millis []
  (date-millis (r/now)))

(defn read-paginated-contributions-entries
  [conn table-name index-name index-value order start direction limit relation-table-name relation-fields
   {count? :count :or {count? false}}]
  {:pre [(db-common/conn? conn)
         (db-common/s-or-k? table-name)
         (db-common/s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))
         (db-common/s-or-k? relation-table-name)
         (#{:desc :asc} order)
         (or (nil? start)
             (number? start))
         (#{:after :before} direction)
         (integer? limit)
         (sequential? relation-fields)
         (every? db-common/s-or-k? relation-fields)
         (boolean? count?)]}
  (let [order-fn (if (= order :desc) r/desc r/asc)]
    (db-common/with-timeout db-common/default-timeout
      (as-> (r/table table-name) query
        (r/get-all query index-value {:index index-name})
           ;; Merge in last-activity-at, last-seen-at and sort-value
        (r/merge query (r/fn [post-row]
           (let [sort-field (r/default (r/get-field post-row :published-at)
                                       (r/get-field post-row :created-at))
                 sort-value (r-millis sort-field)]
             {;; The real value used for the sort
              :sort-value sort-value})))
           ;; Filter out:
        (r/filter query (r/fn [row]
          (r/or (r/not start)
                (r/and (= direction :before)
                        (r/gt start (r/get-field row :sort-value)))
                (r/and (= direction :after)
                      (r/le start (r/get-field row :sort-value))))))
        (if-not count?
          (r/order-by query (order-fn :sort-value))
          query)
           ;; Apply count if needed
        (if count? (r/count query) query)
        ;; Apply limit
        (if (and (pos? limit)
                 (not count?))
          (r/limit query limit)
          query)
        ;; Merge in all the interactions
        (if-not count?
          (r/merge query (r/fn [post-row]
            {:interactions (-> (r/table relation-table-name)
                              (r/get-all [(r/get-field post-row :uuid)] {:index :resource-uuid})
                              (r/pluck relation-fields)
                              (r/coerce-to :array))}))
          query)
        ;; Run!
        (r/run query conn)
        (db-common/drain-cursor query)))))

(defn read-paginated-bookmarked-entries
  [conn table-name index-name index-value order start direction limit relation-table-name relation-fields user-id
   {count? :count :or {count? false}}]
  {:pre [(db-common/conn? conn)
         (db-common/s-or-k? table-name)
         (db-common/s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))
         (db-common/s-or-k? relation-table-name)
         (#{:desc :asc} order)
         (or (nil? start)
             (number? start))
         (#{:after :before} direction)
         (integer? limit)
         (seq user-id)
         (sequential? relation-fields)
         (every? db-common/s-or-k? relation-fields)
         (boolean? count?)]}
  (let [order-fn (if (= order :desc) r/desc r/asc)]
    (db-common/with-timeout db-common/default-timeout
      (as-> (r/table table-name) query
        (r/get-all query index-value {:index index-name})
           ;; Merge in last-activity-at, last-seen-at and sort-value
        (r/merge query (r/fn [post-row]
          (let [sort-field (-> (r/get-field post-row [:bookmarks])
                              (r/filter {:user-id user-id})
                              (r/nth 0)
                              (r/get-field :bookmarked-at)
                              (r/default (r/get-field post-row :published-at))
                              (r/default (r/get-field post-row :created-at)))
                sort-value-base (r-millis sort-field)
                sort-value sort-value-base]
            {;; The real value used for the sort
            :sort-value sort-value})))
        ;; Filter out:
        (r/filter query (r/fn [row]
          (r/or (r/not start)
                (r/and (= direction :before)
                       (r/gt start (r/get-field row :sort-value)))
                (r/and (= direction :after)
                       (r/le start (r/get-field row :sort-value))))))
        (if-not count?
          (r/order-by query (order-fn :sort-value))
          query)
           ;; Apply count if needed
        (if count? (r/count query) query)
           ;; Apply limit
        (if (and (pos? limit)
                 (not count?))
          (r/limit query limit)
          query)
        ;; Merge in all the interactions
        (if-not count?
          (r/merge query (r/fn [post-row]
            {:interactions (-> (r/table relation-table-name)
                              (r/get-all [(r/get-field post-row :uuid)] {:index :resource-uuid})
                              (r/pluck relation-fields)
                              (r/coerce-to :array))}))
          query)
        ;; Run!
        (r/run query conn)
        (db-common/drain-cursor query)))))

(defn read-paginated-recent-activity-entries
  [conn table-name index-name index-value order start direction limit relation-table-name relation-fields {count? :count :or {count? false}}]
  {:pre [(db-common/conn? conn)
         (db-common/s-or-k? table-name)
         (db-common/s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))
         (db-common/s-or-k? relation-table-name)
         (#{:desc :asc} order)
         (or (nil? start)
             (number? start))
         (#{:after :before} direction)
         (integer? limit)
         (sequential? relation-fields)
         (every? db-common/s-or-k? relation-fields)
         (boolean? count?)]}
  (let [order-fn (if (= order :desc) r/desc r/asc)]
    (db-common/with-timeout db-common/default-timeout
      (as-> (r/table table-name) query
        (r/get-all query index-value {:index index-name})
           ;; Merge in last-activity-at, last-seen-at and sort-value
        (r/merge query (r/fn [post-row]
           (let [last-activity-at (-> (r/table relation-table-name)
                                       (r/get-all [[(r/get-field post-row :uuid) true]] {:index :resource-uuid-comment})
                                       (r/max :created-at)
                                       (r/get-field :created-at)
                                       (r/default (r/get-field post-row :published-at))
                                       (r/default (r/get-field post-row :created-at)))
                 sort-value (r-millis last-activity-at)]
             {;; Date of the last added comment on this entry
              :last-activity-at last-activity-at
              ;; The real value used for the sort
              :sort-value sort-value})))
           ;; Filter out:
        (r/filter query (r/fn [row]
          (r/or (r/not start)
                (r/and (= direction :before)
                       (r/gt start (r/get-field row :sort-value)))
                (r/and (= direction :after)
                       (r/le start (r/get-field row :sort-value))))))
        (if-not count?
          (r/order-by query (order-fn :sort-value))
          query)
           ;; Apply count if needed
        (if count? (r/count query) query)
           ;; Apply limit
        (if (and (pos? limit)
                 (not count?))
          (r/limit query limit)
          query)
        ;; Merge in all the interactions
        (if-not count?
          (r/merge query (r/fn [post-row]
            {:interactions (-> (r/table relation-table-name)
                              (r/get-all [(r/get-field post-row :uuid)] {:index :resource-uuid})
                              (r/pluck relation-fields)
                              (r/coerce-to :array))}))
          query)
        ;; Run!
        (r/run query conn)
        (db-common/drain-cursor query)))))
         
(defn read-paginated-recently-posted-entries
  [conn table-name index-name index-value order start direction limit relation-table-name relation-fields
   allowed-boards container-last-seen-at {count? :count unseen :unseen container-id :container-id :or {count? false unseen false}}]
  {:pre [(db-common/conn? conn)
         (db-common/s-or-k? table-name)
         (db-common/s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))
         (db-common/s-or-k? relation-table-name)
         (#{:desc :asc} order)
         (or (nil? start)
             (number? start))
         (#{:after :before} direction)
         (integer? limit)
         (or (nil? container-last-seen-at)
             (string? container-last-seen-at))
         (sequential? relation-fields)
         (every? db-common/s-or-k? relation-fields)
         (boolean? count?)
         (boolean? unseen)]}
  (let [order-fn (if (= order :desc) r/desc r/asc)
        unseen-cap-ms (* 1000 60 60 24 config/unseen-cap-days)
        pins-sort-pivot-ms (* 1000 60 60 24 config/pins-sort-pivot-days)
        allowed-board-uuids (map :uuid allowed-boards)
        private-board-uuids (map :uuid (filter #(= (:access %) "private") allowed-boards))
        pins-allowed-boards (when container-id
                              (set (conj allowed-board-uuids config/seen-home-container-id)))
        fixed-container-id (when (and pins-allowed-boards
                                      (pins-allowed-boards container-id))
                             container-id)]
    (db-common/with-timeout db-common/default-timeout
      (as-> (r/table table-name) query
        (r/get-all query index-value {:index index-name})
           ;; Merge in last-activity-at, last-seen-at and sort-value
        (r/merge query (r/fn [post-row]
         (let [can-pin-to-container? (r/and (r/default (r/get-field post-row [:pins fixed-container-id]) nil)
                                             (r/or (r/ne container-id config/seen-home-container-id)
                                                   (r/not (r/contains (r/coerce-to private-board-uuids :array)
                                                                     (r/get-field post-row [:board-uuid])))))
               sort-field (r/default (r/get-field post-row :published-at)
                                     (r/get-field post-row :created-at))
               sort-value-base (r-millis sort-field)
               unseen-entry? (r/and (seq container-last-seen-at)
                                     (r/gt (r/get-field post-row :published-at) container-last-seen-at))
               unseen-with-cap? (r/and unseen-entry?
                                       (r/gt sort-value-base
                                             (r/sub (now-millis) unseen-cap-ms)))
               sort-value (r/branch can-pin-to-container?
                                    ;; If the item is pinned and was published (for recently posted) in the cap window
                                    ;; let's add the cap window to the publish timestamp so it will sort before the seen ones
                                    (r/add sort-value-base pins-sort-pivot-ms)
                                    ;; :else
                                    (r/branch unseen-with-cap?
                                              ;; If the item is unseen and was published (for recently posted) or bookmarked
                                              ;; (for bookmarks) or last activity was (for recent activity) in the cap window
                                              ;; let's add the cap window to the publish timestamp so it will sort before the seen ones
                                              (r/add sort-value-base unseen-cap-ms)
                                              ;; :else
                                              sort-value-base))]
           {;; The real value used for the sort
            :sort-value sort-value
            ;; If the entry is unseen
            :unseen unseen-with-cap?})))
        ;; Filter out:
        (r/filter query (r/fn [row]
          (r/and ;; Filter out seen entries if unseen flag is on
                 (r/or (r/not unseen)
                       (r/default (r/get-field row :unseen) false))
                 ;; Limit the posts based on the given time based on the initial sort value
                 ;; without any manipulation to move the unseen or pinned posts at the top
                 (r/or (r/not start)
                       (r/and (= direction :before)
                              (r/gt start (r/get-field row :sort-value)))
                       (r/and (= direction :after)
                               (r/le start (r/get-field row :sort-value)))))))
        (if-not count?
          (r/order-by query (order-fn :sort-value))
          query)
           ;; Apply count if needed
        (if count? (r/count query) query)
           ;; Apply limit
        (if (and (pos? limit)
                 (not count?))
          (r/limit query limit)
          query)
        ;; Merge in all the interactions
        (if-not count?
          (r/merge query (r/fn [post-row]
            {:interactions (-> (r/table relation-table-name)
                               (r/get-all [(r/get-field post-row :uuid)] {:index :resource-uuid})
                               (r/pluck relation-fields)
                               (r/coerce-to :array))}))
          query)
        ;; Run!
        (r/run query conn)
        (db-common/drain-cursor query)))))

(defn read-digest-entries
  [conn table-name index-name index-value order start direction limit {count? :count :or {count? false}}]
  {:pre [(db-common/conn? conn)
         (db-common/s-or-k? table-name)
         (db-common/s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))
         (#{:desc :asc} order)
         (or (nil? start)
             (number? start))
         (#{:after :before} direction)
         (integer? limit)
         (boolean? count?)]}
  (let [order-fn (if (= order :desc) r/desc r/asc)]
    (db-common/with-timeout db-common/default-timeout
      (as-> (r/table table-name) query
        (r/get-all query index-value {:index index-name})
           ;; Merge in last-activity-at, last-seen-at and sort-value
        (r/merge query (r/fn [post-row]
          {;; The real value used for the sort
           :sort-value (r-millis (r/get-field post-row :published-at))}))
           ;; Filter out:
        (r/filter query (r/fn [row]
          (r/or (r/not start)
                (r/and (= direction :before)
                       (r/gt start (r/get-field row :sort-value)))
                (r/and (= direction :after)
                       (r/le start (r/get-field row :sort-value))))))
        (if-not count?
          (r/order-by query (order-fn :sort-value))
          query)
        ;; Apply count if needed
        (if count? (r/count query) query)
           ;; Apply limit
        (if (and (pos? limit)
                 (not count?))
          (r/limit query limit)
          query)
           ;; Run!
        (r/run query conn)
        (db-common/drain-cursor query)))))

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

(defn read-paginated-replies-entries
  "Read all entries with at least one comment the user has access to. Filter out those not activily followed
   by the current user. Sort those with unseen content at the top and sort everything by last activity descendant."
  [conn org-uuid allowed-boards user-id order start direction limit follow-data container-last-seen-at relation-fields {count? :count unseen :unseen :or {count? false unseen false}}]
  {:pre [(db-common/conn? conn)
         (lib-schema/unique-id? org-uuid)
         (or (sequential? allowed-boards)
             (nil? allowed-boards))
         (lib-schema/unique-id? user-id)
         (or (nil? follow-data)
             (map? follow-data))
         (or (nil? container-last-seen-at)
             (string? container-last-seen-at))
         (#{:desc :asc} order)
         (or (nil? start)
             (number? start))
         (#{:after :before} direction)
         (or (zero? limit) ;; means all
             (pos? limit))
         (or (nil? relation-fields)
             (coll? relation-fields))
         (boolean? count?)
         (boolean? unseen)]}
  (let [order-fn (if (= order :desc) r/desc r/asc)
        unseen-cap-ms (- (lib-time/now-ts) (* 1000 60 60 24 config/unseen-cap-days))
        has-seen-at? (seq container-last-seen-at)
        container-seen-ms (when has-seen-at?
                            (lib-time/millis container-last-seen-at))
        index-name (if allowed-boards
                     :comment-board-uuid-org-uuid
                     :comment-org-uuid)
        index-value (if allowed-boards
                      (map #(vec [true (:uuid %) org-uuid]) allowed-boards)
                      [[true org-uuid]])]
    (db-common/with-timeout db-common/default-timeout
      (as-> (r/table "interactions") query
        (r/get-all query index-value {:index index-name})
        (r/group query :resource-uuid)
        (r/max query :created-at)
        (r/ungroup query)
        (r/eq-join query :group  ;; {:left {:group "resource-uuid" :reduction comment-map} :right entry-map}
          (r/table "entries"))
        (r/map query (r/fn [row]
          (let [interaction (r/get-field row [:left :reduction])
                entry (r/get-field row [:right])
                last-activity-at (r/get-field interaction [:created-at])
                published-ms (r-millis (r/get-field entry [:published-at]))
                last-activity-ms (r-millis last-activity-at)
                unseen-entry? (when has-seen-at?
                                (r/gt published-ms container-seen-ms))
                unseen-activity? (r/or unseen-entry?
                                       (r/gt last-activity-ms container-seen-ms))
                unseen-with-cap? (r/and unseen-activity?
                                        (r/gt last-activity-ms unseen-cap-ms))
                sort-value (r/branch unseen-with-cap?
                                     ;; If the item is unseen and was published in the cap window
                                     ;; let's add the cap window to the publish timestamp so it will sort before the seen items
                                     (r/add last-activity-ms unseen-cap-ms)
                                           ;; The timestamp in seconds
                                     last-activity-ms)]
            (r/merge entry
              {;; Date of the last added comment on this thread
               :last-activity-at last-activity-at
               :sort-value sort-value
               :unseen unseen-entry?
               :last-activity-ms last-activity-ms
               }))))
        (r/filter query (r/fn [row]
          (r/and ; (r/gt (r/get-field row [:comments-count]) 0)
                 ;; Filter out entries without unseen comments if unseen flag is on
                 (r/or (r/not unseen)
                      (r/default (r/get-field row :unseen-comments) false))
                 ;; All records after/before the start
                 (r/or (r/not start)
                       (r/and (= direction :before)
                              (r/gt start (r/get-field row :last-activity-ms)))
                       (r/and (= direction :after)
                              (r/le start (r/get-field row :last-activity-ms))))
                 ;; Filter on the user's visibility map:
                 (r/or ;; has :follow true
                       (-> row
                           (r/get-field [:user-visibility (keyword user-id) :follow])
                           (r/default false))
                       ;; has :unfollow false (key actually exists)
                       (-> row
                           (r/get-field [:user-visibility (keyword user-id) :unfollow])
                           (r/default true)
                           (r/not))))))
        ;; Sort
        (if-not count?
          (r/order-by query (order-fn :sort-value))
          query)
        ;; Apply limit
        (if (pos? limit)
          (r/limit query limit)
          query)
        (if-not count?
          (r/merge query (r/fn [post-row]
            {:interactions (-> (r/table "interactions")
                                (r/get-all [(r/get-field post-row :uuid)] {:index :resource-uuid})
                                (r/pluck relation-fields)
                                (r/coerce-to :array))}))
          query)
        ;; Apply count if needed
        (if count? (r/count query) query)
        ;; Run!
        (r/run query conn)
        ;; Drain cursor
        (db-common/drain-cursor query)))))

(defn last-entry-of-board
  [conn board-uuid]
  (as-> (r/table "entries") query
   (r/get-all query [board-uuid] {:index :board-uuid})
   (r/order-by query (r/desc :created-at))
   (r/default (r/nth query 0) {})
   (r/run query conn)))