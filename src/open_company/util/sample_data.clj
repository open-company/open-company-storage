(ns open-company.util.sample-data
  "
  Commandline client to import sample data into OpenCompany.

  Usage: lein run -m open-company.util.sample-data -- -d ./opt/samples/
  "
  (:require [clojure.string :as s]
            [clojure.tools.cli :refer (parse-opts)]
            [oc.lib.rethinkdb.pool :as db]
            [open-company.resources.common :as common]
            [open-company.resources.company :as company]
            [open-company.resources.section :as section]
            [open-company.resources.stakeholder-update :as su])
  (:gen-class))

(defn exit [status msg]
  (println msg)
  (System/exit status))

;; ----- Stakeholder updates import -----

(defn- import-stakeholder-update
  "Add a stakeholder update."
  [conn company-slug company update]
  (let [title (:title update)
        as-of (:timestamp update)
        author (:author update)
        message (str "stakeholder update '" title "' at " as-of " by " (:name author) ".")]
    (println (str "Creating " message))
    (if
      (su/create-stakeholder-update! conn
        (su/->stakeholder-update conn
          company
          (dissoc update :timestamp)
          as-of
          author))
        (println (str "SUCCESS: created " message))
        (println (str "FAILURE: creating " message)))))

;; ----- Sections import -----

(defn- import-section
  "Add a section to the company."
  [conn company-slug section org-id]
  (let [section-name (:section-name section)
        timestamp (:timestamp section)
        author (:author section)
        message (str "'" section-name "' at " timestamp " by " (:name author) ".")]
    (println (str "Creating " message))
    (if
      (section/put-section conn company-slug section-name (:section section) (assoc author :org-id org-id) timestamp)
      (println (str "SUCCESS: created " message))
      (println (str "FAILURE: creating " message)))))

(defn- import-sections-map
  "Update the company with the specified slug with the specified :sections map."
  [conn slug sections]
  (println (str "Updating company sections for '" slug "'."))
  (if-let [original-company (common/read-resource conn company/table-name slug)]
    (common/update-resource conn company/table-name :slug original-company (assoc original-company :sections sections))))

;; ----- Company import -----

(defn import-company [conn options data]
  (let [company (:company data)
        slug (:slug company)
        delete (:delete options)
        author (:author company)
        org-id (:org-id company)]
    (when (and delete (company/get-company conn slug))
      (println (str "Deleting company '" slug "'."))
      (company/delete-company conn slug))
    (when-not delete
      (when (company/get-company conn slug)
        (exit 1 (str "A company for '" slug "' already exists. Use the -d flag to delete the company on import."))))
    (println (str "Creating company '" slug "' by " (:name author)"."))
    (company/create-company!
     conn
     (company/->company
      (dissoc company :author :timestamp :sections :org-id)
      (assoc author :org-id org-id)))
    (doseq [section (:sections data)]
      (import-section conn slug section org-id))
    (import-sections-map conn slug (:sections company))
    (doseq [stakeholder-update (:stakeholder-updates data)]
      (import-stakeholder-update conn slug company stakeholder-update)))
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
            conn (db/init-conn)
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
        (exit 1 (data-msg))))))