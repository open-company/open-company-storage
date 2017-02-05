(ns oc.storage.resources.entry
  (:require [clojure.walk :refer (keywordize-keys)]
            [if-let.core :refer (if-let*)]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.rethinkdb.common :as db-common]
            [oc.storage.config :as config]
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

(defn- clean
  "Remove any reserved properties from the org."
  [entry]
  (apply dissoc (common/clean entry) reserved-properties))

;; ----- Entry CRUD -----

(schema/defn ^:always-validate ->entry :- common/Entry
  "
  Take an org UUID, a board UUID, a topic slug, a minimal map describing a Entry, and a user (as the author) and
  'fill the blanks' with any missing properties.
  "
  [conn board-uuid :- lib-schema/UniqueID topic-slug :- common/TopicName org-props user :- common/User]
  {:pre [(db-common/conn? conn)
         (map? org-props)]}
  (if-let* [ts (db-common/current-timestamp)
            topic-name (keyword topic-slug)
            template-props (or (topic-name common/topics-by-name)
                               (:custom common/topics-by-name))
            board (board-res/get-board conn board-uuid)]
    (-> (merge template-props (-> org-props
                                keywordize-keys
                                clean))
        (dissoc :description :slug)
        (assoc :topic-slug topic-name)
        (update :title #(or % (:title template-props)))
        (update :headline #(or % ""))
        (update :body #(or % ""))
        (assoc :org-uuid (:org-uuid board))
        (assoc :board-uuid board-uuid)
        (assoc :author [(assoc (common/author-for-user user) :updated-at ts)])
        (assoc :created-at ts)
        (assoc :updated-at ts))))

(schema/defn ^:always-validate create-entry!
  "Create a board in the system. Throws a runtime exception if org doesn't conform to the common/Board schema."
  [conn entry :- common/Entry]
  {:pre [(db-common/conn? conn)]}
  (let [ts (db-common/current-timestamp)
        author (assoc (first (:author entry)) :updated-at ts)] ; update initial author timestamp
    (db-common/create-resource conn table-name (assoc entry :author author) ts)))

(schema/defn ^:always-validate get-entry
  ""
  [conn board-uuid :- lib-schema/UniqueID topic-slug :- common/TopicName created-at :- lib-schema/ISO8601]
  {:pre [(db-common/conn? conn)]}
  )

(schema/defn ^:always-validate delete-entry!
  "
  Given the uuid of the org, and slug of the board, delete the board and all its entries, and updates,
  and return `true` on success.
  "
  ([conn entry :- common/Entry] (delete-entry! conn (:board-uuid entry) (:topic-slug entry) (:created-at entry)))

  ([conn board-uuid :- lib-schema/UniqueID topic-slug :- common/TopicName created-at :- lib-schema/ISO8601]
  {:pre [(db-common/conn? conn)]}
  ; TODO delete by index
  ;(db-common/delete-resource conn table-name uuid))
  ))

;; ----- Collection of entries -----

(defn get-entries-by-topic
  ""
  [conn board-uuid topic-slug]
  )

(defn get-entries-by-board
  ""
  [conn board-uuid]
  ;r.db('open_company_dev').table('entries').getAll('1234-1234-1234', {index: 'board-uuid'}).group('topic-slug').max('created-at')
  ; filter for archived topics
  )

;; ----- Armageddon -----

(defn delete-all-entries!
  "Use with caution! Failure can result in partial deletes. Returns `true` if successful."
  [conn]
  {:pre [(db-common/conn? conn)]}
  ;; Delete all udpates, entries, boards and orgs
  (db-common/delete-all-resources! conn table-name))