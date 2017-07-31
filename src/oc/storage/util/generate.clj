(ns oc.storage.util.generate
  "
  Commandline client to generate data, in a date range, into an existing OpenCompany org, according to a configuration
  file.

  Usage:

  lein run -m oc.storage.util.generate -- <org-slug> <config-file> <start-date> <end-date>

  lein run -m oc.storage.util.generate -- 18f ./opt/generate.edn 2017-01-01 2017-06-31
  "
  (:require [clojure.tools.cli :refer (parse-opts)]
            [defun.core :refer (defun-)]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [oc.lib.db.pool :as db]
            [oc.lib.db.common :as db-common]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.entry :as entry-res]
            [oc.storage.config :as c])
  (:gen-class))

(def date-parser (f/formatter "YYYY-MM-dd"))

;; ----- Data Generation -----

(defn- generate-update [conn org config-data authors this-date]
  (println "here")
  (let [update-count (int (inc (Math/floor (rand (:max-entries config-data)))))]
    (println (str (f/unparse date-parser this-date) ": Generating " update-count " updates."))
    ))

(defn- author-pool [size]
  [])

(defn- complete? [date-range] (t/after? (first date-range) (last date-range)))

(defun- generate 
  
  ;; Initiate w/ a pool of authors
  ([conn org config-data date-range]
    (println "\nGenerating!")
    (let [authors (author-pool (:author-pool config-data))]
      (generate conn org config-data authors date-range))) ; recurse

  ;; Complete
  ([conn org config-data authors date-range :guard complete?] (println "\nDone!"))

  ;; Generate updates for this date
  ([conn org config-data authors date-range]
    (println "A")
    (let [this-date (first date-range)]
      (println "B")
      (if (<= (rand) (:chance-of-entry config-data)) ; is there an update today?
        (do (println "C") (generate-update conn org config-data authors this-date))
        (do (println "D") (println (str (f/unparse date-parser this-date) ": Skipped"))))
      (recur conn org config-data authors [(t/plus this-date (t/days 1)) (last date-range)])))) ; recurse

;; ----- CLI -----

(def cli-options
  [["-h" "--help"]])

(defn- usage [options-summary]
  (clojure.string/join \newline
     ["\nThis program generates OpenCompany data in a date range, into an existing OpenCompany org, according to a configuration file, for development and testing purposes."
      ""
      "Usage:"
      "  lein run -m oc.storage.util.generate -- [options] <org-slug> <config-file> <start-date> <end-date>"
      "  lein run -m oc.storage.util.generate -- 18f ./opt/generate.edn 2016-01-01 2017-06-30"
      ""
      "Options:"
      options-summary
      ""
      "Config file: an EDN file with a map of config options."
      ""
      "Please refer to ./opt/generate.edn for more information on the file format."
      ""]))

(def config-msg "\nNOTE: The config file must be an EDN file with a map of config options.\n")

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join \newline errors)))

(defn- exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 4) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    ;; Get the list of files to import
    (try
      (let [org-slug (first arguments)
            filename (second arguments)
            start-date (f/parse date-parser (nth arguments 2))
            end-date (f/parse date-parser (last arguments))
            conn (db/init-conn c/db-options)
            config-data (read-string (slurp filename))
            org (org-res/get-org conn org-slug)]
        (when-not (map? config-data) (exit 1 config-msg))
        (when (nil? org) (exit 1 (str "Org not found for: " org-slug)))
        (generate conn org config-data [start-date end-date])
        (exit 0 "\n"))
      (catch Exception e
        (println e)
        (exit 1 "Exception generating.")))))