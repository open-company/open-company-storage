(ns oc.storage.db.common
  "CRUD function to retrieve entries from RethinkDB with pagination."
  (:require [clj-time.core :as t]
            [rethinkdb.query :as r]
            [cuerdas.core :as string]
            [oc.lib.schema :as lib-schema]
            [oc.storage.config :as config]
            [oc.lib.time :as lib-time]
            [oc.lib.db.common :as db-common]))

(def min-iso8601 (lib-time/to-iso (lib-time/from-millis 0)))

(defn- direction-filter [direction start row-field]
  (if (= direction :before)
    (r/gt start row-field)
    (r/le start row-field)))

(defn- unseen-filter [container-last-seen-at row-field]
  (r/gt row-field container-last-seen-at))

(defn- user-visibility-filter [user-id entry-row]
  (r/or ;; has :follow true
        (-> (r/get-field entry-row [:user-visibility (keyword user-id) :follow])
            (r/default false))
        (-> (r/get-field entry-row [:user-visibility (keyword user-id) :unfollow])
            (r/default true)
            (r/not))))

(defn row-pinned-at [allowed-board-uuids row container-id]
  (r/branch (r/contains allowed-board-uuids (r/get-field row :board-uuid))
            (-> (r/get-field row [:pins (keyword container-id) :pinned-at])
                (r/default min-iso8601))
            min-iso8601))

