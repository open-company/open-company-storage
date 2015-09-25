(ns open-company.resources.section
  (:require [clojure.string :as s]
            [defun :refer (defun)]
            [rethinkdb.query :as r]
            [open-company.config :as c]
            [open-company.resources.common :as common]
            [open-company.resources.company :as company]))

(def ^:private table-name :sections)
(def ^:private primary-key :slug)

;; ----- Validations -----

;; ----- Section CRUD -----
