(ns open-company.resources.company
  (:require [clojure.string :as string]
            [clojure.set :as cset]
            [medley.core :as med]
            [schema.core :as s]
            [defun :refer (defun defun-)]
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
  (assoc company :categories (vec common/category-names)))

(defn- section-list
  "Return the set of section names that are contained in the provided company."
  [company]
  (set (cset/intersection common/section-names (set (keys company)))))

(defn sections-for
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
    (apply dissoc company (cset/difference (section-list company) sections))))

(defn sections-with
  "Add the provided section name to the end of the correct category, unless it's already in the category."
  [sections section-name]
  (if ((set (map keyword (flatten (vals sections)))) (keyword section-name))
    sections ; already contains the section-name
    (let [category-name (common/category-for section-name)] ; get the category for this section name
      (update-in sections [category-name] conj section-name)))) ; add the section name to the category

;; ----- Validations -----

(defun valid-company
  "Given the name and optionally the slug of a new company, and a map of the new company's properties,
  check if the everything is in order to create the new company.

  Ensures the company is provided as an associative data structure or returns `:invalid-map`.

  Ensures the name of the company is specified or returns `:invalid-name`.

  Ensures the slug is valid, or returns `:invalid-slug`.

  If everything is OK with the proposed new company, `true` is returned."
  ([_ :guard #(not (map? %))] :invalid-map)
  ([_ :guard #(and (:slug %) (not (slug/valid-slug? (:slug %))))] :invalid-slug)
  ([_ :guard #(or (not (string? (:name %))) (string/blank? (:name %)))] :invalid-name)
  ([_] true))


;; ----- Slug -----

(declare list-companies)
(defn taken-slugs []
  (set (map :slug (list-companies))))

(defn slug-available? [slug]
  (not (contains? (taken-slugs) slug)))

;; ----- Company CRUD -----

(defn get-company
  "Given the slug of the company, retrieve it from the database, or return nil if it doesn't exist."
  [slug]
  {:pre [(string? slug)]}
  (common/read-resource table-name slug))

(defn placeholder-sections [company-slug]
  (reduce (fn [s sec]
            (assoc s
                   (-> sec :name keyword)
                   (-> sec
                       (assoc :section-name (-> sec :name keyword))
                       (assoc :company-slug company-slug)
                       (assoc :placeholder true)
                       (dissoc :name))))
          {}
          (filter :core common/sections)))

(defn add-placeholder-sections [company]
  (-> (placeholder-sections (:slug company))
      (merge company)
      (sections-for)))

;; ----- Create saveable company doc ----

(defn real-sections [company]
  (med/remove-vals :placeholder (select-keys company common/section-names)))

(defn complete-real-sections
  [company user]
  (let [rs (real-sections company)
        add-info (fn [[k section-data]]
                   [k (-> section-data
                          (assoc :author (common/author-for-user user))
                          (assoc :company-slug (:slug company))
                          (assoc :section-name k))])]
    (merge company (into {} (map add-info rs)))))

(s/defn ->company :- common/Company
  [company-props user]
  (let [slug (or (:slug company-props) (slug/find-available-slug (:name company-props) (taken-slugs)))]
    (-> company-props
        (assoc :slug slug)
        (update :currency #(or % "USD"))
        (assoc :org-id (:org-id user))
        (complete-real-sections user)
        (categories-for)
        (sections-for))))

(s/defn add-updated-at
  [{:keys [section-name] :as section} :- common/Section ts]
  (let [notes-section? (contains? common/notes-sections section-name)]
    (cond-> (assoc section :updated-at ts)
      (and notes-section? (:notes section)) (assoc-in [:notes :updated-at] ts))))

(s/defn create-company!
  [company :- common/Company]
  (s/validate common/Company company) ; throw if invalid
  (let [real-sections     (real-sections company)
        ts                (common/current-timestamp)
        rs-w-ts           (med/map-vals #(add-updated-at % ts) real-sections)]
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
