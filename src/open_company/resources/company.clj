(ns open-company.resources.company
  (:require [clojure.string :as s]
            [rethinkdb.query :as r]))

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
  (with-open [conn (r/connect :host "127.0.0.1" :port 28015 :db "opencompany")]
    (first (-> (r/table "companies")
        (r/filter (r/fn [row]
        (r/eq ticker (r/get-field row "symbol"))))
        (r/run conn)))))

(defn put-company [ticker company]
  (with-open [conn (r/connect :host "127.0.0.1" :port 28015 :db "opencompany")]
    (-> (r/table "companies")
        (r/insert company)
        (r/run conn))))

(defn delete-company [ticker]
  nil)

(defn report-count [ticker]
  0)