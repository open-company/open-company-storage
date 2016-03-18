(ns open-company.resources.company
  (:require [medley.core :as med]
            [schema.core :as schema]
            [defun :refer (defun)]
            [open-company.lib.slugify :as slug]
            [open-company.resources.common :as common]))

;; ----- RethinkDB metadata -----

(def table-name common/company-table-name)
(def primary-key :slug)

;; ----- Metadata -----

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{:slug :org-id :categories})

;; ----- Utility functions -----

(defn- clean
  "Remove any reserved properties from the company."
  [company]
  (apply dissoc (common/clean company) reserved-properties))

(defn- categories-for
  "Add the :categories vector in the company with the definitive initial list of categories."
  [company]
  (assoc company :categories common/category-names))

(defn- section-list
  "Return the set of section names that are contained in the provided company."
  [company]
  (set (clojure.set/intersection common/section-names (set (keys company)))))

(defn- sections-for
  "Add a :sections key to given company containing category->ordered-sections mapping
  Only add sections to the ordered-sections list that are used in the company map"
  [company]
  (let [seclist (section-list company)]
    (->> (for [[cat sects] common/category-section-tree]
          [cat (vec (filter (set seclist) sects))])
      (into {})
      (assoc company :sections))))

(defn- remove-sections
  "Remove any sections from the company that are not in the :sections property"
  [company]
  (let [sections (set (map keyword (flatten (vals (:sections company)))))]
    (apply dissoc company (clojure.set/difference (section-list company) sections))))

;; ----- Company Slug -----

(declare list-companies)
(defn taken-slugs
  "Return all slugs which are in use as a set."
  []
  (set (map :slug (list-companies))))

(defn slug-available?
  "Return true if the slug is not used by any company in the database."
  [slug]
  (not (contains? (taken-slugs) slug)))

;; ----- Validations -----

(defn valid-company [slug company]
  (schema/check common/Company (assoc company :slug slug)))

;; ----- Company CRUD -----

(defn get-company
  "Given the slug of the company, retrieve it from the database, or return nil if it doesn't exist."
  [slug]
  {:pre [(string? slug)]}
  (common/read-resource table-name slug))

(defn- core-placeholder-sections
  "Return a map of section-name -> section containing just the core
   placeholder sections in sections.json"
  [company-slug]
  (reduce (fn [s sec]
            (assoc s
                   (:section-name sec)
                   (-> sec
                       (assoc :company-slug company-slug)
                       (assoc :placeholder true)
                       (dissoc :name))))
          {}
          (filter :core common/sections)))

(defn add-core-placeholder-sections
  "Add the placeholder for any core sections that are missing from the provided company."
  [company]
  (-> (core-placeholder-sections (:slug company))
      (merge company)
      (sections-for)))

