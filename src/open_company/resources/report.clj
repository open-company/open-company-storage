(ns open-company.resources.report
  (:require [defun :refer (defun)]
            [rethinkdb.query :as r]
            [open-company.config :as c]
            [open-company.resources.common :as common]
            [open-company.resources.company :as company]))

(def ^:private primary-key :symbol-year-period)

(def periods #{"Q1" "Q2" "Q3" "Q4"
               "M1" "M2" "M3" "M4" "M5" "M6" "M7" "M8" "M9" "M10" "M11" "M12"})

(defn- key-for
  ([report] (key-for (:symbol report) (:year report) (:period report)))
  ([ticker year period] (str ticker "-" year "-" period)))

;; ----- Validations -----

(defun valid-year?
  "Return `true` if the specified period is valid, `false` if not."
  ([year :guard integer?] (and (> year 1900) (< year 3000)))
  ([year :guard string?] (valid-year? (Integer. (re-find  #"\d+" year)))))

(defn valid-period?
  "Return `true` if the specified period is valid, `false` if not."
  [period]
  (if (periods period) true false))

(defun valid-report
  "Validate the ticker symbol, year and period of the report
  returning `:bad-company`, `:bad-year` and `bad-period` respectively.
  TODO: Use prismatic schema to validate report properties."
  ([ticker year period] (valid-report ticker year period {}))
  ([_ year :guard #(not (valid-year? %)) _ _] :bad-year)
  ([_ _ period :guard #(not (valid-period? %)) _] :bad-period)
  ([ticker :guard #(not (company/get-company %)) _ _ _] :bad-company)
  ([_ _ _ _] true))

;; ----- Report CRUD -----

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

(defun create-report
  "Given the report property map, create the report returning the property map for the resource or `false`.
  Return `:bad-company` if the company for the report does not exist."
  ([report :guard #(company/get-company (:symbol %))]
    (common/create-resource "reports" (assoc report primary-key (key-for report))))
  ([_] :bad-company))

(defn update-report
  "Given the an updated report property map, update the report and return `true` on success."
  [report]
  (if-let [original-report (get-report (:symbol report) (:year report) (:period report))]
    (common/update-resource "reports" original-report (assoc report primary-key (key-for report)))
    :bad-company))

(defun put-report
  "Given a report property map, create or update the report and return `true` on success."
  ([report :guard #(get-report (key-for %))] (update-report report))
  ([report] (create-report report)))

(defn delete-report
  "Given the ticker symbol of the company and the year and period of the report, delete the report and return `true`."
  [ticker year period]
  (< 0 (:deleted
    (with-open [conn (apply r/connect c/db-options)]
      (-> (r/table "reports")
        (r/get-all [(key-for ticker year period)] {:index primary-key})
        (r/delete)
        (r/run conn))))))

;; ----- Collection of reports -----

(defn list-reports
  "Given the ticker symbol of a company, return a sequence of report hashes with `:year` and `:period`."
  [ticker]
  (with-open [conn (apply r/connect c/db-options)]
    (-> (r/table "reports")
      (r/get-all [ticker] {:index "symbol"})
      (r/with-fields ["year" "period"])
      (r/run conn))))

(defn report-count
  "Given the ticker symbol of a company, return how many reports exist for the company."
  [ticker]
  (with-open [conn (apply r/connect c/db-options)]
    (-> (r/table "reports")
      (r/get-all [ticker] {:index "symbol"})
      (r/count)
      (r/run conn))))