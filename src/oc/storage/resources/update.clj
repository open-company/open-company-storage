(ns oc.storage.resources.update
  (:require [clojure.walk :refer (keywordize-keys)]
            [if-let.core :refer (if-let*)]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.slugify :as slug]
            [oc.lib.db.common :as db-common]
            [oc.storage.resources.common :as common]
            [oc.storage.resources.org :as org-res]))

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

;; ----- Update Slug -----

(declare get-updates-by-org)
(defn taken-slugs
  "Return all update slugs which are in use as a set."
  [conn org-uuid]
  {:pre [(db-common/conn? conn)
         (schema/validate lib-schema/UniqueID org-uuid)]}
  (map :slug (get-updates-by-org conn org-uuid)))

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
  Take an org UUID, a minimal map describing as update, a user and an optional slug and 'fill the blanks' with
  any missing properties.
  "
  ; ([conn org-uuid update-props user]
  ; (->update org-uuid (or (:slug update-props) (slug/slugify (:name board-props))) update-props user))

  ([conn org-slug slug update-props user :- common/User]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? org-slug)
         (slug/valid-slug? slug)
         (map? update-props)]}
  (if-let* [org (org-res/get-org conn org-slug)
            ts (db-common/current-timestamp)]
    (-> update-props
        keywordize-keys
        clean
        (assoc :slug slug)
        (assoc :org-uuid (:uuid org))
        (assoc :currency (:currency org))
        (assoc :logo-url (:logo-url org))
        (assoc :logo-width (or (:logo-width org) 0))
        (assoc :logo-height (or (:logo-height org) 0))
        (assoc :topics [])
        (assoc :author (common/author-for-user user))
        (assoc :created-at ts)
        (assoc :updated-at ts))
    
      false))) ; no org for that slug

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

(defn get-updates-by-org
  "
  Return a sequence of maps with slugs, titles, medium, and created-at, sorted by slug.
  
  Note: if additional-keys are supplied, they will be included in the map, and only boards
  containing those keys will be returned.
  "
  ([conn org-uuid] (get-updates-by-org conn org-uuid []))

  ([conn org-uuid additional-keys]
  {:pre [(db-common/conn? conn)
         (schema/validate lib-schema/UniqueID org-uuid)
         (sequential? additional-keys)
         (every? #(or (string? %) (keyword? %)) additional-keys)]}
  (->> (into [primary-key :slug :title :medium :created-at] additional-keys)
    (db-common/read-resources conn table-name :org-uuid org-uuid)
    (sort-by :slug)
    vec)))