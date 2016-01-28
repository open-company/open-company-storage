(ns open-company.resources.company
  (:require [clojure.string :as string]
            [clojure.set :as cset]
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
  (assoc company :categories common/category-names))

(defn- section-list
  "Return the set of section names that are contained in the provided company."
  [company]
  (set (cset/intersection common/sections (set (keys company)))))

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

(defn- notes-for
  "
  Return a sequence of the sections that do have notes in the form:

  [[:section-name :notes] [:section-name :notes]]
  "
  [company]
  (let [possible-sections-with-notes
        (cset/intersection common/notes-sections
          (set (:sections company)))
        sections-with-notes
          (filter #(get-in company [(keyword %) :notes])
            possible-sections-with-notes)]
    (vec (map #(vec [(keyword %) :notes]) sections-with-notes))))

(defun- author-for
  "Add or replace the :author map for each specified section with the specified author for this revision."
  ;; determine sections with notes
  ([author sections company] (author-for author sections (notes-for company) company))
  ;; all done!
  ([_author _sections :guard empty? _notes-sections :guard empty? company] company)
  ;; replace the :author in the section notes and recurse
  ([author _sections :guard empty? notes-sections company]
    (author-for author [] (rest notes-sections)
      (assoc-in company (flatten [(first notes-sections) :author]) author)))
  ;; replace the :author in the section and recurse
  ([author sections notes-sections company]
    (author-for author (rest sections) notes-sections
      (assoc-in company [(keyword (first sections)) :author] author))))

(defun- updated-for
  "Add or replace the :updated-at for each specified section with the specified timestamp for this revision."
  ;; determine sections with notes
  ([timestamp sections company] (updated-for timestamp sections (notes-for company) company))
  ;; all done!
  ([_timestamp _sections :guard empty? _notes-sections :guard empty? company] company)
  ;; replace the :updated-at in the section notes and recurse
  ([timestamp _sections :guard empty? notes-sections company]
    (updated-for timestamp [] (rest notes-sections)
      (assoc-in company (flatten [(first notes-sections) :updated-at]) timestamp)))
  ;; replace the :updated-at in the section and recurse
  ([timestamp sections notes-sections company]
    (updated-for timestamp (rest sections) notes-sections
      (assoc-in company [(keyword (first sections)) :updated-at] timestamp))))

;; ----- Validations -----

(defun valid-company
  "Given the name and optionally the slug of a new company, and a map of the new company's properties,
  check if the everything is in order to create the new company.

  Ensures the company is provided as an associative data structure or returns `:invalid-map`.

  Ensures the name of the company is specified or returns `:invalid-name`.

  Ensures the slug is valid, or returns `:invalid-slug`.

  If everything is OK with the proposed new company, `true` is returned.

  TODO: Use prismatic schema to validate company properties."
  ([_ :guard #(not (map? %))] :invalid-map)
  ([_ :guard #(and (:slug %) (not (slug/valid-slug? (:slug %))))] :invalid-slug)
  ([_ :guard #(or (not (string? (:name %))) (string/blank? (:name %)))] :invalid-name)
  ([_] true))

;; ----- Company CRUD -----

(defn get-company
  "Given the slug of the company, retrieve it from the database, or return nil if it doesn't exist."
  [slug]
  {:pre [(string? slug)]}
  (common/read-resource table-name slug))

(defun create-company
  "Given the company property map, create the company, returning the property map for the resource or `false`.
  If you get a false response and aren't sure why, use the `valid-company` function to get a reason keyword."

  ([company user] (create-company company user (common/current-timestamp)))

  ;; not a map
  ([_company :guard #(not (map? %)) _user _timestamp] false)
  ([_company _user :guard #(not (map? %)) _timestamp] false)

  ;; no org-id
  ([_company _user :guard #(not (:org-id %)) _timestamp] false)

  ;; potentially a valid company
  ([company user timestamp]
    (let [company-slug (or (:slug company) (slug/slugify (:name company)))
          author (common/author-for-user user)
          interim-company (-> company
                            (clean) ; remove disallowed properties
                            (assoc :slug company-slug) ; store the slug
                            (assoc :org-id (:org-id user)) ; store the org
                            (categories-for) ; add/replace the :categories property
                            (sections-for)) ; add/replace the :sections property
          section-names (flatten (vals (:sections interim-company)))
          final-company (->> interim-company
                          (author-for author section-names) ; add/replace the :author in the sections
                          (updated-for timestamp section-names))] ; add/replace the :updated-at in the sections
      (if (true? (valid-company final-company))
        (do
          ;; create the each section in the DB
          (doseq [section-name section-names]
            (let [section (-> (get final-company (keyword section-name))
                              (assoc :company-slug (:slug final-company))
                              (assoc :section-name section-name))]
              (common/create-resource common/section-table-name section timestamp)))
          ;; create the company in the DB
          (common/create-resource table-name final-company timestamp))
        false))))

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
  ([_slug company user] (create-company company user)))

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
  "Return a sequence of company property maps with slugs and names, sorted by slug."
  []
  (vec (sort-by primary-key
    (common/read-resources table-name [primary-key "name"]))))

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