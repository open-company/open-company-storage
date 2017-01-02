(ns oc.api.resources.org
  (:require [clojure.string :as s]
            [clojure.walk :refer (keywordize-keys)]
            [schema.core :as schema]
            [oc.lib.slugify :as slug]
            [oc.lib.rethinkdb.common :as db-common]
            [oc.api.resources.common :as common]))

;; ----- RethinkDB metadata -----

(def table-name common/org-table-name)
(def primary-key :slug)

;; ----- Metadata -----

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{:slug :org-id :admins})

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
  "Return all slugs which are in use as a set."
  [conn]
  (into reserved-slugs (map :slug (list-orgs conn))))

(defn slug-available?
  "Return true if the slug is not used by any org in the database."
  [conn slug]
  (not (contains? (taken-slugs conn) slug)))

;; ----- Org CRUD -----

(defn get-org
  "Given the slug of the org, retrieve it from the database, or return nil if it doesn't exist."
  [conn slug]
  {:pre [(string? slug)]}
  (db-common/read-resource conn table-name slug))

(schema/defn ^:always-validate ->org :- common/Org
  "Take a minimal map describing an org and a user and an optional slug and 'fill the blanks' with any missing properties."
  ([org-props user]
  (->org org-props user (or (:slug org-props) (slug/slugify (:name org-props)))))

  ([org-props user slug]
  {:pre [(map? org-props)
         (map? user)
         (string? (:user-id user))
         (not (s/blank? (:user-id user)))
         (sequential? (:teams user))
         (every? string? (:teams user))
         (every? #(not (s/blank? %)) (:teams user))
         (string? slug)
         (slug/valid-slug? slug)]}
  (let [ts (db-common/current-timestamp)]
    (-> org-props
        keywordize-keys
        clean
        (assoc :slug slug)
        (assoc :uuid (db-common/unique-id))
        (assoc :team-id (first (:teams user))) ; TODO: how do we decide which auth-id to create the org with?
        (assoc :admins [(:user-id user)])
        (update :currency #(or % "USD"))
        (assoc :author (common/author-for-user user))
        (assoc :created-at ts)
        (assoc :updated-at ts)))))

(schema/defn ^:always-validate create-org!
  "Create an org document in RethinkDB."
  [conn org :- common/Org]
  (db-common/create-resource conn table-name org (db-common/current-timestamp)))

(defn delete-org!
  "Given the slug of the org, delete it and all its dashboards and return `true` on success."
  [conn slug]
  {:pre [(string? slug)]}
  ; (try
  ;   (common/delete-resource conn common/stakeholder-update-table-name :company-slug slug)
  ;   (catch java.lang.RuntimeException e)) ; it's OK if there are no stakeholder updates to delete
  ; (try
  ;   (common/delete-resource conn common/section-table-name :company-slug slug)
  ;   (catch java.lang.RuntimeException e)) ; it's OK if there are no sections to delete
  (try
    (db-common/delete-resource conn table-name slug)
    (catch java.lang.RuntimeException e))) ; it's OK if there is no org to delete

;; ----- Collection of orgs -----

(defn list-orgs
  "
  Return a sequence of maps with slugs, UUIDs and names, sorted by slug.
  Note: if additional-keys are supplied, they will be included in the map, and only orgs
  containing those keys will be returned.
  "
  ([conn] (list-orgs conn []))
  ([conn additional-keys]
    (->> (into [primary-key "uuid" "name"] additional-keys)
      (db-common/read-resources conn table-name)
      (sort-by primary-key)
      vec)))

;; ----- Armageddon -----

(defn delete-all-orgs!
  "Use with caution! Failure can result in partial deletes. Returns `true` if successful."
  [conn]
  ;; Delete all stakeholder udpates, sections and all companies
  ; (common/delete-all-resources! conn common/stakeholder-update-table-name)
  ; (common/delete-all-resources! conn common/section-table-name)
  (db-common/delete-all-resources! conn table-name))