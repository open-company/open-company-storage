(ns oc.storage.util.import
  "
  Commandline client to import data into OpenCompany.

  Usage:

  lein run -m oc.storage.util.import -- -d ./opt/samples/buff.edn

  lein run -m oc.storage.util.import -- -d ./opt/samples/
  "
  (:require [clojure.string :as s]
            [clojure.tools.cli :refer (parse-opts)]
            [clojure.walk :refer (keywordize-keys)]
            [zprint.core :as zp]
            [oc.lib.db.pool :as db]
            [oc.lib.db.common :as db-common]
            [oc.storage.resources.org :as org]
            [oc.storage.resources.board :as board]
            [oc.storage.resources.entry :as entry]
            [oc.storage.config :as c])
  (:gen-class))

(defn exit [status msg]
  (println msg)
  (System/exit status))

;; ----- REPL functions for porting legacy sample data -----

(defn to-entry [section]
  (let [entry (-> section
                (dissoc :description :updated-at :body-placeholder :id :company-slug :prompt :units :intervals)
                (clojure.set/rename-keys {:section-name :topic-slug}))]
    (if (s/blank? (:image-url entry))
      (dissoc entry :image-url :image-height :image-width)
      entry)))

(defn port-entries [old]
  (let [sections (:sections old)
        entries (map to-entry sections)]
    (zp/zprint entries {:map {:comma? false}})))

(defn port-org [old]
  (let [company (:company old)
        props (select-keys company [:slug :name :currency :team-id :logo :promoted :topics])
        org (clojure.set/rename-keys props {:logo :logo-url})]
    (zp/zprint (assoc org :team-id "1234-abcd-1234") {:map {:comma? false}})))

;; ----- Resource import -----

(defn import-update [conn board update author]
  ;; TODO
  )

(defn- import-entry [conn org board entry author]
  (let [timestamp (:created-at entry)
        slug (:topic-slug entry)
        entry-author (or (first (:author entry)) author)
        author (assoc entry-author :teams [(:team-id org)])]
    (println (str "Creating entry for " slug " topic at " timestamp))
    (db-common/create-resource conn entry/table-name
      (entry/->entry conn (:uuid board) slug entry author) timestamp)))

(defn- import-board [conn org board author]
  (println (str "Creating board '" (:name board) "'."))
  (if-let [new-board (board/create-board! conn (board/->board (:uuid org)
                        (dissoc board :entries :updates) author))]
    (do
      ;; Create the entries
      (println (str "Creating " (count (:entries board)) " entries."))
      (doseq [entry (:entries board)]
        (import-entry conn org new-board entry author))

      ;; Create the updates
      (println (str "Creating " (count (:updates board)) " updates."))
      (doseq [entry (:updates board)]
        (import-update conn new-board entry author)))
    
    (do
      (println "\nFailed to create the board!")
      (exit 1 "Board creation failed."))))

(defn- import-org [conn options data]
  (let [delete (:delete options)
        org (dissoc data :boards)
        boards (:boards data)
        org-slug (:slug org)
        author (assoc (:author org) :teams [(:team-id org)])
        prior-org (org/get-org conn org-slug)]

    ;; Delete the org if needed
    (when (and delete prior-org)
      (println (str "Deleting org '" org-slug "'."))
      (org/delete-org! conn org-slug))
    
    ;; Conflicting org?
    (when (and (not delete) prior-org)
      (exit 1 (str "An org for '" org-slug "' already exists. Use the -d flag to delete the org on import.")))
    
    ;; Create the org
    (println (str "Creating org '" org-slug "."))
    (if-let [new-org (org/create-org! conn (org/->org org author))]

      (do
        ;; Create the boards
        (println (str "Creating " (count boards) " boards."))
        (doseq [board boards]
          (import-board conn new-org board author)))

      (do
        (println "\nFailed to create the org!")
        (exit 1 "Org creation failed."))))

  (println "\nImport complete!\n"))

;; ----- CLI -----

(def cli-options
  [["-d" "--delete" "Delete the org if it already exists"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (s/join \newline
     ["\nThis program imports OpenCompany data from EDN file(s)."
      ""
      "Usage:"
      "  lein run -m oc.storage.util.import -- [options] org-data.edn"
      "  lein run -m oc.storage.util.import -- [options] /directory/"
      ""
      "Options:"
      options-summary
      ""
      "Org data: an EDN file with an org, consisting of board(s) with topic entries and updates."
      "Directory: a directory of org data EDN files"
      ""
      "Please refer to ./opt/samples for more information on the file format."
      ""]))

(defn data-msg []
  "\nNOTE: The data file(s) must be an EDN file with an Org map with a sequence of Boards in a :boards property.\n")

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
            (if (and (map? data) (:slug data) (:boards data))
              (import-org conn options (keywordize-keys data))
              (exit 1 (data-msg))))))
      (catch Exception e
        (println e)
        (exit 1 "Exception importing.")))))