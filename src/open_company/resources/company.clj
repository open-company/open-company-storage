(ns open-company.resources.company
  (:require [clojure.string :as s]
            [rethinkdb.query :as r]
            [open-company.config :as c]))

(def company-media-type "application/vnd.open-company.company+json;version=1")

(defn valid-ticker-symbol? [ticker]
  (let [char-count (count ticker)]
    (and (>= char-count 1) (<= char-count 5))))

(defn valid-company
  "
  Given the ticker symbol of a new company, and a map of the new company's properties,
  check if the everything is in order to create the new company.

  Ensures the name of the company is specified or returns `:no-name`.

  Ensures the ticker is valid, or returns `:invalid-symbol`.

  If everything is OK with the proposed new company, `true` is returned.
  "
  ([ticker] (valid-company ticker {}))
  ([ticker properties]
    (let [provided-ticker (:symbol properties)
        company-name (:name properties)]
      (cond
        (or (not (string? company-name)) (s/blank? company-name)) :no-name
        (not (= ticker provided-ticker)) :invalid-symbol
        (not (valid-ticker-symbol? ticker)) :invalid-symbol
        :else true))))

(defn get-company [ticker]
  "Given the ticker symbol of the company, retrieve it from the database, or return nil if it doesn't exist."
  (with-open [conn (apply r/connect c/db-options)]
    (first
      (-> (r/table "companies")
        (r/filter (r/fn [row] (r/eq ticker (r/get-field row "symbol"))))
        (r/run conn)))))

(defn put-company
  "Given the ticker symbol of the company, create it or update it and return `true`."
  [ticker company]
  (< 0 (:inserted
    (with-open [conn (apply r/connect c/db-options)]
    (-> (r/table "companies")
        (r/insert company)
        (r/run conn))))))

(defn delete-company
  "Given the ticker symbol of the company, delete it and all its reports and return `true`."
  [ticker]
  (< 0 (:deleted
    (with-open [conn (apply r/connect c/db-options)]
      (-> (r/table "companies")
        (r/filter (r/fn [row] (r/eq ticker (r/get-field row "symbol"))))
        (r/delete)
        (r/run conn))))))
  
(defn report-count [ticker]
  0)