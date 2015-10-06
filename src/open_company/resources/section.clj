(ns open-company.resources.section
  (:require [clojure.string :as s]
            [defun :refer (defun)]
            [rethinkdb.query :as r]
            [open-company.config :as c]
            [open-company.resources.common :as common]
            [open-company.resources.company :as company]))

;; ----- RethinkDB metadata -----

(def table-name common/section-table-name)
(def primary-key :id)

;; ----- Metadata -----

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{:id :company-slug :section-name})

;; ----- Utility functions -----

(defn- clean
  "Remove any reserved properties from the section."
  [section]
  (apply dissoc (common/clean section) reserved-properties))

;; ----- Validations -----

(defun valid-section
  "
  Validate the company, and section name of the section
  returning `:bad-company`, `:bad-section-name` respectively.
  
  TODO: take company slug and section name separately and validate they match
  TODO: Use prismatic schema to validate section properties.
  "
  ; ([company-slug section-name section :guard #(or (not (:company-slug %)) (not (company/get-company (:company-slug %))))] :bad-company)
  ; ([company-slug section-name section :guard #(not (common/sections (:section-name %)))] :bad-section-name)
  ([_ _ _] true))

;; ----- Section CRUD -----

(defn get-section
  "Given the id of a section, retrieve the section from the database or return nil."
  [id] (common/read-resource table-name id))

(defun update-section
  "
  Given the company slug, section name, and section property map, create a new section revision,
  updating the company with a new revision and returning the property map for the resource or `false`.
  
  If you get a false response and aren't sure why, use the `valid-section` function to get a reason keyword.
  
  TODO: :author and :updated-at for commentary if it's changed
  TODO: author is hard-coded, how will this be passed in from API's auth?
  TODO: what to use for author when using Clojure API?
  "
  ([company-slug section-name :guard #(not (keyword? %)) section]
  (update-section company-slug (keyword section-name) section))

  ([company-slug section-name section]
  (let [timestamp (common/current-timestamp)
        original-company (company/get-company company-slug)
        clean-section (clean section)
        updated-section (-> clean-section
          (assoc :company-slug company-slug)
          (assoc :section-name section-name)
          (assoc :author common/stuart)
          (assoc :updated-at timestamp))
        updated-company (assoc original-company section-name (-> updated-section
          (dissoc :company-slug)
          (dissoc :section-name)))]
    (if (true? (valid-section company-slug section-name updated-section))
      (do
        ;; update the company
        (company/update-company company-slug updated-company)
        ;; create the new section revision
        (common/create-resource table-name updated-section timestamp))
      false))))

;; ----- Collection of sections -----

(defn list-sections
  "Given the slug of the company a section name, and an optional specific updated-at timestamp,
  retrieve the sections from the database."
  ([company-slug section-name]
  (common/updated-at-order (common/read-resources table-name "company-slug-section-name" [company-slug section-name])))
  
  ([slug section-name updated-at]
  (common/updated-at-order (common/read-resources table-name "company-slug-section-name-updated-at" [slug section-name updated-at]))))

(defn list-revisions
  "Given the slug of the company, and a section name retrieve the timestamps of the section revisions from the database."
  [company-slug section-name]
  (common/updated-at-order
    (common/read-resources table-name "company-slug-section-name" [company-slug section-name] [:updated-at :author])))

(defn delete-all-sections!
  "Use with caution! Failure can result in partial deletes of just some sections. Returns `true` if successful."
  [] (common/delete-all-resources! table-name))