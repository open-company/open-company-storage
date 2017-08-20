(ns oc.storage.resources.board
  (:require [clojure.walk :refer (keywordize-keys)]
            [if-let.core :refer (when-let*)]
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

(def ignored-properties
  "Properties of a resource that are ignored during an update."
  (merge reserved-properties #{:type}))

;; ----- Data Defaults -----

(def default-boards ["Who We Are" "Company News"])
(def default-storyboards ["All-hands Update" "Investor Update"])

(def default-access :team)

(def default-drafts-storyboard {
  :uuid "0000-0000-0000"
  :name "Drafts"
  :slug "drafts"
  :type "story"
  :viewers []
  :access :private})

;; ----- Utility functions -----

(defn clean
  "Remove any reserved properties from the board."
  [board]
  (apply dissoc (common/clean board) reserved-properties))

(defn ignore-props
  "Remove any ignored properties from the board."
  [board]
  (apply dissoc board ignored-properties))

(schema/defn ^:always-validate drafts-storyboard :- common/Board
  "Return a storyboard for the specified org and author."
  [org-uuid :- lib-schema/UniqueID user :- lib-schema/User]
  (let [now (db-common/current-timestamp)]
    (merge default-drafts-storyboard {
      :org-uuid org-uuid
      :author (lib-schema/author-for-user user)
      :authors [(:user-id user)]
      :created-at now
      :updated-at now})))

;; ----- Board Slug -----

(def reserved-slugs #{"create-board" "settings" "boards" "stories" "activity"})

(declare list-all-boards-by-org)
(defn taken-slugs
  "Return all board slugs which are in use as a set."
  [conn org-uuid]
  {:pre [(db-common/conn? conn)
         (schema/validate lib-schema/UniqueID org-uuid)]}
  (into reserved-slugs (map :slug (list-all-boards-by-org conn org-uuid))))

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

  ([org-uuid slug board-props user :- lib-schema/User]
  {:pre [(schema/validate lib-schema/UniqueID org-uuid)
         (slug/valid-slug? slug)
         (map? board-props)]}
  (let [ts (db-common/current-timestamp)]
    (-> board-props
        keywordize-keys
        clean
        (assoc :uuid (db-common/unique-id))
        (update :type #(or % :entry))
        (assoc :slug slug)
        (assoc :org-uuid org-uuid)
        (update :access #(or % default-access))
        (assoc :authors [(:user-id user)])
        (assoc :viewers [])
        (assoc :author (lib-schema/author-for-user user))
        (assoc :created-at ts)
        (assoc :updated-at ts)))))

(schema/defn ^:always-validate ->storyboard :- common/Board
  "
  Take an org UUID, a minimal map describing a storyboard, a user and an optional slug and 'fill the blanks' with
  any missing properties.
  "
  ([org-uuid board-props user]
  (assoc (->board org-uuid board-props user) :type :story))

  ([org-uuid slug board-props user :- lib-schema/User]
  (assoc (->board org-uuid slug board-props user) :type :story)))
  
(schema/defn ^:always-validate create-board!
  "
  Create a board in the system. Throws a runtime exception if the board doesn't conform to the common/Board schema.

  Check the slug in the response as it may change if there is a conflict with another board for the org.
  "
  [conn board :- common/Board]
  {:pre [(db-common/conn? conn)]}
  (db-common/create-resource conn table-name
    (update board :slug #(slug/find-available-slug % (taken-slugs conn (:org-uuid board))))
    (db-common/current-timestamp)))

(schema/defn ^:always-validate get-board :- (schema/maybe common/Board)
  "
  Given the uuid of the board, or the uuid of the org and slug of the board, retrieve the board,
  or return nil if it doesn't exist.
  "
  ([conn uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/read-resource conn table-name uuid))

  ([conn org-uuid :- lib-schema/UniqueID slug]
  {:pre [(db-common/conn? conn)
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
  to the common/Board schema.
  
  NOTE: doesn't update authors, see: `add-author`, `remove-author`
  NOTE: doesn't update viewers, see: `add-viewer`, `remove-viewer`
  NOTE: doesn't handle case of slug change.
  "
  [conn uuid :- lib-schema/UniqueID board]
  {:pre [(db-common/conn? conn)
         (map? board)]}
  (when-let [original-board (get-board conn uuid)]
    (let [updated-board (merge original-board (ignore-props board))]
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
  ;; Delete interactions
  (db-common/delete-resource conn common/interaction-table-name :board-uuid uuid)
  ;; Delete entries
  (db-common/delete-resource conn common/entry-table-name :board-uuid uuid)
  ;; Delete the board itself
  (db-common/delete-resource conn table-name uuid)))

;; ----- Board's set operations -----

(schema/defn ^:always-validate add-author :- (schema/maybe common/Board)
  "
  Given the unique ID of the org, the slug of the board, and the user-id of the user, add the user as an author of
  the board if it exists.
  
  Returns the updated board on success, nil on non-existence, and a RethinkDB error map on other errors.
  "
  [conn org-uuid :- lib-schema/UniqueID slug user-id :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? slug)]}
  (when-let* [board (get-board conn org-uuid slug)]
    (db-common/remove-from-set conn table-name (:uuid board) "viewers" user-id)
    (db-common/add-to-set conn table-name (:uuid board) "authors" user-id)))

(schema/defn ^:always-validate remove-author :- (schema/maybe common/Board)
  "
  Given the unique ID of the org, the slug of the board, and the user-id of the user, remove the user as an author of
  the board if it exists.
  
  Returns the updated board on success, nil on non-existence, and a RethinkDB error map on other errors.
  "
  [conn org-uuid :- lib-schema/UniqueID slug user-id :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? slug)]}
  (when-let* [board (get-board conn org-uuid slug)]
    (db-common/remove-from-set conn table-name (:uuid board) "authors" user-id)))

(schema/defn ^:always-validate add-viewer :- (schema/maybe common/Board)
  "
  Given the unique ID of the org, the slug of the board, and the user-id of the user, add the user as a viewer of
  the board if it exists.
  
  Returns the updated board on success, nil on non-existence, and a RethinkDB error map on other errors.
  "
  [conn org-uuid :- lib-schema/UniqueID slug user-id :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? slug)]}
  (when-let* [board (get-board conn org-uuid slug)]
    (db-common/remove-from-set conn table-name (:uuid board) "authors" user-id)
    (db-common/add-to-set conn table-name (:uuid board) "viewers" user-id)))

(schema/defn ^:always-validate remove-viewer :- (schema/maybe common/Board)
  "
  Given the unique ID of the org, the slug of the board, and the user-id of the user, remove the user as a viewer of
  the board if it exists.
  
  Returns the updated board on success, nil on non-existence, and a RethinkDB error map on other errors.
  "
  [conn org-uuid :- lib-schema/UniqueID slug user-id :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? slug)]}
  (when-let* [board (get-board conn org-uuid slug)]
    (db-common/remove-from-set conn table-name (:uuid board) "viewers" user-id)))

;; ----- Collection of boards -----

(defn list-all-boards
  "
  Return a sequence of boards and storyboards with slugs, UUIDs, names, type, and org-uuid, sorted by slug.
  
  Note: if additional-keys are supplied, they will be included in the map, and only boards
  containing those keys will be returned.
  "
  ([conn] (list-all-boards conn []))

  ([conn additional-keys]
  {:pre [(db-common/conn? conn)
         (sequential? additional-keys)
         (every? #(or (string? %) (keyword? %)) additional-keys)]}
  (->> (into [primary-key :slug :name :org-uuid :type] additional-keys)
    (db-common/read-resources conn table-name)
    (sort-by :slug)
    vec)))

(defn list-all-boards-by-org
  "
  Return a sequence of boards and storyboards with slugs, UUIDs, type and names, sorted by slug.
  
  Note: if additional-keys are supplied, they will be included in the map, and only boards
  containing those keys will be returned.
  "
  ([conn org-uuid] (list-all-boards-by-org conn org-uuid []))

  ([conn org-uuid additional-keys]
  {:pre [(db-common/conn? conn)
         (schema/validate lib-schema/UniqueID org-uuid)
         (sequential? additional-keys)
         (every? #(or (string? %) (keyword? %)) additional-keys)]}
  (->> (into [primary-key :slug :name :type] additional-keys)
    (db-common/read-resources conn table-name :org-uuid org-uuid)
    (sort-by :slug)
    vec)))

(defn list-boards-by-org
  "
  Return a sequence of boards with slugs, UUIDs, type and names, sorted by slug.
  
  Note: if additional-keys are supplied, they will be included in the map, and only boards
  containing those keys will be returned.
  "
  ([conn org-uuid] (list-boards-by-org conn org-uuid []))

  ([conn org-uuid additional-keys]
  (filter #(= (keyword (:type %)) :entry) (list-all-boards-by-org conn org-uuid additional-keys))))

(defn list-storyboards-by-org
  "
  Return a sequence of boards with slugs, UUIDs, type and names, sorted by slug.
  
  Note: if additional-keys are supplied, they will be included in the map, and only boards
  containing those keys will be returned.
  "
  ([conn org-uuid] (list-storyboards-by-org conn org-uuid []))

  ([conn org-uuid additional-keys]
  (filter #(= (keyword (:type %)) :story) (list-all-boards-by-org conn org-uuid additional-keys))))

(defn list-all-boards-by-index
  "
  Given the name of a secondary index and a value, retrieve all matching boards and storyboards
  as a sequence of maps with slugs, UUIDs and names, sorted by slug.
  
  Secondary indexes:
  :uuid
  :slug
  :authors
  :viewers

  Note: if additional-keys are supplied, they will be included in the map, and only boards
  containing those keys will be returned.
  "
  ([conn index-key index-value] (list-all-boards-by-index conn index-key index-value []))

  ([conn index-key index-value additional-keys]
  {:pre [(db-common/conn? conn)
         (or (keyword? index-key) (string? index-key))
         (sequential? additional-keys)
         (every? #(or (string? %) (keyword? %)) additional-keys)]}

  (->> (into [primary-key :slug :name :type] additional-keys)
    (db-common/read-resources conn table-name index-key index-value)
    (sort-by :slug)
    vec)))

(defn list-boards-by-index
  "
  Given the name of a secondary index and a value, retrieve all matching boards
  as a sequence of maps with slugs, UUIDs and names, sorted by slug.
  
  Secondary indexes:
  :uuid
  :slug
  :authors
  :viewers

  Note: if additional-keys are supplied, they will be included in the map, and only boards
  containing those keys will be returned.
  "
  ([conn index-key index-value] (list-boards-by-index conn index-key index-value []))

  ([conn index-key index-value additional-keys]
  (filter #(= (keyword (:type %)) :entry) (list-all-boards-by-index conn index-key index-value additional-keys))))

(defn list-storyboards-by-index
  "
  Given the name of a secondary index and a value, retrieve all matching storyboards
  as a sequence of maps with slugs, UUIDs and names, sorted by slug.
  
  Secondary indexes:
  :uuid
  :slug
  :authors
  :viewers

  Note: if additional-keys are supplied, they will be included in the map, and only boards
  containing those keys will be returned.
  "
  ([conn index-key index-value] (list-storyboards-by-index conn index-key index-value []))

  ([conn index-key index-value additional-keys]
  (filter #(= (keyword (:type %)) :story) (list-all-boards-by-index conn index-key index-value additional-keys))))

;; ----- Armageddon -----

(defn delete-all-boards!
  "Use with caution! Failure can result in partial deletes. Returns `true` if successful."
  [conn]
  {:pre [(db-common/conn? conn)]}
  ;; Delete all interactions, entries, and boards
  (db-common/delete-all-resources! conn common/interaction-table-name)
  (db-common/delete-all-resources! conn common/entry-table-name)
  (db-common/delete-all-resources! conn table-name))