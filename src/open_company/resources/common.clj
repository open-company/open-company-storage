(ns open-company.resources.common
  "Resources are any thing stored in the open company platform: companies, reports"
  (:require [clj-time.format :as format]))

;; ----- ISO 8601 timestamp -----

(def timestamp-format (format/formatters :date-time-no-ms))