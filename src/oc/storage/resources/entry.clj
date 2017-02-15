(ns oc.storage.resources.entry
  (:require [clojure.walk :refer (keywordize-keys)]
            [if-let.core :refer (if-let*)]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.db.common :as db-common]
            [oc.storage.resources.common :as common]
            [oc.storage.resources.board :as board-res]))

;; ----- RethinkDB metadata -----

(def table-name common/entry-table-name)
(def primary-key :id)

;; ----- Metadata -----

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{:topic-slug})

;; ----- Utility functions -----

(defn clean
  "Remove any reserved properties from the org."
  [entry]
  (apply dissoc (common/clean entry) reserved-properties))

;; ----- Entry CRUD -----

(schema/defn ^:always-validate ->entry :- common/Entry
  "
  Take an org UUID, a board UUID, a topic slug, a minimal map describing a Entry, and a user (as the author) and
  'fill the blanks' with any missing properties.
  "
  [conn board-uuid :- lib-schema/UniqueID slug :- common/TopicSlug entry-props user :- common/User]
  {:pre [(db-common/conn? conn)
         (map? entry-props)]}
  (if-let* [topic-slug (keyword slug)
            template-props (or (topic-slug common/topics-by-slug)
                               (:custom common/topics-by-slug))
            board (board-res/get-board conn board-uuid)
            ts (db-common/current-timestamp)]
    (-> (merge template-props (-> entry-props
                                keywordize-keys
                                clean))
        (dissoc :description :slug)
        (assoc :topic-slug topic-slug)
        (update :title #(or % (:title template-props)))
        (update :headline #(or % ""))
        (update :body #(or % ""))
        (assoc :org-uuid (:org-uuid board))
        (assoc :board-uuid board-uuid)
        (assoc :author [(assoc (common/author-for-user user) :updated-at ts)])
        (assoc :created-at ts)
        (assoc :updated-at ts))))

(schema/defn ^:always-validate create-entry!
  "Create an entry in the board. Throws a runtime exception if the entry doesn't conform to the common/Entry schema.

  Updates the `topics` of the board if neccessary.

  Returns the newly created entry, or false if the board specified in the entry can't be found."
  [conn entry :- common/Entry]
  {:pre [(db-common/conn? conn)]}
  (if-let* [board-uuid (:board-uuid entry)
            board (board-res/get-board conn board-uuid)]
    (let [topic-slug (:topic-slug entry)
          topics (:topics board) ; topics currently on the board
          add-topic? (not ((set topics) (name topic-slug))) ; need to add the topic for this entry to the board?
          ts (db-common/current-timestamp)
          author (assoc (first (:author entry)) :updated-at ts) ; update initial author timestamp
          entry-result (db-common/create-resource conn table-name (assoc entry :author [author]) ts)] ; create the entry
      (when (and entry-result add-topic?)
        ;; Add the topic to the board
        (board-res/update-board! conn board-uuid {:topics (conj topics topic-slug)}))
      entry-result)
    ;; No board
    false))

(schema/defn ^:always-validate get-entry
  "
  Given the ID of the entry, or the UUID of the board, the slug of the topic and the created-at timestamp,
  retrieve the entry, or return nil if it doesn't exist.
  "
  ([conn id :- lib-schema/NonBlankStr]
  {:pre [(db-common/conn? conn)]}
  (db-common/read-resource conn table-name id))
  
  ([conn board-uuid :- lib-schema/UniqueID topic-slug :- common/TopicSlug created-at :- lib-schema/ISO8601]
  {:pre [(db-common/conn? conn)]}
  (first
    (db-common/read-resources conn table-name :created-at-topic-slug-board-uuid [[created-at topic-slug board-uuid]]))))

(schema/defn ^:always-validate update-entry! :- (schema/maybe common/Entry)
  "
  Given the ID of the entry, an updated entry property map, and a user (as the author), update the entry and
  return the updated entry on success.

  Throws an exception if the merge of the prior entry and the updated entry property map doesn't conform
  to the common/Entry schema.
  "
  [conn id entry user :- common/User]
  {:pre [(db-common/conn? conn)
         (schema/validate lib-schema/UUIDStr id)
         (map? entry)]}
  (if-let [original-entry (get-entry conn id)]
    (let [authors (:author original-entry)
          merged-entry (merge original-entry (clean entry))
          ts (db-common/current-timestamp)
          updated-authors (conj authors (assoc (common/author-for-user user) :updated-at ts))
          updated-entry (assoc merged-entry :author updated-authors)]
      (schema/validate common/Entry updated-entry)
      (db-common/update-resource conn table-name primary-key original-entry updated-entry ts))))

(declare get-entries-by-topic)
(schema/defn ^:always-validate delete-entry!
  "
  Given the entry map, or the UUID of the board, the slug of the topic and the created-at timestamp, delete the entry
  and return `true` on success.
  "
  ([conn entry :- common/Entry] (delete-entry! conn (:board-uuid entry) (:topic-slug entry) (:created-at entry)))

  ([conn board-uuid :- lib-schema/UniqueID topic-slug :- common/TopicSlug created-at :- lib-schema/ISO8601]
  {:pre [(db-common/conn? conn)]}
  (if-let [board (board-res/get-board conn board-uuid)]
    (let [topics (:topics board) ; topics currently on the board
          has-topic? ((set topics) (name topic-slug)) ; topic is in the board? (could be archived)
          entry-result (db-common/delete-resource conn table-name
                          :created-at-topic-slug-board-uuid [created-at topic-slug board-uuid])]
      (when (and entry-result
                 has-topic?
                 (empty? (get-entries-by-topic conn board-uuid topic-slug)))
          ;; Remove the topic from the board
          (board-res/update-board! conn board-uuid {:topics (filterv #(not= % (name topic-slug)) topics)}))
      entry-result)
    ;; No board
    false)))

;; ----- Collection of entries -----

(schema/defn ^:always-validate get-entries-by-board
  "Given the UUID of the board, return the latest entry (by :created-at) for each topic."
  [conn board-uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/read-resources-in-group conn table-name :board-uuid board-uuid :topic-slug :created-at))

(schema/defn ^:always-validate get-entries-by-topic
  "Given the UUID of the board, and a topic slug, return all the entries for the topic slug, ordered by `created-at`."
  [conn board-uuid :- lib-schema/UniqueID topic-slug :- common/TopicSlug]
  {:pre [(db-common/conn? conn)]}
  (vec (sort-by :created-at
    (db-common/read-resources conn table-name :topic-slug-board-uuid [[topic-slug board-uuid]]))))

;; ----- Armageddon -----

(defn delete-all-entries!
  "Use with caution! Failure can result in partial deletes. Returns `true` if successful."
  [conn]
  {:pre [(db-common/conn? conn)]}
  ;; Delete all udpates, entries, boards and orgs
  (db-common/delete-all-resources! conn table-name))