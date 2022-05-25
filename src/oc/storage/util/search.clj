(ns oc.storage.util.search
  "
  Publish search service triggers to AWS SQS.
  Usage:

  lein run -m oc.storage.util.search

  "
  (:require [clojure.string :as s]
            [amazonica.aws.sqs :as sqs]
            [amazonica.aws.kinesisfirehose :as fh]
            [cheshire.core :as json]
            [clojure.tools.cli :refer (parse-opts)]
            [taoensso.timbre :as timbre]
            [oc.storage.config :as config]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.entry :as entry-res]
            [oc.lib.db.pool :as db])
  (:gen-class))

(defn exit [status msg]
  (println msg)
  (System/exit status))

;; ----- Search triggering -----

(defn ->trigger [resource-type content org board]
  {:Message
   (json/generate-string {:resource-type resource-type
                          :org org
                          :board board
                          :content {:new content}})
   })

(defn send-trigger! [trigger]
  (timbre/debug "Search request:" trigger)
  (timbre/info "Sending request to queue:" config/aws-sqs-search-index-queue)
  (let [message (json/generate-string trigger {:pretty true})]
    (try
      (sqs/send-message
       {:access-key config/aws-access-key-id
        :secret-key config/aws-secret-access-key}
       config/aws-sqs-search-index-queue
       message)
      (catch Exception e
        (timbre/info "SQS failed with: " e)
        (timbre/info "sending to " config/aws-kinesis-reindex-stream-name)
        ;; If an exception occurred write to the kinesis firehouse.
        (when config/aws-kinesis-reindex-stream-name
          (fh/put-record
           config/aws-kinesis-reindex-stream-name
           message))))))

(defn index-all-entries [conn]
  (doseq [org (org-res/list-orgs conn [:team-id])]
    (doseq [board (board-res/list-boards-by-org conn (:uuid org) [:access :viewers :authors])]
      (let [entries (entry-res/list-entries-by-board conn (:uuid board) {})]
        (doseq [entry entries]
          (send-trigger! (->trigger "entry" entry org board)))))))

;; ----- CLI -----

(def cli-options
  [["-h" "--help"]])

(defn usage [options-summary]
  (s/join \newline
     ["\nThis program queries for all OpenCompany entries and sends them to the search index queue."
      ""
      "Usage:"
      "  lein run -m oc.storage.util.search  "

      ""]))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (s/join \newline errors)))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 0) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))

    (let [conn (db/init-conn config/db-options)]
      (println "Indexing all entries.")
      (index-all-entries conn))))
