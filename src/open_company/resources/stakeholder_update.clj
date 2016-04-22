(ns open-company.resources.stakeholder-update
  (:require [schema.core :as schema]
            [if-let.core :refer (if-let*)]
            [defun :refer (defun-)]
            [open-company.lib.slugify :as slug]
            [open-company.resources.common :as common]
            [open-company.resources.company :as company]
            [open-company.resources.section :as section]))

;; ----- RethinkDB metadata -----

(def table-name common/stakeholder-update-table-name)
(def primary-key :id)

;; ----- Utility functions -----

(defn- slug-for
  "Create a slug for the stakeholder update from the slugified title and a short UUID."
  [title]
  (str (slug/slugify title) "-" (subs (str (java.util.UUID/randomUUID)) 0 4)))

(defun- sections-for
  "Recursive function to get each specified section and add it to the stakeholder update."
  ([su-props conn] (sections-for su-props conn (:sections su-props)))
  
  ([su-props _conn sections :guard empty?] su-props)
  
  ([su-props conn sections]
    (let [company-slug (:company-slug su-props)
          section-name (keyword (first sections))
          as-of (:created-at su-props)
          section (section/get-section conn company-slug section-name as-of)
          clean-section (dissoc section :section-name :id :company-slug)]
      (recur (assoc su-props section-name clean-section) conn (rest sections)))))
  
;; ----- Stakeholder update CRD (Create, Read, Delete) -----

(schema/defn ->stakeholder-update :- common/Stakeholder-update
  "Take a minimal map describing a stakeholder-update and a user and 'fill the blanks'"
  
  ([conn company su-props user] (->stakeholder-update conn company su-props (common/current-timestamp) user))
  
  ([conn company {:keys [title intro outro sections] :as su-props} timestamp user]
  {:pre [(map? conn)
         (string? title)
         (or (map? intro) (nil? intro))
         (or (map? outro) (nil? outro))
         (sequential? sections)
         (map? user)]
   :post [map?]} ; title and sections required
  (-> su-props
    (assoc :slug (slug-for title))
    (assoc :company-slug (:slug company))
    (merge (select-keys company [:name :description :logo :logo-width :logo-height])) ; freeze some props of company
    (update :title #(or % ""))
    (update :intro #(or % {:body ""}))
    (update :outro #(or % {:body ""}))
    (assoc :author (common/author-for-user user))
    (assoc :created-at timestamp)
    (sections-for conn))))

(schema/defn ^:always-validate create-stakeholder-update!
  "Create a stakeholder-update document in RethinkDB."
  [conn stakeholder-update :- common/Stakeholder-update]
  (if-let* [company (company/get-company conn (:company-slug stakeholder-update))
            sections (get-in company [:stakeholder-update :sections])
            cleared-update (assoc common/empty-stakeholder-update :sections sections)
            updated-company (assoc company :stakeholder-update cleared-update)
            ;; create the stakeholder update 
            update (common/create-resource conn table-name stakeholder-update (:created-at stakeholder-update))
            ;; clear out the new/pending/live stakeholder update fields from the company (except for `:sections`)
            _company (company/update-company conn (:slug company) updated-company)]
    update
    false))

(defn get-stakeholder-update
  "
  Given the company slug, and stakeholder update slug, retrieve the stakeholder update from
  the database. Return nil if the specified stakeholder doesn't exist.
  "
  ([conn company-slug slug]
  {:pre [(map? conn) (string? company-slug) (string? slug)]
   :post [(or (map? %) (nil? %))]}
  (first (common/read-resources conn table-name "company-slug-slug" [company-slug slug]))))

(defn delete-stakeholder-update
  "Given the slug of the company and the stakeholder update, delete the stakeholder update and return `true` on success."
  [conn company-slug slug]
  {:pre [(string? company-slug) (string? slug)]
   :post [(or (true? %) (false? %))]}
  (try
    (common/delete-resource conn table-name "company-slug-slug" [company-slug slug])
    (catch java.lang.RuntimeException e
      false))) ; it's OK if there is no stakeholder updates to delete

;; ----- Collection of stakeholder updates -----

(defn list-stakeholder-updates
  "
  Return a sequence of maps for each stakeholder update for the specified company
  with slug and created-at, sorted by created-at.

  Note: if additional-keys are supplied only documents containing those keys will be returned
  "
  ([conn company-slug] (list-stakeholder-updates conn company-slug []))
  ([conn company-slug additional-keys]
  {:pre [(map? conn) (string? company-slug) (sequential? additional-keys)]
   :post [sequential?]}
  (->> (into ["slug" "company-slug" "created-at"] additional-keys)
    (common/read-resources conn table-name "company-slug" company-slug)
    (sort-by :created-at)
    vec)))

;; ----- Armageddon -----

(defn delete-all-stakeholder-updates!
  "Use with caution! Failure can result in partial deletes of just some sections. Returns `true` if successful."
  [conn]
  (common/delete-all-resources! conn table-name))