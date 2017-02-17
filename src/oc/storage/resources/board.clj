(ns oc.storage.resources.board
  (:require [clojure.walk :refer (keywordize-keys)]
            [defun.core :refer (defun)]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.slugify :as slug]
            [oc.lib.db.common :as db-common]
            [oc.storage.resources.common :as common]
            [oc.storage.resources.org :as org-res]))

;; ----- RethinkDB metadata -----

(def table-name common/board-table-name)
(def primary-key :uuid)

;; ----- Metadata -----

(def access #{:private :team :public})

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{:authors :viewers})

;; ----- Data Defaults -----

(def default-boards ["Who-We-Are" "All-Hands"])

(def default-access :team)

;; ----- Utility functions -----

(defn- clean
  "Remove any reserved properties from the org."
  [org]
  (apply dissoc (common/clean org) reserved-properties))

;; ----- Board Slug -----

(def reserved-slugs #{"create-board"})

(declare get-boards-by-org)
(defn taken-slugs
  "Return all board slugs which are in use as a set."
  [conn org-uuid]
  {:pre [(db-common/conn? conn)
         (schema/validate lib-schema/UniqueID org-uuid)]}
  (map :slug (get-boards-by-org conn org-uuid)))

(defn slug-available?
  "Return true if the slug is not used by any board in the org."
  [conn org-uuid slug]
  {:pre [(db-common/conn? conn)
         (schema/validate lib-schema/UniqueID org-uuid)
         (slug/valid-slug? slug)]}
  (not (contains? (taken-slugs conn) slug)))

;; ----- Org CRUD -----

(schema/defn ^:always-validate ->board :- common/Board
  "
  Take an org UUID, a minimal map describing a board, a user and an optional slug and 'fill the blanks' with
  any missing properties.
  "
  ([org-uuid board-props user]
  {:pre [(schema/validate lib-schema/NonBlankStr (:name board-props))]}
  (->board org-uuid (or (:slug board-props) (slug/slugify (:name board-props))) board-props user))

  ([org-uuid slug board-props user :- common/User]
  {:pre [(schema/validate lib-schema/UniqueID org-uuid)
         (slug/valid-slug? slug)
         (map? board-props)]}
  (let [ts (db-common/current-timestamp)]
    (-> board-props
        keywordize-keys
        clean
        (assoc :uuid (db-common/unique-id))
        (assoc :slug slug)
        (assoc :org-uuid org-uuid)
        (update :access #(or % default-access))
        (assoc :authors [(:user-id user)])
        (assoc :viewers [])
        (update :topics #(or % []))        
        (assoc :author (common/author-for-user user))
        (assoc :created-at ts)
        (assoc :updated-at ts)))))

(schema/defn ^:always-validate create-board!
  "Create a board in the system. Throws a runtime exception if the board doesn't conform to the common/Board schema."
  [conn board :- common/Board]
  {:pre [(db-common/conn? conn)]}
  (db-common/create-resource conn table-name board (db-common/current-timestamp)))

(schema/defn ^:always-validate get-board :- (schema/maybe common/Board)
  "
  Given the uuid of the board, or the uuid of the org and slug of the board, retrieve the board,
  or return nil if it doesn't exist.
  "
  ([conn uuid]
  {:pre [(db-common/conn? conn)
         (schema/validate lib-schema/UniqueID uuid)]}
  (db-common/read-resource conn table-name uuid))

  ([conn org-uuid slug]
  {:pre [(db-common/conn? conn)
         (schema/validate lib-schema/UniqueID org-uuid)
         (slug/valid-slug? slug)]}
  (first (db-common/read-resources conn table-name "slug-org-uuid" [[slug org-uuid]]))))

(schema/defn ^:always-validate uuid-for :- (schema/maybe lib-schema/UniqueID)
  "Given an org slug, and a board slug, return the UUID of the board, or nil if it doesn't exist."
  [conn org-slug slug]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? org-slug)
         (slug/valid-slug? slug)]}
  (when-let [org-uuid (org-res/uuid-for conn org-slug)]
    (:uuid (get-board conn org-uuid slug))))

