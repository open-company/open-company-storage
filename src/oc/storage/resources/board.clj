(ns oc.storage.resources.board
  (:require [clojure.walk :refer (keywordize-keys)]
            [schema.core :as schema]
            [oc.lib.slugify :as slug]
            [oc.lib.rethinkdb.common :as db-common]
            [oc.lib.schema :as lib-schema]
            [oc.storage.resources.common :as common]))

;; ----- RethinkDB metadata -----

(def table-name common/board-table-name)
(def primary-key :uuid)

;; ----- Metadata -----

(def access #{:private :team :public})

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{:org-uuid :authors :viewers})

;; ----- Data Defaults -----

(def default-slug "who-we-are")
(def default-name "Who-we-are")
(def default-access :team)
(def default-promoted false)
(def default-update-template {:title "" :topics []})

;; ----- Utility functions -----

(defn- clean
  "Remove any reserved properties from the org."
  [org]
  (apply dissoc (common/clean org) reserved-properties))

;; ----- Board Slug -----

(declare list-boards)
(defn taken-slugs
  "Return all board slugs which are in use as a set."
  [conn org-uuid]
  {:pre [(db-common/conn? conn)
         (schema/validate lib-schema/UniqueID org-uuid)]}
  (map :slug (list-boards conn org-uuid)))

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
        (update :update-template #(or % default-update-template))        
        (assoc :author (common/author-for-user user))
        (assoc :created-at ts)
        (assoc :updated-at ts)))))

(schema/defn ^:always-validate create-board!
  "Create a board in the system. Throws a runtime exception if org doesn't conform to the common/Board schema."
  [conn board :- common/Board]
  {:pre [(db-common/conn? conn)]}
  (db-common/create-resource conn table-name board (db-common/current-timestamp)))

(schema/defn ^:always-validate get-board :- (schema/maybe common/Board)
  "Given the uuid of the org and slug of the board, retrieve the dasdboard, or return nil if it doesn't exist."
  [conn org-uuid slug]
  {:pre [(db-common/conn? conn)
         (schema/validate lib-schema/UniqueID org-uuid)
         (slug/valid-slug? slug)]}
  (first (db-common/read-resources conn table-name "slug-org-uuid" [[slug org-uuid]])))

; filter for archived topics
;r.db('open_company_dev').table('entries').getAll('1234-1234-1234', {index: 'board-uuid'}).group('topic-slug').max('created-at')

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