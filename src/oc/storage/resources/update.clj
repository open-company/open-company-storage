(ns oc.storage.resources.update
  (:require [clojure.string :as s]
            [clojure.walk :refer (keywordize-keys)]
            [if-let.core :refer (if-let*)]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.slugify :as slug]
            [oc.lib.db.common :as db-common]
            [oc.storage.resources.common :as common]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.entry :as entry-res]))

;; ----- RethinkDB metadata -----

(def table-name common/update-table-name)
(def primary-key :id)

;; ----- Metadata -----

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{})

;; ----- Utility functions -----

(defn- clean
  "Remove any reserved properties from the org."
  [update]
  (apply dissoc (common/clean update) reserved-properties))

(defn- slug-for
  "Create a slug for the update from the slugified title and a short UUID."
  [title]
  (let [non-blank-title (if (s/blank? title) "update" title)]
    (str (slug/slugify non-blank-title) "-" (subs (str (java.util.UUID/randomUUID)) 0 5))))

(schema/defn ^:always-validate entry-for :- common/UpdateEntry
  "
  Given an entry spec from a share request, get the specified entry from the DB.

  Throws an exception if the entry isn't found.
  "
  [conn org-uuid entry]
  (let [topic-slug (:topic-slug entry)
        timestamp (:created-at entry)]
    (if-let [entry (entry-res/get-entry conn :org org-uuid topic-slug timestamp)]
      (dissoc entry :org-uuid :board-uuid :body-placeholder :prompt)
      (throw (ex-info "Invalid entry." {:org-uuid org-uuid :topic-slug topic-slug :created-at timestamp})))))

;; ----- Update Slug -----

(declare list-updates-by-org)
(defn taken-slugs
  "Return all update slugs which are in use as a set."
  [conn org-uuid]
  {:pre [(db-common/conn? conn)
         (schema/validate lib-schema/UniqueID org-uuid)]}
  (map :slug (list-updates-by-org conn org-uuid)))

(defn slug-available?
  "Return true if the slug is not used by any update in the org."
  [conn org-uuid slug]
  {:pre [(db-common/conn? conn)
         (schema/validate lib-schema/UniqueID org-uuid)
         (slug/valid-slug? slug)]}
  (not (contains? (taken-slugs conn) slug)))

;; ----- Update CRUD -----

(schema/defn ^:always-validate ->update :- common/Update
  "
  Take an org slug, a minimal map containing a ShareRequest, and a user as the update author
  and 'fill the blanks' with any missing properties.
  "
  [conn org-slug update-props :- common/ShareRequest user :- common/User]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? org-slug)]}
  (if-let* [org (org-res/get-org conn org-slug)
            org-uuid (:uuid org)
            ts (db-common/current-timestamp)
            entries (map #(entry-for conn org-uuid %) (:entries update-props))]
    (-> update-props
        keywordize-keys
        clean
        (assoc :slug (slug-for (:title update-props)))
        (assoc :org-uuid org-uuid)
        (assoc :org-name (:name org))
        (assoc :currency (:currency org))
        (assoc :logo-url (:logo-url org))
        (assoc :logo-width (or (:logo-width org) 0))
        (assoc :logo-height (or (:logo-height org) 0))
        (assoc :entries entries)
        (assoc :author (common/author-for-user user))
        (assoc :created-at ts)
        (assoc :updated-at ts))
    (throw (ex-info "Invalid org slug." {:slug org-slug}))))

(schema/defn ^:always-validate create-update!
  "Create an update in the system. Throws a runtime exception if the update doesn't conform to the common/Update schema."
  ([conn update] (create-update! conn update (db-common/current-timestamp)))
  
  ([conn update :- common/Update timestamp :- lib-schema/ISO8601]
  {:pre [(db-common/conn? conn)]}
  (db-common/create-resource conn table-name update timestamp)))

(schema/defn ^:always-validate get-update :- (schema/maybe common/Update)
  "
  Given the unique ID of the org, and slug of the update, retrieve the update,
  or return nil if it doesn't exist.
  "
  [conn org-uuid :- lib-schema/UniqueID slug]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? slug)]}
  (first (db-common/read-resources conn table-name "slug-org-uuid" [[slug org-uuid]])))

;; ----- Collection of updates -----

(defn list-updates
  "
  Return a sequence of maps with slugs, titles, medium, created-at and org-uuid, sorted by slug.
  
  Note: if additional-keys are supplied, they will be included in the map, and only updates
  containing those keys will be returned.
  "
  ([conn] (list-updates conn []))

  ([conn additional-keys]
  {:pre [(db-common/conn? conn)
         (sequential? additional-keys)
         (every? #(or (string? %) (keyword? %)) additional-keys)]}
  (->> (into [primary-key :slug :title :org-uuid :medium :created-at] additional-keys)
    (db-common/read-resources conn table-name)
    (sort-by :slug)
    vec)))

(schema/defn ^:always-validate list-updates-by-org
  "
  Return a sequence of maps with slugs, titles, medium, and created-at, sorted by created-at.
  
  Note: if additional-keys are supplied, they will be included in the map, and only boards
  containing those keys will be returned.
  "
  ([conn org-uuid] (list-updates-by-org conn org-uuid []))

  ([conn org-uuid :- lib-schema/UniqueID additional-keys]
  {:pre [(db-common/conn? conn)
         (sequential? additional-keys)
         (every? #(or (string? %) (keyword? %)) additional-keys)]}
  (->> (into [primary-key :slug :title :medium :created-at] additional-keys)
    (db-common/read-resources conn table-name :org-uuid org-uuid)
    (sort-by :created-at)
    vec)))

(schema/defn ^:always-validate list-updates-by-author
  "
  Return a sequence of maps with slugs, titles, medium, and created-at, sorted by created-at.
  
  Note: if additional-keys are supplied, they will be included in the map, and only boards
  containing those keys will be returned.
  "
  ([conn org-uuid user-id] (list-updates-by-author conn org-uuid user-id []))

  ([conn org-uuid :- lib-schema/UniqueID user-id :- lib-schema/UniqueID additional-keys]
  {:pre [(db-common/conn? conn)
         (sequential? additional-keys)
         (every? #(or (string? %) (keyword? %)) additional-keys)]}
  (->> (into [primary-key :slug :title :medium :created-at] additional-keys)
    (db-common/read-resources conn table-name :author-user-id-org-uuid [[user-id org-uuid]])
    (sort-by :created-at)
    vec)))

;; ----- Armageddon -----

(defn delete-all-updates!
  "Use with caution! Failure can result in partial deletes. Returns `true` if successful."
  [conn]
  {:pre [(db-common/conn? conn)]}
  ;; Delete all udpates
  (db-common/delete-all-resources! conn table-name))