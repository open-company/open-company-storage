(ns open-company.resources.common
  "Resources are any thing stored in the open company platform: companies, reports"
  (:require [clj-time.format :refer (parse formatters unparse)]
            [clj-time.core :refer (now)]))

;; ----- ISO 8601 timestamp -----

(def timestamp-format (formatters :date-time-no-ms))