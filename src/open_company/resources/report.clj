(ns open-company.resources.report
  (:require [clojure.string :as s]
            [rethinkdb.query :as r]
            [open-company.config :as c]))

(def media-type "application/vnd.open-company.report+json;version=1")

(def ^:private primary-key :symbol-year-period)

(defn- key-for [ticker year period]
  (str ticker "-" year "-" period))

(defn valid-period? [period]
  "TBD"
  true)

(defn valid-report
  "
  TBD. Use prismatic schema.
  "
  ([ticker year period] (valid-report ticker year period {}))
  ([ticker year period properties]
    true))

(defn get-report [ticker year period]
  "Given the ticker symbol of the company, retrieve it from the database, or return nil if it doesn't exist."
  (with-open [conn (apply r/connect c/db-options)]
    (first
      (-> (r/table "reports")
        (r/get-all [(key-for ticker year period)] {:index primary-key})
        (r/run conn)))))

(defn put-report
  "Given the ticker symbol of the company, create it or update it and return `true`."
  [ticker year period report]
  (< 0 (:inserted
    (with-open [conn (apply r/connect c/db-options)]
      (-> (r/table "reports")
        (r/insert (assoc report primary-key (key-for ticker year period))
        (r/run conn)))))))

(defn delete-report
  "Given the ticker symbol of the company, delete it and all its reports and return `true`."
  [ticker year period]
  (< 0 (:deleted
    (with-open [conn (apply r/connect c/db-options)]
      (-> (r/table "reports")
        (r/get-all [(key-for ticker year period)] {:index primary-key})
        (r/delete)
        (r/run conn))))))

(defn report-count [ticker]
  (with-open [conn (apply r/connect c/db-options)]
    (-> (r/table "reports")
      (r/get-all [ticker] {:index "symbol"})
      (r/count)
      (r/run conn))))