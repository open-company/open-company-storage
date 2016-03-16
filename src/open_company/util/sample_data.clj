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
  ([_company-slug _sections :guard empty? _org-id] true)

  ([company-slug sections org-id]
  (let [section (first sections)
        section-name (:section-name section)
        timestamp (:timestamp section)
        author (:author section)
        message (str "'" section-name "' at " timestamp " by " (:name author) ".")]
    (println (str "Creating " message))
    (if
      (section/put-section company-slug section-name (:section section) (assoc author :org-id org-id) timestamp)
      (println (str "SUCCESS: created " message))
      (println (str "FAILURE: creating " message))))
  (recur company-slug (rest sections) org-id)))

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
        author (:author company)
        org-id (:org-id company)]
    (when (and delete (company/get-company slug))
      (println (str "Deleting company '" slug "'."))
      (company/delete-company slug))
    (when-not delete
      (when (company/get-company slug)
        (exit 1 (str "A company for '" slug "' already exists. Use the -d flag to delete the company on import."))))
    (println (str "Creating company '" slug "' by " (:name author)"."))
    (company/create-company!
     (company/->company
      (dissoc company :author :timestamp :sections :org-id)
      (assoc author :org-id org-id)))
    (import-sections slug (:sections data) org-id)
    (import-sections-map slug (:sections company)))
  (println "\nImport complete!\n"))

;; ----- CLI -----

(def cli-options
  [["-d" "--delete" "Delete the company if it already exists"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (s/join \newline
     ["This program imports OpenCompany data from EDN file(s)."
      ""
      "Usage:"
      "  lein run -m open-company.util.sample-data -- [options] company-data.edn"
      "  lein run -m open-company.util.sample-data -- [options] /directory/"
      ""
      "Options:"
      options-summary
      ""
      "Company data: an EDN file with company and sections"
      "Directory: a directory of comany data EDN files"
      ""
      "Please refer to ./opt/samples for more information on the file format."
      ""]))

(defn data-msg []
  "\nNOTE: The data file(s) must be an EDN file with a :company map and :sections vector.\n")

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (s/join \newline errors)))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    ;; Get the list of files to import
    (try
      (let [arg (first arguments)
            edn-file #".*\.edn$"
            filenames (if (re-matches edn-file arg)
                        [arg] ; they specified just 1 file
                        (filter #(re-matches edn-file %) (map str (file-seq (clojure.java.io/file arg)))))] ; a dir
        ;; Import each file
        (doseq [filename filenames]
          (let [data (read-string (slurp filename))]
            (if (and (map? data) (:company data) (:sections data))
              (import-company options data)
              (exit 1 (data-msg))))))
      (catch Exception e
        (println e)
        (exit 1 (data-msg))))))