(schema/defn ^:always-validate update-board! :- (schema/maybe common/Board)
  "
  Given the board UUID and an updated board property map, update the board and return the updated board on success.

  Throws an exception if the merge of the prior board and the updated board property map doesn't conform
  to the common/Org schema.
  
  NOTE: doesn't update authors, see: `add-author`, `remove-author`
  NOTE: doesn't update viewers, see: `add-viewer`, `remove-viewer`
  NOTE: doesn't handle case of slug change.
  "
  [conn uuid :- lib-schema/UniqueID board]
  {:pre [(db-common/conn? conn)
         (map? board)]}
  (if-let [original-board (get-board conn uuid)]
    (let [updated-board (merge original-board (clean board))]
      (schema/validate common/Board updated-board)
      (db-common/update-resource conn table-name primary-key original-board updated-board))))

(defun delete-board!
  "
  Given the uuid of the org, and slug of the board, delete the board and all its entries, and updates,
  and return `true` on success.
  "
  ([conn :guard db-common/conn? board :guard map?]
  (delete-board! conn (:uuid board)))

  ([conn :guard db-common/conn? org-uuid :guard #(schema/validate lib-schema/UniqueID %) slug :guard slug/valid-slug?]
  (if-let [uuid (:uuid (get-board conn org-uuid slug))]
    (delete-board! conn uuid)))

  ([conn :guard db-common/conn? uuid :guard #(schema/validate lib-schema/UniqueID %)]
  ;; Delete entries
  (try
    (db-common/delete-resource conn common/entry-table-name :board-uuid uuid)
    (catch java.lang.RuntimeException e)) ; it's OK if there are no entries to delete
  ;; Delete the board itself
  (db-common/delete-resource conn table-name uuid)))

;; ----- Collection of boards -----

(defn list-boards
  "
  Return a sequence of maps with slugs, UUIDs, names and org-uuid, sorted by slug.
  
  Note: if additional-keys are supplied, they will be included in the map, and only boards
  containing those keys will be returned.
  "
  ([conn] (list-boards conn []))

  ([conn additional-keys]
  {:pre [(db-common/conn? conn)
         (sequential? additional-keys)
         (every? #(or (string? %) (keyword? %)) additional-keys)]}
  (->> (into [primary-key :slug :name :org-uuid] additional-keys)
    (db-common/read-resources conn table-name)
    (sort-by :slug)
    vec)))

(defn get-boards-by-org
  "
  Return a sequence of maps with slugs, UUIDs and names, sorted by slug.
  
  Note: if additional-keys are supplied, they will be included in the map, and only boards
  containing those keys will be returned.
  "
  ([conn org-uuid] (get-boards-by-org conn org-uuid []))

  ([conn org-uuid additional-keys]
  {:pre [(db-common/conn? conn)
         (schema/validate lib-schema/UniqueID org-uuid)
         (sequential? additional-keys)
         (every? #(or (string? %) (keyword? %)) additional-keys)]}
  (->> (into [primary-key :slug :name] additional-keys)
    (db-common/read-resources conn table-name :org-uuid org-uuid)
    (sort-by :slug)
    vec)))

(defn get-boards-by-index
  "
  Given the name of a secondary index and a value, retrieve all matching boards
  as a sequence of maps with slugs, UUIDs and names, sorted by slug.
  
  Secondary indexes:
  :uuid
  :slug
  :authors
  :viewers
  :topics

  Note: if additional-keys are supplied, they will be included in the map, and only boards
  containing those keys will be returned.
  "
  ([conn index-key index-value] (get-boards-by-index conn index-key index-value []))

  ([conn index-key index-value additional-keys]
  {:pre [(db-common/conn? conn)
         (or (keyword? index-key) (string? index-key))
         (sequential? additional-keys)
         (every? #(or (string? %) (keyword? %)) additional-keys)]}

  (->> (into [primary-key :slug :name] additional-keys)
    (db-common/read-resources conn table-name index-key index-value)
    (sort-by "slug")
    vec)))

;; ----- Armageddon -----

(defn delete-all-boards!
  "Use with caution! Failure can result in partial deletes. Returns `true` if successful."
  [conn]
  {:pre [(db-common/conn? conn)]}
  ;; Delete all udpates, entries, boards and orgs
  (db-common/delete-all-resources! conn common/update-table-name)
  (db-common/delete-all-resources! conn common/entry-table-name)
  (db-common/delete-all-resources! conn table-name))