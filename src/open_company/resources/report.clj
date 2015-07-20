(ns open-company.resources.report
  (:require [rethinkdb.query :as r]
            [open-company.config :as c]
            [open-company.resources.common :as common]
            [open-company.resources.company :as company]))

(def ^:private primary-key :symbol-year-period)

(defn- key-for
  ([report] (key-for (:symbol report) (:year report) (:period report)))
  ([ticker year period] (str ticker "-" year "-" period)))

(defn valid-period?
  "TBD"
  [period]
  true)

(defn valid-report
  "
  TBD. Use prismatic schema.
  "
  ([ticker year period] (valid-report ticker year period {}))
  ([ticker year period properties]
    true))

(defn get-report
  "Given the ticker symbol of the company and the year and period of the report,
  or the primary key, retrieve it from the database, or return nil if it doesn't exist."
  ([ticker year period] (get-report (key-for ticker year period)))
  ([report-key]
    (with-open [conn (apply r/connect c/db-options)]
      (first
        (-> (r/table "reports")
          (r/get-all [report-key] {:index primary-key})
          (r/run conn))))))

(defn create-report
  "Given the report property map, create the report returning the property map for the resource or `false`.
  Return `:bad-company` if the company for the report does not exist."
  [report]
  (if (company/get-company (:symbol report))
    (common/create-resource "reports" (assoc report primary-key (key-for report)))
    :bad-company))

(defn update-report
  "Given the an updated report property map, update the report and return `true` on success."
  [report]
  (if-let [original-report (get-report (:symbol report) (:year report) (:period report))]
    (common/update-resource "reports" original-report (assoc report primary-key (key-for report)))
    :bad-company))

(defn put-report
  "Given a report property map, create or update the report and return `true` on success."
  [report]
  (if (get-report (key-for report))
    (update-report report)
    (create-report report)))

(defn delete-report
  "Given the ticker symbol of the company and the year and period of the report, delete the report and return `true`."
  [ticker year period]
  (< 0 (:deleted
    (with-open [conn (apply r/connect c/db-options)]
      (-> (r/table "reports")
        (r/get-all [(key-for ticker year period)] {:index primary-key})
        (r/delete)
        (r/run conn))))))

(defn report-count
  "Given the ticker symbol of a company, return how many reports exist for the company."
  [ticker]
  (with-open [conn (apply r/connect c/db-options)]
    (-> (r/table "reports")
      (r/get-all [ticker] {:index "symbol"})
      (r/count)
      (r/run conn))))