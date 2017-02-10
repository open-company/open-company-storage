(ns oc.storage.resources.org
  (:require [clojure.walk :refer (keywordize-keys)]
            [schema.core :as schema]
            [oc.lib.slugify :as slug]
            [oc.lib.schema :as lib-schema]
            [oc.lib.db.common :as db-common]
            [oc.storage.resources.common :as common]))

;; ----- RethinkDB metadata -----

(def table-name common/org-table-name)
(def primary-key :slug)

;; ----- Metadata -----

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{:team-id :authors})

;; ----- Data Defaults -----

(def default-promoted false)

;; ----- Utility functions -----

(defn- clean
  "Remove any reserved properties from the org."
  [org]
  (apply dissoc (common/clean org) reserved-properties))

;; ----- Organization Slug -----

(def reserved-slugs #{"about" "android" "api" "app" "careers" "companies"
                      "company" "contact" "create-company" "crowd" "developer"
                      "developers" "download" "email-confirmation" "faq" "forum" "forums" 
                      "founder" "founders" "help" "home"
                      "investor" "investors" "invite" "ios" "jobs" "login" "logout"
                      "news" "newsletter" "press" "privacy" "profile" "register" "reset"
                      "section" "sections" "signin" "signout" "signup" "stakeholder"
                      "stakeholder-update" "subscription-completed" "terms" "topic" "topics"
                      "update" "updates"})

(declare list-orgs)
(defn taken-slugs
  "Return all org slugs which are in use as a set."
  [conn]
  {:pre [(db-common/conn? conn)]}
  (into reserved-slugs (map :slug (list-orgs conn))))

(defn slug-available?
  "Return true if the slug is not used by any org in the system."
  [conn slug]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? slug)]}
  (not (contains? (taken-slugs conn) slug)))

;; ----- Org CRUD -----

(schema/defn ^:always-validate ->org :- common/Org
  "Take a minimal map describing an org and a user and an optional slug and 'fill the blanks' with any missing properties."
  ([org-props user]
  (->org (or (:slug org-props) (slug/slugify (:name org-props))) org-props user))

  ([slug org-props user :- common/User]
  {:pre [(slug/valid-slug? slug)
         (map? org-props)]}
  (let [ts (db-common/current-timestamp)]
    (-> org-props
        keywordize-keys
        clean
        (assoc :slug slug)
        (assoc :uuid (db-common/unique-id))
        (assoc :team-id (first (:teams user))) ; TODO: how do we decide which auth-id to create the org with?
        (assoc :authors [(:user-id user)])
        (update :currency #(or % "USD"))
        (update :promoted #(or % default-promoted))
        (assoc :author (common/author-for-user user))
        (assoc :created-at ts)
        (assoc :updated-at ts)))))

(schema/defn ^:always-validate create-org!
  "Create an org in the system. Throws a runtime exception if org doesn't conform to the common/Org schema."
  [conn org :- common/Org]
  {:pre [(db-common/conn? conn)]}
  (db-common/create-resource conn table-name org (db-common/current-timestamp)))

(schema/defn ^:always-validate get-org :- (schema/maybe common/Org)
  "Given the slug of the org, return the org, or return nil if it doesn't exist."
  [conn slug]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? slug)]}
  (db-common/read-resource conn table-name slug))

(schema/defn ^:always-validate uuid-for :- (schema/maybe lib-schema/UniqueID)
  "Given an org slug, return the UUID of the org, or nil if it doesn't exist."
  [conn slug]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? slug)]}
  (:uuid (get-org conn slug)))

(schema/defn ^:always-validate update-org! :- (schema/maybe common/Org)
  "
  Given the slug of the org and an updated org property map, update the org and return the updated org on success.

  Throws an exception if the merge of the prior org and the updated org property map doesn't conform
  to the common/Org schema.
  
  NOTE: doesn't update admins, see: `add-admin`, `remove-admin`
  NOTE: doesn't handle case of slug change.
  "
  [conn slug org]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? slug)
         (map? org)]}
  (if-let [original-org (get-org conn slug)]
    (let [updated-org (merge original-org (clean org))]
      (schema/validate common/Org updated-org)
      (db-common/update-resource conn table-name primary-key original-org updated-org))))

