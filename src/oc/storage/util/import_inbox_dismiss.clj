(ns oc.storage.util.import-inbox-dismiss
  "
  For every read we have in DynamoDB, create a dismiss-at record in the corresponding entry.

  Dry run:

  lein run -m oc.storage.util.import-inbox-dismiss

  To actualy update

  lein run -m oc.storage.util.import-inbox-dismiss -f

  or

  lein run -m oc.storage.util.import-inbox-dismiss --force-update
  "
  (:require [clojure.string :as s]
            [clojure.tools.cli :refer (parse-opts)]
            [oc.lib.db.pool :as db]
            [oc.storage.resources.org :as org]
            [oc.storage.resources.entry :as entry]
            [oc.lib.change.resources.read :as read]
            [oc.storage.config :as c])
  (:gen-class))

(defn exit [status msg]
  (println msg)
  (System/exit status))

;; ----- Resource import -----

;; ----- CLI -----

(def cli-options
  [["-f" "--force-update"]])

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (s/join \newline errors)))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (try
      (let [conn (db/init-conn c/db-options)
            orgs (org/list-orgs conn)]
        (doseq [org orgs]
          (let [entries (entry/list-entries-by-org conn (:uuid org))]
            (println "------------------------------------------------------------------")
            (println "Org:" (:name org) "(" (:uuid org) ")")
            (doseq [e entries]
              (let [entry-reads (read/retrieve-by-item c/dynamodb-opts (:uuid e))]
                (when (seq entry-reads)
                  (let [entry-user-vis (loop [user-visibility (:user-visibility e)
                                              reads entry-reads]
                                         (let [[first-read & rest-reads] reads
                                               next-user-visibility (assoc-in user-visibility
                                                                     [(keyword (:user-id first-read)) :dismiss-at]
                                                                     (:read-at first-read))]
                                           (if (seq rest-reads)
                                             (recur next-user-visibility rest-reads)
                                             next-user-visibility)))
                        updated-entry (assoc e :user-visibility entry-user-vis)]
                    (println "Updating entry" (:uuid e) "with:" entry-user-vis)
                    (when (:force-update options)
                      (entry/update-entry-no-user! conn (:uuid e) updated-entry)))))))))
      (catch Exception e
        (println e)
        (exit 1 "Exception migrating read->dimiss-at.")))))