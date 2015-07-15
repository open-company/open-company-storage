(ns open-company.representations.report
  (:require [cheshire.core :as json]
            [open-company.representations.common :as common]
            [open-company.resources.report :as report]))

(defn- url [report] 
  (str "/v1/companies/" (:symbol report) "/" (:year report) "/" (:period report)))

(defn- self-link [report]
  (common/self-link (url report) report/media-type))

(defn- update-link [report]
  (common/update-link (url report) report/media-type))

(defn- delete-link [report]
  (common/delete-link (url report)))

(defn report-links
  "Add the HATEAOS links to the report"
  [report]
  (apply array-map (concat (flatten (vec report)) 
    [:links [
      (self-link report)
      (update-link report)
      (delete-link report)]])))

(defn- report-to-json-map [report]
  ;; Generate JSON from the sorted array map that results from:
  ;; 1) removing unneeded :id key
  ;; 2) remove unneeded :symbol-year-period compound primary key
  ;; 2) render timestamps as strings
  ;; 3) adding the HATEAOS links to the array hash
  (let [report-props (dissoc report :id)]
    (-> report-props
      (dissoc report :symbol-year-period)
      report-links)))

(defn render-report
  "Create a JSON representation of a report for the REST API"
  [report]
  (json/generate-string (report-to-json-map report) {:pretty true}))