(schema/defn ^:always-validate put-org! :- common/Org
  "
  Given the slug of the org and an org property map, create or update the org
  and return `true` on success.

  NOTE: doesn't update admins, see: `add-admin`, `remove-admin`
  NOTE: doesn't handle case of slug change.
  "
  [conn slug org user :- common/User]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? slug)
         (map? org)]}
  (if (get-org conn slug)
    (update-org! conn slug org)
    (create-org! conn (->org slug org user))))

(defn delete-org!
  "Given the slug of the org, delete it and all its boards, entries, and updates and return `true` on success."
  [conn slug]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? slug)]}
  (if-let [uuid (:uuid (get-org conn slug))]
    
    (do
      ;; Updates
      (try
        (db-common/delete-resource conn common/update-table-name :org-uuid uuid)
        (catch java.lang.RuntimeException e)) ; it's OK if there are no updates to delete
      ;; Entries
      (try
        (db-common/delete-resource conn common/entry-table-name :org-uuid uuid)
        (catch java.lang.RuntimeException e)) ; it's OK if there are no entries to delete
      ;; Boards
      (try
        (db-common/delete-resource conn common/board-table-name :org-uuid uuid)
        (catch java.lang.RuntimeException e)) ; it's OK if there are no boards to delete
      ;; The org itself
      (db-common/delete-resource conn table-name slug))
    
    false)) ; it's OK if there is no org to delete

;; ----- Collection of orgs -----

(defn list-orgs
  "
  Return a sequence of maps with slugs, UUIDs and names, sorted by slug.
  
  NOTE: if additional-keys are supplied, they will be included in the map, and only orgs
  containing those keys will be returned.
  "
  ([conn] (list-orgs conn []))

  ([conn additional-keys]
  {:pre [(db-common/conn? conn)
         (sequential? additional-keys)
         (every? #(or (string? %) (keyword? %)) additional-keys)]}
  (->> (into [primary-key "uuid" "name"] additional-keys)
    (db-common/read-resources conn table-name)
    (sort-by primary-key)
    vec)))

(defn get-orgs-by-index
  "
  Given the name of a secondary index and a value, retrieve all matching orgs.

  Secondary indexes:
  `uuid`
  `team-id`
  `admins`
  "
  [conn index-key v]
  {:pre [(db-common/conn? conn)
         (string? index-key)]}
  (db-common/read-resources conn table-name index-key v))

(defn get-orgs-by-teams
  "
  Get orgs by a sequence of team-id's, returning `slug` and `name`. 
  
  Additional fields can be optionally specified.
  "
  ([conn team-ids] (get-orgs-by-teams conn team-ids []))

  ([conn team-ids additional-fields]
  {:pre [(db-common/conn? conn)
         (schema/validate [lib-schema/UniqueID] team-ids)
         (sequential? additional-fields)
         (every? #(or (string? %) (keyword? %)) additional-fields)]}
    (db-common/read-resources conn table-name :team-id team-ids (concat [:slug :name] additional-fields))))

(defn get-orgs-by-team
  "
  Get orgs by a team-id, returning `slug` and `name`. 
  
  Additional fields can be optionally specified.
  "
  ([conn team-id] (get-orgs-by-team conn team-id []))
  
  ([conn team-id additional-fields]
    {:pre [(schema/validate lib-schema/UniqueID team-id)]}
  (get-orgs-by-teams conn [team-id] additional-fields)))

;; ----- Armageddon -----

(defn delete-all-orgs!
  "Use with caution! Failure can result in partial deletes. Returns `true` if successful."
  [conn]
  {:pre [(db-common/conn? conn)]}
  ;; Delete all udpates, entries, boards and orgs
  (db-common/delete-all-resources! conn common/update-table-name)
  (db-common/delete-all-resources! conn common/entry-table-name)
  (db-common/delete-all-resources! conn common/board-table-name)
  (db-common/delete-all-resources! conn table-name))