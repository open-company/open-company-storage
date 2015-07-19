(ns open-company.resources.common
  "Resources are any thing stored in the open company platform: companies, reports"
  (:require [clj-time.format :as format]
            [clj-time.core :as time]
            [rethinkdb.query :as r]
            [open-company.config :as c]))

;; ----- ISO 8601 timestamp -----

(def timestamp-format (format/formatters :date-time-no-ms))

(defn- current-timestamp
  "ISO 8601 string timestamp for the current time."
  []
  (format/unparse timestamp-format (time/now)))

;; ----- Resource CRUD funcitons -----

(defn create-resource
  "Create a resource in the DB, returning the property map for the resource or `false`."
  [table-name resource]
  (let [timestamp (current-timestamp)
        timed-resource (merge resource {
          :created-at timestamp
          :updated-at timestamp})]
  (if (< 0 (:inserted
    (with-open [conn (apply r/connect c/db-options)]
      (-> (r/table table-name)
        (r/insert timed-resource)
        (r/run conn)))))
    timed-resource
    false)))