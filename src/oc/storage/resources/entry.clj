(ns oc.storage.resources.entry
  (:require [clojure.walk :refer (keywordize-keys)]
            [if-let.core :refer (if-let* when-let*)]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.db.common :as db-common]
            [oc.lib.slugify :as slugify]
            [oc.storage.config :as config]
            [oc.storage.resources.common :as common]
            [oc.storage.resources.board :as board-res]))

;; ----- RethinkDB metadata -----

(def table-name common/entry-table-name)
(def primary-key :uuid)

;; ----- Metadata -----

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{:topic-slug})

;; ----- Utility functions -----

(defn clean
  "Remove any reserved properties from the entry."
  [entry]
  (apply dissoc (common/clean entry) reserved-properties))

(defn timestamp-attachments
  "Add a `:created-at` timestamp with the specified value to any attachment that's missing it."
  ([attachments] (timestamp-attachments attachments (db-common/current-timestamp)))
  
  ([attachments timestamp]
  (map #(if (:created-at %) % (assoc % :created-at timestamp)) attachments)))

;; ----- Entry CRUD -----

(schema/defn ^:always-validate ->entry :- common/Entry
  "
  Take a board UUID, a minimal map describing an Entry, and a user (as the author) and
  'fill the blanks' with any missing properties.

  Throws an exception if the board specified in the entry can't be found.
  "
  [conn board-uuid :- lib-schema/UniqueID entry-props user :- lib-schema/User]
  {:pre [(db-common/conn? conn)
         (map? entry-props)]}
  (if-let [board (board-res/get-board conn board-uuid)]
    (let [topic-name (:topic-name entry-props)
          topic-slug (when topic-name (slugify/slugify topic-name))
          ts (db-common/current-timestamp)]
      (-> entry-props
          keywordize-keys
          clean
          (assoc :uuid (db-common/unique-id))
          (assoc :topic-slug topic-slug)
          (update :topic-name #(or % nil))
          (update :headline #(or % ""))
          (update :body #(or % ""))
          (update :attachments #(timestamp-attachments % ts))
          (assoc :org-uuid (:org-uuid board))
          (assoc :board-uuid board-uuid)
          (assoc :author [(assoc (lib-schema/author-for-user user) :updated-at ts)])
          (assoc :created-at ts)
          (assoc :updated-at ts)))
    (throw (ex-info "Invalid board uuid." {:board-uuid board-uuid})))) ; no board

(schema/defn ^:always-validate create-entry! :- (schema/maybe common/Entry)
  "
  Create an entry for the board. Returns the newly created entry.

  Throws a runtime exception if the provided entry doesn't conform to the
  common/Entry schema. Throws an exception if the board specified in the entry can't be found.
  "
  ([conn entry :- common/Entry] (create-entry! conn entry (db-common/current-timestamp)))

  ([conn entry :- common/Entry ts :- lib-schema/ISO8601]
  {:pre [(db-common/conn? conn)]}
  (if-let* [board-uuid (:board-uuid entry)
            board (board-res/get-board conn board-uuid)]
    (let [author (assoc (first (:author entry)) :updated-at ts)] ; update initial author timestamp
      (db-common/create-resource conn table-name (assoc entry :author [author]) ts)) ; create the entry
    (throw (ex-info "Invalid board uuid." {:board-uuid (:board-uuid entry)}))))) ; no board

(schema/defn ^:always-validate get-entry :- (schema/maybe common/Entry)
  "
  Given the UUID of the entry, retrieve the entry, or return nil if it doesn't exist.

  Or given the UUID of the org, board, entry, retrieve the entry, or return nil if it doesn't exist. This variant 
  is used to confirm that the entry belongs to the specified org and board.
  "
  ([conn uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/read-resource conn table-name uuid))

  ([conn org-uuid :- lib-schema/UniqueID board-uuid :- lib-schema/UniqueID uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (first (db-common/read-resources conn table-name :uuid-board-uuid-org-uuid [[uuid board-uuid org-uuid]]))))

(schema/defn ^:always-validate update-entry! :- (schema/maybe common/Entry)
  "
  Given the UUID of the entry, an updated entry property map, and a user (as the author), update the entry and
  return the updated entry on success.

  Throws an exception if the merge of the prior entry and the updated entry property map doesn't conform
  to the common/Entry schema.
  "
  [conn uuid :- lib-schema/UniqueID entry user :- lib-schema/User]
  {:pre [(db-common/conn? conn)         
         (map? entry)]}
  (if-let [original-entry (get-entry conn uuid)]
    (let [authors (:author original-entry)
          new-topic-name (:topic-name entry)
          topic-name (when-not (clojure.string/blank? new-topic-name) new-topic-name)
          topic-slug (when topic-name (slugify/slugify topic-name))
          merged-entry (merge original-entry (clean entry))
          topic-named-entry (assoc merged-entry :topic-name topic-name)
          slugged-entry (assoc topic-named-entry :topic-slug topic-slug)
          ts (db-common/current-timestamp)
          updated-authors (conj authors (assoc (lib-schema/author-for-user user) :updated-at ts))
          updated-author (assoc slugged-entry :author updated-authors)
          attachments (:attachments merged-entry)
          updated-entry (assoc updated-author :attachments (timestamp-attachments attachments ts))]
      (schema/validate common/Entry updated-entry)
      (db-common/update-resource conn table-name primary-key original-entry updated-entry ts))))

(schema/defn ^:always-validate delete-entry!
  "Given the UUID of the entry, delete the entry and all its interactions. Return `true` on success."
  [conn uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/delete-resource conn common/interaction-table-name :resource-uuid uuid)
  (db-common/delete-resource conn table-name uuid))

(schema/defn ^:always-validate list-comments-for-entry
  "Given the UUID of the entry, return a list of the comments for the entry."
  [conn uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (filter :body (db-common/read-resources conn common/interaction-table-name "resource-uuid" uuid [:uuid :author :body])))

(schema/defn ^:always-validate list-reactions-for-entry
  "Given the UUID of the entry, return a list of the reactions for the entry."
  [conn uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (filter :reaction (db-common/read-resources conn common/interaction-table-name "resource-uuid" uuid [:uuid :author :reaction])))

;; ----- Collection of entries -----

(schema/defn ^:always-validate list-entries-by-org
  "
  Given the UUID of the org, an order, one of `:asc` or `:desc`, a start date as an ISO8601 timestamp, 
  and a direction, one of `:before` or `:after`, return the entries for the org with any interactions.
  "
  [conn org-uuid :- lib-schema/UniqueID order start :- lib-schema/ISO8601 direction]
  {:pre [(db-common/conn? conn)
          (#{:desc :asc} order)
          (#{:before :after} direction)]}
  (db-common/read-resources-and-relations conn table-name :org-uuid org-uuid
                                          "created-at" order start direction config/default-limit
                                          :interactions common/interaction-table-name :uuid :resource-uuid
                                          ["uuid" "body" "reaction" "author" "created-at" "updated-at"]))

(schema/defn ^:always-validate list-entries-by-board
  "Given the UUID of the board, return the entries for the board with any interactions."
  [conn board-uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/read-resources-and-relations conn table-name :board-uuid board-uuid
                                          :interactions common/interaction-table-name :uuid :resource-uuid
                                          ["uuid" "body" "reaction" "author" "created-at" "updated-at"]))

;; ----- Data about entries -----

(schema/defn ^:always-validate entry-months-by-org
  "
  Given the UUID of the org, return an ordered sequence of all the months that have at least one entry.

  Response:

  [['2017' '06'] ['2017' '01'] [2016 '05']]

  Sequence is ordered, newest to oldest.
  "
  [conn org-uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/months-with-resource conn table-name :org-uuid org-uuid :created-at))

;; ----- Armageddon -----

(defn delete-all-entries!
  "Use with caution! Failure can result in partial deletes. Returns `true` if successful."
  [conn]
  {:pre [(db-common/conn? conn)]}
  ;; Delete all interactions and entries
  (db-common/delete-all-resources! conn common/interaction-table-name)
  (db-common/delete-all-resources! conn table-name))