(defn add-placeholder-sections
  "Add the canonical placeholder section for any section names listed in the :sections property of the company,
  but that aren't present in the company."
  [company]
  (let [sections (-> company :sections vals flatten vec)
        missing-section-names (map keyword (filter #(nil? (company (keyword %))) sections))
        missing-sections (map common/section-by-name missing-section-names)
        ; add :placeholder flag and remove :core flag
        placeholder-sections (map #(dissoc % :core) (map #(assoc % :placeholder true) missing-sections))]
    (merge company (zipmap missing-section-names placeholder-sections))))

(defn add-prior-sections
  "Add the most recent section revision (if there are any) for any section names listed in the :sections property
  of the company, but that aren't present in the company."
  [company]
  (let [slug (:slug company)
        sections (-> company :sections vals flatten vec)
        ; names of sections in the sections property, but not present in company
        missing-section-names (map keyword (filter #(nil? (company (keyword %))) sections))
        ; IDs of the most recent prior section of those missing sections (where found)
        prior-section-ids (map :id (flatten (remove nil? (map #(common/read-resources-in-order
                                                common/section-table-name
                                                "company-slug-section-name"
                                                [slug %]
                                                [:id]) missing-section-names))))
        prior-sections (map #(common/read-resource common/section-table-name %) prior-section-ids)
        prior-section-names (map #(keyword (:section-name %)) prior-sections)]
    (merge company (zipmap prior-section-names (map #(dissoc % :section-name) prior-sections)))))

(defn- real-sections
  "Select all non-placeholder sections from a company map"
  [company]
  (med/remove-vals :placeholder (select-keys company common/section-names)))

(defn- complete-real-sections
  "For each non-placeholder section in the company add an author,
   the company's slug, and the section's name, description and image.
   Section image and description are from the canonical section definitions."
  [company user]
  (let [rs (real-sections company)
        add-info (fn [[section-name section-data]]
                   [section-name (-> section-data
                          (assoc :author (common/author-for-user user))
                          (assoc :company-slug (:slug company))
                          (assoc :section-name section-name)
                          (assoc :description (:description (common/section-by-name section-name)))
                          (assoc :image (:image (common/section-by-name section-name))))])]
    (merge company (into {} (map add-info rs)))))

(schema/defn ->company :- common/Company
  "Take a minimal map describing a company and a user and 'fill the blanks'"
  [company-props user]
  (let [slug (or (:slug company-props) (slug/find-available-slug (:name company-props) (taken-slugs)))]
    (-> company-props
        (assoc :slug slug)
        (update :currency #(or % "USD"))
        (assoc :org-id (:org-id user))
        (complete-real-sections user)
        (categories-for)
        (sections-for))))

(schema/defn ^:private add-updated-at
  "Add `:updated-at` key with `ts` as value to a given section.
   If the section has a `:notes` key also add the timestamp there."
  [{:keys [section-name] :as section} :- common/Section ts]
  (let [notes-section? (contains? common/notes-sections section-name)]
    (cond-> (assoc section :updated-at ts)
      (and notes-section? (:notes section)) (assoc-in [:notes :updated-at] ts))))

(schema/defn ^:always-validate create-company!
  "Create a company document in RethinkDB. Add `updated-at` keys where necessary."
  [company :- common/Company]
  (let [real-sections (real-sections company)
        ts (common/current-timestamp)
        rs-w-ts (med/map-vals #(add-updated-at % ts) real-sections)]
    (doseq [[_ section] real-sections]
      (common/create-resource common/section-table-name section ts))
    (common/create-resource table-name (merge company rs-w-ts) ts)))

(defn update-company
  "
  Given an updated company property map, update the company and return `true` on success.

  TODO: handle case of slug change.
  TODO: handle new and updated sections.
  "
  [slug company]
  {:pre [(string? slug) (map? company)]}
  (if-let [original-company (get-company slug)]
    (let [org-id (:org-id company)
          updated-company (-> company
                            (clean)
                            (categories-for)
                            (remove-sections)
                            (assoc :org-id org-id)
                            (assoc :slug slug))]
      (common/update-resource table-name primary-key original-company updated-company))))

(defun put-company
  "Given the slug of the company and a company property map, create or update the company
  and return `true` on success.
  TODO: handle case of slug mismatch between URL and properties.
  TODO: handle case of slug change."
  ([slug :guard get-company company _user] (update-company slug company))
  ([_slug company user] (create-company! (->company company user))))

(defn delete-company
  "Given the slug of the company, delete it and all its sections and return `true` on success."
  [slug]
  {:pre [(string? slug)]}
  (try
    (common/delete-resource common/section-table-name :company-slug slug)
    (catch java.lang.RuntimeException e)) ; it's OK if there are no sections to delete
  (try
    (common/delete-resource table-name slug)
    (catch java.lang.RuntimeException e))) ; it's OK if there is no company to delete

;; ----- Company revisions -----

(defn list-revisions
  "Given the slug of the company retrieve the timestamps and author of the revisions from the database."
  [slug]
  {:pre [(string? slug)]}
  (common/updated-at-order
    (common/read-resources common/section-table-name "company-slug" slug [:updated-at :author])))

;; ----- Collection of companies -----

(defn list-companies
  "Return a sequence of company property maps with slugs and names, sorted by slug.
  Note: if additional-keys are supplied only documents containing those keys will be returned"
  ([] (list-companies []))
  ([additional-keys]
    (->> (into [primary-key "name"] additional-keys)
      (common/read-resources table-name)
      (sort-by primary-key)
      vec)))

(defn get-companies-by-index
  "Given the name of a secondary index and a value, retrieve all matching companies"
  ;; e.g.: (get-companies-by-index "org-id" "slack:T06SBMH60")
  [index-key v]
  {:pre [(string? index-key)]}
  (common/read-resources table-name index-key v))

;; ----- Armageddon -----

(defn delete-all-companies!
  "Use with caution! Failure can result in partial deletes of sections and companies. Returns `true` if successful."
  []
  ;; Delete all sections and all companies
  (common/delete-all-resources! common/section-table-name)
  (common/delete-all-resources! table-name))