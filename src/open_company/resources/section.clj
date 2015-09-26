(ns open-company.resources.section
  (:require [clojure.string :as s]
            [defun :refer (defun)]
            [rethinkdb.query :as r]
            [open-company.config :as c]
            [open-company.resources.common :as common]
            [open-company.resources.company :as company]))

(def ^:private table-name :sections)
(def ^:private primary-key :id)

;; ----- Validations -----

;; ----- Section CRUD -----

(defn get-section
  "Given the slug of the company, a section name, and a specific updated-at timestamp, retrieve the sections from the database.
  TODO:"
  ([id] (common/read-resource table-name id))
  ([slug section updated-at]
    nil))

;; ----- Collection of sections -----

(defn list-sections
  "Given the slug of the company, a section name, and an optional since date,
  return a sequence of section hashes with `:author` and `:updated-at`, ordered descending by update date."
  ([company-slug section-name]
    (vec (with-open [conn (apply r/connect c/db-options)]
      (-> (r/table table-name)
        (r/get-all [[company-slug section-name]] {:index :company-slug-section-name})
        (r/with-fields ["updated-at" "author"])
        (r/run conn)))))
  ([slug section-name since] nil))

(defn section-revision-count
  "Given slug of a company, and an optional section name, return how many section revisions exist for the section."
  ([company-slug]
    (with-open [conn (apply r/connect c/db-options)]
      (-> (r/table table-name)
        (r/get-all [company-slug] {:index :company-slug})
        (r/count)
        (r/run conn))))
  ([company-slug section-name]
    (with-open [conn (apply r/connect c/db-options)]
      (-> (r/table table-name)
        (r/get-all [(s/join [company-slug section-name] "-")] {:index :company-slug-section-name})
        (r/count)
        (r/run conn)))))

(defn delete-all-sections!
  "Use with caution! Failure can result in partial deletes of just some sections. Returns `true` if successful."
  ([]
    (common/delete-all-resources! table-name))
  ([company-slug]
    )
  ([company-slug section-name]
    )
  ([company-slug section-name since]
    ))