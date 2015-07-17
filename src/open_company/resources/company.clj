(ns open-company.resources.company
  (:require [clojure.string :as s]
            [rethinkdb.query :as r]
            [open-company.config :as c]))

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
  [ticker properties]
    (let [provided-ticker (:symbol properties)
        company-name (:name properties)]
      (cond
        (or (not (string? company-name)) (s/blank? company-name)) :no-name
        (and provided-ticker (not (= ticker provided-ticker))) :invalid-symbol
        (not (valid-ticker-symbol? ticker)) :invalid-symbol
        :else true)))

(defn get-company
  "Given the ticker symbol of the company, retrieve it from the database, or return nil if it doesn't exist."
  [ticker]
  (with-open [conn (apply r/connect c/db-options)]
    (first
      (-> (r/table "companies")
        (r/get-all [ticker] {:index "symbol"})
        (r/run conn)))))

(defn create-company
  "Given the company property map, create or update the company and return `true` on success."
  [company]
  (< 0 (:inserted
    (with-open [conn (apply r/connect c/db-options)]
    (-> (r/table "companies")
        (r/insert company)
        (r/run conn))))))

(defn update-company
  "Given the current ticker symbol of the company and an updated company property map,
  update the company and return `true` on success.
  TODO: handle case of ticker symbol change."
  [ticker company]
  (if (get-company ticker)
    (< 0 (:replaced
      (with-open [conn (apply r/connect c/db-options)]
        (-> (r/table "companies")
          (r/replace company)
          (r/run conn)))))
    false))

(defn put-company
  "Given the ticker symbol of the company and a company property map, create or update the company
  and return `true` on success.
  TODO: handle case of ticker symbol change."
  [ticker company]
  (if (get-company ticker)
    (update-company ticker company)
    (create-company company)))

(defn delete-company
  "Given the ticker symbol of the company, delete it and all its reports and return `true` on success."
  [ticker]
  (< 0 (:deleted
    (with-open [conn (apply r/connect c/db-options)]
      (-> (r/table "companies")
        (r/get-all [ticker] {:index "symbol"})
        (r/delete)
        (r/run conn))))))

(defn delete-all!
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