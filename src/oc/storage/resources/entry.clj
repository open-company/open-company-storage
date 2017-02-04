(ns oc.storage.resources.entry
  (:require [clojure.walk :refer (keywordize-keys)]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.rethinkdb.common :as db-common]
            [oc.storage.resources.common :as common]))

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
  Take an org UUID, a board UUID, a minimal map describing a Entry, and a user (as the author) and
  'fill the blanks' with any missing properties.
  "
  [org-uuid board-uuid org-props user :- common/User]
  {:pre [(schema/validate lib-schema/UniqueID org-uuid)
         (schema/validate lib-schema/UniqueID board-uuid)
         (map? org-props)
         (map? user)]}
  (let [ts (db-common/current-timestamp)]
    (-> org-props
        keywordize-keys
        clean
        ; topic-slug
        ; title
        ; headline
        ; body
        (assoc :org-uuid org-uuid)
        (assoc :board-uuid board-uuid)
        (assoc :author (common/author-for-user user))
        (assoc :created-at ts)
        (assoc :updated-at ts))))

; filter for archived topics
;r.db('open_company_dev').table('entries').getAll('1234-1234-1234', {index: 'board-uuid'}).group('topic-slug').max('created-at')

;; ----- Armageddon -----

(defn delete-all-entries!
  "Use with caution! Failure can result in partial deletes. Returns `true` if successful."
  [conn]
  {:pre [(db-common/conn? conn)]}
  ;; Delete all udpates, entries, boards and orgs
  (db-common/delete-all-resources! conn table-name))