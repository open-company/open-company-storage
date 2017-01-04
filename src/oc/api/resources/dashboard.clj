(ns oc.api.resources.dashboard
  (:require [clojure.walk :refer (keywordize-keys)]
            [schema.core :as schema]
            [oc.lib.slugify :as slug]
            [oc.lib.rethinkdb.common :as db-common]
            [oc.api.resources.common :as common]))

;; ----- RethinkDB metadata -----

(def table-name common/dashboard-table-name)
(def primary-key :uuid)

;; ----- Metadata -----

(def access #{:private :team :public})

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{:org-uuid :authors :viewers})

;; ----- Data Defaults -----

(def default-slug "dashboard")
(def default-name "Dashboard")
(def default-access :team)
(def default-promoted false)
(def default-update-template {:title "" :topics []})

;; ----- Utility functions -----

(defn- clean
  "Remove any reserved properties from the org."
  [org]
  (apply dissoc (common/clean org) reserved-properties))

;; ----- Dashboard Slug -----

(declare list-dashboards)
(defn taken-slugs
  "Return all dashboard slugs which are in use as a set."
  [conn org-uuid]
  {:pre [(db-common/conn? conn)
         (schema/validate common/UniqueID org-uuid)]}
  (map :slug (list-dashboards conn org-uuid)))

(defn slug-available?
  "Return true if the slug is not used by any dashboard in the org."
  [conn org-uuid slug]
  {:pre [(db-common/conn? conn)
         (schema/validate common/UniqueID org-uuid)
         (slug/valid-slug? slug)]}
  (not (contains? (taken-slugs conn) slug)))

;; ----- Org CRUD -----

(schema/defn ^:always-validate ->dashboard :- common/Dashboard
  "
  Take an org UUID, a minimal map describing a dashboard, a user and an optional slug and 'fill the blanks' with
  any missing properties.
  "
  ([org-uuid dash-props user]
  (->dashboard org-uuid (or (:slug dash-props) (slug/slugify (:name dash-props))) dash-props user))

  ([org-uuid slug dash-props user :- common/User]
  {:pre [(schema/validate common/UniqueID org-uuid)
         (slug/valid-slug? slug)
         (map? dash-props)]}
  (let [ts (db-common/current-timestamp)]
    (-> dash-props
        keywordize-keys
        clean
        (assoc :uuid (db-common/unique-id))
        (assoc :slug slug)
        (assoc :org-uuid org-uuid)
        (update :access #(or % default-access))
        (update :promoted #(or % default-promoted))
        (assoc :authors [])
        (assoc :viewers [])
        (update :topics #(or % []))        
        (update :update-template #(or % default-update-template))        
        (assoc :author (common/author-for-user user))
        (assoc :created-at ts)
        (assoc :updated-at ts)))))

(schema/defn ^:always-validate create-dashboard!
  "Create a dashboard in the system. Throws a runtime exception if org doesn't conform to the common/Dashboard schema."
  [conn dashboard :- common/Dashboard]
  {:pre [(db-common/conn? conn)]}
  (db-common/create-resource conn table-name dashboard (db-common/current-timestamp)))

(schema/defn ^:always-validate get-dashboard :- (schema/maybe common/Dashboard)
  "Given the uuid of the org and slug of the dashboard, retrieve the dasdboard, or return nil if it doesn't exist."
  [conn org-uuid slug]
  {:pre [(db-common/conn? conn)
         (schema/validate common/UniqueID org-uuid)
         (slug/valid-slug? slug)]}
  (first (db-common/read-resources conn table-name "slug-org-uuid" [slug org-uuid])))

;r.db('open_company_dev').table('sections').getAll('green-labs', {index: 'company-slug'}).group('section-name').max('created-at')

;; ----- Collection of dashboards -----

(defn list-dashboards
  "
  Return a sequence of maps with slugs, UUIDs and names, sorted by slug.
  Note: if additional-keys are supplied, they will be included in the map, and only dashboards
  containing those keys will be returned.
  "
  ([conn org-uuid] (list-dashboards conn org-uuid []))

  ([conn org-uuid additional-keys]
  {:pre [(db-common/conn? conn)
         (schema/validate common/UniqueID org-uuid)
         (sequential? additional-keys)
         (every? #(or (string? %) (keyword? %)) additional-keys)]}
  (->> (into [primary-key "slug" "name"] additional-keys)
    (db-common/read-resources conn table-name "org-uuid" org-uuid)
    (sort-by "slug")
    vec)))

(defn get-dashboards-by-index
  "
  Given the name of a secondary index and a value, retrieve all matching dashboards.

  Secondary indexes:
  :uuid
  :slug
  :access-promoted
  :authors
  :viewers
  :topics
  "
  [conn index-key index-value]
  {:pre [(db-common/conn? conn)
         (string? index-key)]}
  (db-common/read-resources conn table-name index-key index-value))