(defn row-order-val [allowed-board-uuids row container-id]
  (if container-id
    (r/add (row-pinned-at allowed-board-uuids row container-id)
           (r/get-field row :published-at))
    (r/get-field row :published-at)))

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
             (string? start))
         (#{:after :before} direction)
         (integer? limit)
         (sequential? relation-fields)
         (every? db-common/s-or-k? relation-fields)
         (boolean? count?)]}
  (let [order-fn (if (= order :desc) r/desc r/asc)]
    (db-common/with-timeout db-common/default-timeout
      (as-> (r/table table-name) query
        (r/get-all query index-value {:index index-name})
           ;; Filter out:
        (if-not (string/blank? start)
          (r/filter query (r/fn [row]
            ;; All records after/before the start
            (direction-filter direction start (r/get-field row :published-at))))
          query)
        (if-not count?
          (r/order-by query (order-fn :published-at))
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
                              (r/coerce-to :array))
             :sort-value (r/get-field post-row :published-at)}))
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
             (string? start))
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
        (r/merge query (r/fn [row]
          {:sort-value (-> (r/get-field row :bookmarks)
                           (r/filter {:user-id user-id})
                           (r/nth 0)
                           (r/get-field :bookmarked-at)
                           (r/default nil))}))
        ;; Filter out:
        (if-not (string/blank? start)
          (r/filter query (r/fn [row]
            ;; All records after/before the start
            (direction-filter direction start (r/get-field row :sort-value))))
          query)
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
  [conn table-name index-name index-value order start direction limit relation-table-name relation-fields allowed-boards
   container-last-seen-at {count? :count unseen :unseen container-id :container-id :or {count? false unseen false}}]
  {:pre [(db-common/conn? conn)
         (db-common/s-or-k? table-name)
         (db-common/s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))
         (db-common/s-or-k? relation-table-name)
         (#{:desc :asc} order)
         (or (nil? start)
             (string? start))
         (#{:after :before} direction)
         (integer? limit)
         (or (nil? container-last-seen-at)
             (string? container-last-seen-at))
         (sequential? relation-fields)
         (every? db-common/s-or-k? relation-fields)
         (boolean? count?)
         (boolean? unseen)]}
  (let [order-fn (if (= order :desc) r/desc r/asc)
        allowed-pins-boards (if (= config/seen-home-container-id container-id)
                              (set
                               (map :uuid
                                    (filter #(not= (:access %) "private") allowed-boards)))
                              (set (map :uuid allowed-boards)))
        dir-filter (when start
                     #(direction-filter direction start (r/get-field % :sort-value)))
        uns-filter (when (and unseen container-last-seen-at)
                      #(unseen-filter container-last-seen-at (r/get-field % :sort-value)))
        filter? (or dir-filter uns-filter)]
    (db-common/with-timeout db-common/default-timeout
      (as-> (r/table table-name) query
        ;; Filter out non allowed boards
        (r/get-all query index-value {:index index-name})
        (r/merge query (r/fn [row]
          {:sort-value (row-order-val allowed-pins-boards row container-id)}))
        (if filter?
          (r/filter query (r/fn [row]
            (cond (and dir-filter uns-filter)
                  (r/and (dir-filter row) (uns-filter row))
                  dir-filter
                  (dir-filter row)
                  :else
                  (uns-filter row))))
          query)
        ;; Order by home pinned-at/published-at
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
             (string? start))
         (#{:after :before} direction)
         (integer? limit)
         (boolean? count?)]}
  (let [order-fn (if (= order :desc) r/desc r/asc)]
    (db-common/with-timeout db-common/default-timeout
      (as-> (r/table table-name) query
        (r/get-all query index-value {:index index-name})
        ;; Filter out:
        (if-not (string/blank? start)
          (r/filter query (r/fn [row]
            (direction-filter direction start (r/get-field row :published-at))))
          query)
        (if-not count?
          (r/order-by query (order-fn :published-at))
          query)
        ;; Apply count if needed
        (if count? (r/count query) query)
           ;; Apply limit
        (if (and (pos? limit)
                 (not count?))
          (r/limit query limit)
          query)
        (if-not count?
          (r/merge query (r/fn [row]
            {:sort-value (r/get-field row :published-at)}))
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
  [conn index-name index-value user-id order start direction limit container-last-seen-at
   relation-fields {count? :count unseen :unseen :or {count? false unseen false}}]
  {:pre [(db-common/conn? conn)
         (lib-schema/unique-id? user-id)
         (or (nil? container-last-seen-at)
             (string? container-last-seen-at))
         (#{:desc :asc} order)
         (or (nil? start)
             (string? start))
         (#{:after :before} direction)
         (or (zero? limit) ;; means all
             (pos? limit))
         (or (nil? relation-fields)
             (coll? relation-fields))
         (boolean? count?)
         (boolean? unseen)]}
  (let [order-fn (if (= order :desc) r/desc r/asc)
        dir-filter (when start
                     #(direction-filter direction start (r/get-field % [:reduction :created-at])))
        uns-filter (when (and unseen container-last-seen-at)
                      #(unseen-filter container-last-seen-at (r/get-field % [:reduction :created-at])))
        filter? (or dir-filter uns-filter)]
    (db-common/with-timeout db-common/default-timeout
      (as-> (r/table :interactions) query
        (r/get-all query index-value {:index index-name})
        (r/group query :resource-uuid)
        (r/max query :created-at)
        (r/ungroup query)
        (if filter?
          (r/filter query (r/fn [row]
            ;; All records after/before the start
            (cond (and dir-filter uns-filter)
                  (r/and (dir-filter row)
                         (uns-filter row))
                  dir-filter
                  (dir-filter row)
                  uns-filter
                  (uns-filter row))))
          query)
        ;; Join with the relative entry
        (r/eq-join query :group  ;; {:left {:group "resource-uuid" :reduction comment-map} :right entry-map}
          (r/table :entries))
        ;; Filter out items the user is not following
        (r/filter query (r/fn [row]
          ;; Filter on the user's visibility map:
          (user-visibility-filter user-id (r/get-field row :right))))
        ;; Sort
        (if-not count?
          (r/order-by query (order-fn (r/fn [row] (r/get-field row [:left :reduction :created-at]))))
          query)
        ;; Apply limit
        (if (pos? limit)
          (r/limit query limit)
          query)
        (if-not count?
          (r/map query (r/fn [row]
            (let [entry (r/get-field row :right)
                  entry-uuid (r/get-field entry :uuid)
                  last-activity-at (r/get-field row [:left :reduction :created-at])]
              (r/merge entry
                      {;; Date of the last added comment on this thread
                       :last-activity-at last-activity-at
                       ;; Add sort-value for pagination
                       :sort-value last-activity-at
                       ;; Add all the related interactions
                       :interactions (-> (r/table :interactions)
                                         (r/get-all [entry-uuid] {:index :resource-uuid})
                                         (r/pluck relation-fields)
                                         (r/coerce-to :array))}))))
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

(defn list-latest-published-entries
  [conn org-uuid allowed-boards days]
  (let [start-date (t/minus (t/date-midnight (t/year (t/today)) (t/month (t/today)) (t/day (t/today))) (t/days days))
        allowed-board-uuids (map :uuid allowed-boards)]
    (db-common/with-timeout db-common/default-timeout
      (as-> (r/table "entries") query
       (r/get-all query [[:published org-uuid]] {:index :status-org-uuid})
       ;; Make an initial filter to select only posts the user has access to
       (r/filter query (r/fn [row]
         (r/and (r/contains allowed-board-uuids (r/get-field row :board-uuid))
                (r/ge (r/to-epoch-time (r/iso8601 (r/get-field row [:published-at])))
                      (lib-time/epoch start-date)))))
       (r/pluck query [:uuid :publisher :published-at :headline])
       (r/order-by query (r/desc :published-at))
       (r/run query conn)
       ;; Drain cursor
       (if (= (type query) rethinkdb.net.Cursor)
         (seq query)
         query)))))