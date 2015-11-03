(ns open-company.util.sample-data
  "Commandline client to import sample data into OpenCompany."
  (:require [clojure.string :as s]
            [clojure.tools.cli :refer (parse-opts)]
            [defun :refer (defun-)]
            [open-company.db.pool :as pool]
            [open-company.resources.common :as common]
            [open-company.resources.company :as company]
            [open-company.resources.section :as section])
  (:gen-class))

(defn exit [status msg]
  (println msg)
  (System/exit status))

;; ----- Sections import -----

(defun- import-sections
  "Add each section to the company recursively."
  ([_company-slug _sections :guard empty?] true)

  ([company-slug sections]
  (let [section (first sections)
        section-name (:section-name section)
        timestamp (:timestamp section)
        author (:author section)]
    (println (str "Creating section '" section-name "' at " timestamp " by " (:name author) "."))
    (section/put-section company-slug section-name (:section section) author timestamp)
    (import-sections company-slug (rest sections)))))

(defn- import-sections-map
  "Update the company with the specified slug with the specified :sections map."
  [slug sections]
  (println (str "Updating company sections for '" slug "'."))
  (if-let [original-company (common/read-resource company/table-name slug)]
    (common/update-resource company/table-name :slug original-company (assoc original-company :sections sections))))

;; ----- Company import -----

(defn import-company [options data]
  (let [company (:company data)
        slug (:slug company)
        delete (:delete options)
        author (:author company)]
    (when (and delete (company/get-company slug))
      (println (str "Deleting company '" slug "'."))
      (company/delete-company slug))
    (when-not delete
      (when (company/get-company slug)
        (exit 1 (str "A company for '" slug "' already exists. Use the -d flag to delete the company on import."))))
    (println (str "Creating company '" slug "' by " (:name author)"."))
    (company/create-company (dissoc company :author :timestamp :sections) author (:timestamp company))
    (import-sections slug (:sections data))
    (import-sections-map slug (:sections company)))
  (println "\nImport complete!\n"))

;; ----- CLI -----

(def cli-options
  [["-d" "--delete" "Delete the company if it already exists"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (s/join \newline
     ["This program imports OpenCompany data from an EDN file."
      ""
      "Usage: lein run -m open-company.util.sample-data -- [options] company-data.edn"
      ""
      "Options:"
      options-summary
      ""
      "Company data: an EDN file with company and sections"
      ""
      "Please refer to ./opt/samples for more information on the file format."
      ""]))

(defn data-msg []
  "\nThe data file must be an EDN file with a :company map and :sections vector.\n")

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (s/join \newline errors)))

(defn -main [& args]
  (pool/start)
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    ;; Try the import
    (try
      (let [data (read-string (slurp (first arguments)))]
        (if (and (map? data) (:company data) (:sections data))
          (import-company options data)
          (exit 1 (data-msg))))
      (catch Exception e
        (println e)
        (exit 1 (data-msg))))))