(ns oc.storage.util.inbox-opt-out
  "
  Convert all the entries from opt-in to opt-out.

  NB: before running this script you need to add this key:

  (schema/optional-key :follow) schema/Bool

  to the UserVisibility schema in oc.storage.resources.common
  or the update will fail when reading the source entry.


  Usage:

  lein run -m oc.storage.util.inbox-opt-out
  "
  (:require [clojure.string :as s]
            [clojure.tools.cli :refer (parse-opts)]
            [oc.lib.db.pool :as db]
            [oc.storage.resources.org :as org]
            [oc.storage.resources.entry :as entry]
            [oc.storage.config :as c])
  (:gen-class))

(defn exit [status msg]
  (println msg)
  (System/exit status))

;; ----- Resource import -----

;; ----- CLI -----

(def cli-options
  [])

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (s/join \newline errors)))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Get the list of files to import
    (try
      (let [conn (db/init-conn c/db-options) ; a dir
            orgs (org/list-orgs conn)]
        (doseq [org orgs]
          (let [entries (entry/list-entries-by-org conn (:uuid org))]
            (println "------------------------------------------------------------------")
            (println "Org:" (:name org) "(" (:uuid org) ")")
            (doseq [e entries]
              (let [user-vis (into {}
                              (remove nil?
                               (map (fn [[k v]]
                                     (cond
                                       (false? (:follow v))
                                       {k (-> v (dissoc :follow) (assoc :unfollow true))}
                                       (seq (dissoc v :follow))
                                       {k (dissoc v :follow)}
                                       :else
                                       nil))
                                (:user-visibility e))))
                    cleaned-entry (if (seq user-vis) (assoc e :user-visibility user-vis) (dissoc e :user-visibility))]
                (println "Updating entry" (:uuid e))
                (entry/update-entry-no-user! conn (:uuid e) cleaned-entry))))))
      (catch Exception e
        (println e)
        (exit 1 "Exception migrating follow<->unfollow.")))))