(ns open-company.resources.company
  (:require [clojure.string :as s]
            [defun :refer (defun defun-)]
            [rethinkdb.query :as r]
            [open-company.lib.slugify :as slug]
            [open-company.config :as c]
            [open-company.resources.common :as common]))

;; ----- RethinkDB metadata -----

(def table-name common/company-table-name)
(def primary-key :slug)

;; ----- Metadata -----

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{:slug})

;; ----- Utility functions -----

(defn- clean
  "Remove any reserved properties from the company."
  [company]
  (apply dissoc (common/clean company) reserved-properties))

(defn- sections-for
  "Add or replace the :sections vector in the company with the sections this company map contains."
  [company]
  (assoc company :sections
    (vec (clojure.set/intersection common/sections (set (map name (keys company)))))))

(defn notes-for
  "
  Return a sequence of the sections that do have notes in the form:

  [[:section-name :notes] [:section-name :notes]]
  "
  [company]
  (let [possible-sections-with-notes
        (clojure.set/intersection common/notes-sections
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
  ([_ :guard #(or (not (string? (:name %))) (s/blank? (:name %)))] :invalid-name)
  ([_] true))

;; ----- Company CRUD -----

(defn get-company
  "Given the slug of the company, retrieve it from the database, or return nil if it doesn't exist."
  [slug]
  (common/read-resource table-name slug))

(defun create-company
  "Given the company property map, create the company, returning the property map for the resource or `false`.
  If you get a false response and aren't sure why, use the `valid-company` function to get a reason keyword.
  TODO: author is hard-coded, how will this be passed in from API's auth?
  TODO: what to use for author when using Clojure API?"

  ([company] (create-company (common/current-timestamp)))
  
  ;; not a map
  ([_company :guard #(not (map? %)) timestamp] false)
  
  ;; potentially a valid company
  ([company timestamp]
    (let [company-slug (:slug company)
          clean-company (clean company)
          slugged-company (if company-slug
                            (assoc clean-company :slug company-slug)
                            (assoc clean-company :slug (slug/slugify (:name company))))
          company-with-sections (sections-for slugged-company) ;; add/replace the :sections property
          company-with-revision-author (author-for common/stuart
                                        (:sections company-with-sections)
                                        company-with-sections) ;; add/replace the :author
          final-company (updated-for timestamp (:sections company-with-revision-author) company-with-revision-author)]
      (if (true? (valid-company final-company))
        (do
          ;; create the sections
          (doseq [section-name (:sections final-company)]
            (let [section (get final-company (keyword section-name))
                  section-with-company (assoc section :company-slug (:slug final-company))
                  final-section (assoc section-with-company :section-name section-name)]
              (common/create-resource common/section-table-name final-section timestamp)))
          ;; create the company
          (common/create-resource table-name final-company timestamp))
        false))))

(defn update-company
  "
  Given an updated company property map, update the company and return `true` on success.

  TODO: company :sections update if section is being added
  TODO: handle case of slug change.
  "
  [slug company]
  (if-let [original-company (get-company slug)]
    (let [updated-company (-> company
                            (clean)
                            (sections-for)
                            (assoc :slug slug))]
      (common/update-resource table-name primary-key original-company updated-company))))

(defun put-company
  "Given the slug of the company and a company property map, create or update the company
  and return `true` on success.
  TODO: handle case of slug mismatch between URL and properties.
  TODO: handle case of slug change."
  ([slug :guard get-company company] (update-company slug company))
  ([_slug company] (create-company company)))

(defn delete-company
  "Given the slug of the company, delete it and all its sections and return `true` on success."
  [slug]
  (common/delete-resource common/section-table-name :company-slug slug)
  (common/delete-resource table-name slug))

;; ----- Company revisions -----

(defn list-revisions
  "Given the slug of the company retrieve the timestamps and author of the revisions from the database."
  [slug]
  (common/updated-at-order
    (common/read-resources common/section-table-name "company-slug" slug [:updated-at :author])))

;; ----- Collection of companies -----

(defn list-companies
  "Return a sequence of company property maps with slugs and names, sorted by slug."
  []
  (vec (sort-by primary-key
    (with-open [conn (apply r/connect c/db-options)]
      (-> (r/table table-name)
        (r/with-fields [primary-key "name"])
        (r/run conn))))))

;; ----- Armageddon -----

(defn delete-all-companies!
  "Use with caution! Failure can result in partial deletes of sections and companies. Returns `true` if successful."
  []
  ;; Delete all reports and all companies
  (common/delete-all-resources! common/section-table-name)
  (common/delete-all-resources! table-name))