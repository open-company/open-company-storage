(ns open-company.util.export
  "
  Commandline client to export data from OpenCompany.

  Usage:

  lein run -m open-company.util.export -- buff ./opt/samples/buff.edn
  "
  (:require [clojure.string :as s]
            [clojure.tools.cli :refer (parse-opts)]
            [oc.lib.db.pool :as db]
            [open-company.resources.common :as common]
            [open-company.resources.company :as company]
            [open-company.config :as c]
            [zprint.core :as zp])
  (:gen-class))

;; ----- CLI -----

(defn exit [status msg]
  (println msg)
  (System/exit status))

(def cli-options
  [["-h" "--help"]])

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (s/join \newline errors)))

(defn usage [options-summary]
  (s/join \newline
     ["\nThis program exports OpenCompany data to an EDN file."
      ""
      "Usage:"
      "  lein run -m open-company.util.export -- [organization slug] [edn file]"
      "  lein run -m open-company.util.export -- buff ./opt/samples/buff.edn"
      ""
      "Please refer to ./opt/samples for more information on the file format."
      ""]))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 2) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    ;; Export the organization, historic sections, and shared updates
    (try
      (let [conn (db/init-conn c/db-options)
            slug (first arguments)
            edn-file (second arguments)]
        (if-let [company (company/get-company conn slug)]
          (let [sections (common/read-resources conn common/section-table-name "company-slug" slug)
                updates (common/read-resources conn common/stakeholder-update-table-name "company-slug" slug)]
            (spit edn-file (zp/zprint-str
              {:company company
               :sections sections
               :updates updates}))
            (exit 0 (str "\nExport of " slug " to " edn-file " completed.\n")))
          (exit 1 (str "\nNot found: " slug "\n"))))
      (catch Exception e
        (println e)
        (exit 1 "Exception exporting.")))))