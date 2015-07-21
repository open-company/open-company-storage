(ns open-company.resources.company
  (:require [clojure.string :as s]
            [defun :refer (defun)]
            [rethinkdb.query :as r]
            [open-company.config :as c]
            [open-company.resources.common :as common]))

;; ----- Validations -----

(defun valid-ticker-symbol?
  "Return `true` if the specified ticker symbol is potentially a valid symbol (follows the rules)
  of an open company, otherwise return `false`."
  ([_ticker :guard nil?] false)
  ([ticker]
    (let [clean-ticker (s/trim ticker)
          char-count (count clean-ticker)]
      (and (>= char-count 1) (<= char-count 5)))))

(defun valid-company
  "Given the ticker symbol of a new company, and a map of the new company's properties,
  check if the everything is in order to create the new company.

  Ensures the name of the company is specified or returns `:invalid-name`.

  Ensures the ticker is valid, or returns `:invalid-symbol`.

  If everything is OK with the proposed new company, `true` is returned.

  TODO: Use prismatic schema to validate company properties."
  ([_ticker :guard #(not (string? %)) _] :invalid-symbol)
  ([_ticker :guard #(not (valid-ticker-symbol? %)) _] :invalid-symbol)
  ([_ _properties :guard #(or (not (string? (:name %))) (s/blank? (:name %)))] :invalid-name)
  ([ticker properties] (if (= ticker (:symbol properties)) true :invalid-symbol)))

;; ----- Company CRUD -----

(defn get-company
  "Given the ticker symbol of the company, retrieve it from the database, or return nil if it doesn't exist."
  [ticker]
  (with-open [conn (apply r/connect c/db-options)]
    (first
      (-> (r/table "companies")
        (r/get-all [ticker] {:index "symbol"})
        (r/run conn)))))

(defn create-company
  "Given the company property map, create the company returning the property map for the resource or `false`."
  [company]
  (common/create-resource "companies" company))

(defn update-company
  "Given the current ticker symbol of the company and an updated company property map,
  update the company and return `true` on success.
  TODO: handle case of ticker symbol change."
  [ticker company]
  (if-let [original-company (get-company ticker)]
    (common/update-resource "companies" original-company company)))

(defun put-company
  "Given the ticker symbol of the company and a company property map, create or update the company
  and return `true` on success.
  TODO: handle case of ticker symbol change."
  ([ticker :guard get-company company] (update-company ticker company))
  ([_ company] (create-company company)))

(defn delete-company
  "Given the ticker symbol of the company, delete it and all its reports and return `true` on success."
  [ticker]
  (< 0 (:deleted
    (with-open [conn (apply r/connect c/db-options)]
      (-> (r/table "companies")
        (r/get-all [ticker] {:index "symbol"})
        (r/delete)
        (r/run conn))))))

;; ----- Collection of companies -----

(defn list-companies
  "Return a sorted sequence of the company ticker symbols."
  []
  (vec (sort (map :symbol
    (with-open [conn (apply r/connect c/db-options)]
      (-> (r/table "companies")
        (r/with-fields ["symbol"])
        (r/run conn)))))))

(defn delete-all-companies!
  "Use with caution! Returns `true` if successful."
  []
  (< 0 (:deleted
    (with-open [conn (apply r/connect c/db-options)]
      (-> (r/table "reports")
        (r/delete)
        (r/run conn))
      (-> (r/table "companies")
        (r/delete)
        (r/run conn))))))