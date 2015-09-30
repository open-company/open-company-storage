(ns open-company.resources.section
  (:require [clojure.string :as s]
            [defun :refer (defun)]
            [rethinkdb.query :as r]
            [open-company.config :as c]
            [open-company.resources.common :as common]
            [open-company.resources.company :as company]))

(def table-name :sections)
(def primary-key :id)

;; ----- Validations -----

;; ----- Section CRUD -----

(defn get-section
  "Given the id of a section, retrieve the section from the database or return nil."
  [id] (common/read-resource table-name id))

;; ----- Collection of sections -----

(defn list-sections
  "Given the slug of the company, an optional section name, and an optional specific updated-at timestamp,
  retrieve the sections from the database."
  ([company-slug] (common/read-resources table-name "company-slug" company-slug))
  ([company-slug section-name]
    (common/read-resources table-name "company-slug-section-name" [company-slug section-name]))
  ([slug section-name updated-at]
    (common/read-resources table-name "company-slug-section-name-updated-at" [slug section-name updated-at])))

(defn delete-all-sections!
  "Use with caution! Failure can result in partial deletes of just some sections. Returns `true` if successful."
  [] (common/delete-all-resources! table-name))