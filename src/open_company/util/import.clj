(ns open-company.util.import
  "
  Commandline client to import data into OpenCompany.

  Usage:

  lein run -m open-company.util.import -- -d ./opt/samples/buff.edn

  lein run -m open-company.util.import -- -d ./opt/samples/
  "
  (:require [clojure.string :as s]
            [clojure.tools.cli :refer (parse-opts)]
            [oc.lib.rethinkdb.pool :as db]
            [open-company.resources.common :as common]
            [open-company.resources.company :as company]
            [open-company.config :as c])
  (:gen-class))

(defn exit [status msg]
  (println msg)
  (System/exit status))

;; ----- Company import -----

(defn import-company [conn options data]
  (let [delete (:delete options)
        company (:company data)
        slug (:slug company)
        sections (:sections data)
        updates (:updates data)
        prior-company (company/get-company conn slug)]
    (when (and delete prior-company)
      (println (str "Deleting company '" slug "'."))
      (company/delete-company! conn slug))
    (when (and (not delete) prior-company)
      (exit 1 (str "A company for '" slug "' already exists. Use the -d flag to delete the company on import.")))
    (println (str "Creating company '" slug "."))
    (common/create-resource conn common/company-table-name company (:created-at company) (:updated-at company))
    (println (str "Creating " (count sections) " sections."))
    (doseq [section sections]
      (common/create-resource conn common/section-table-name section (:created-at section) (:updated-at section)))
    (println (str "Creating " (count updates) " updates."))
    (doseq [su updates]
      (common/create-resource conn common/stakeholder-update-table-name su (:created-at su) (:updated-at su))))
  (println "\nImport complete!\n"))

;; ----- CLI -----

(def cli-options
  [["-d" "--delete" "Delete the company if it already exists"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (s/join \newline
     ["\nThis program imports OpenCompany data from EDN file(s)."
      ""
      "Usage:"
      "  lein run -m open-company.util.import -- [options] company-data.edn"
      "  lein run -m open-company.util.import -- [options] /directory/"
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
            conn (db/init-conn c/db-options)
            edn-file #".*\.edn$"
            filenames (if (re-matches edn-file arg)
                        [arg] ; they specified just 1 file
                        (filter #(re-matches edn-file %) (map str (file-seq (clojure.java.io/file arg)))))] ; a dir
        ;; Import each file
        (doseq [filename filenames]
          (let [data (read-string (slurp filename))]
            (if (and (map? data) (:company data) (:sections data))
              (import-company conn options data)
              (exit 1 (data-msg))))))
      (catch Exception e
        (println e)
        (exit 1 "Exception importing.")))))