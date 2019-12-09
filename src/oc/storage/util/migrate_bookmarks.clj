(ns oc.storage.util.migrate-bookmarks
  "Get all the existing uncompleted follow-ups and create a bookmark for every one of them.
   To perform a dry run and simulate the update use:

  lein run -m oc.storage.util.migrate-bookmark

  To actually update the entries:

  lein run -m oc.storage.util.migrate-bookmark -f

  Use -s/--self-only to create bookmarks only for self created follow-ups."

  (:require [clojure.string :as s]
            [rethinkdb.query :as r]
            [clojure.walk :refer (keywordize-keys)]
            [clojure.tools.cli :refer (parse-opts)]
            [defun.core :refer (defun-)]
            [oc.lib.db.migrations :as m]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [oc.lib.db.pool :as db]
            [oc.lib.db.common :as db-common]
            [oc.storage.resources.common :as common]
            [oc.storage.resources.entry :as entry]
            [oc.storage.resources.org :as org]
            [oc.storage.config :as c])
  (:gen-class))

(def temp-index-name "org-uuid-status-follow-ups-completed?-multi")

(defn- create-temp-index [conn]
  (println (m/create-compound-index conn entry/table-name temp-index-name
    (r/fn [row] (r/map (r/get-field row "follow-ups")
                   (r/fn [follow-up-row]
                     [(r/get-field row "org-uuid")
                      (r/get-field row "status")
                      (r/get-field follow-up-row "completed?")])))
    {:multi true})))

(defn- delete-temp-index [conn]
  (println (m/remove-index conn entry/table-name temp-index-name)))

(defn- entries-for-org [conn org-uuid]
  (db-common/read-resources conn entry/table-name temp-index-name [[org-uuid :published false]] [:uuid :follow-ups :bookmarks]))

(defn index-of
  "Given a collection and a function return the index that make the function truely."
  [s f]
  (loop [idx 0 items s]
    (cond
      (empty? items) nil
      (f (first items)) idx
      :else (recur (inc idx) (rest items)))))

(defn- migrate-follow-ups-for-entry [conn e self-only? dry-run?]
  (let [follow-ups (:follow-ups e)
        new-bookmarks (remove nil? (mapv #(when (and (not (:completed? %))
                                           (or (not self-only?)
                                               (and self-only?
                                                    (= (-> % :assignee :user-id) (-> % :author :user-id)))))
                                    (:assignee %))
                            follow-ups))
        updated-bookmarks (clojure.set/union (set (map :user-id new-bookmarks)) (:bookmarks e))
        updated-follow-ups (mapv #(if (and (not (:completed? %))
                                             (or (not self-only?)
                                                 (and self-only?
                                                      (= (-> % :assignee :user-id) (-> % :author :user-id)))))
                                      (assoc % :completed? true)
                                      %)
                              follow-ups)]
    (println "  Updating entry:" (:uuid e))
    (println "    Adding bookmarks for:" updated-bookmarks)
    (println "    Updating follow-ups:")
    (doseq [f updated-follow-ups]
      (println "     -" (:uuid f) "completed?" (:completed? f) "self?" (= (-> f :assignee :user-id) (-> f :author :user-id))))
    (when-not dry-run?
      (println "  Actually updating entry:" (:uuid e))
      (entry/update-entry-no-user! conn (:uuid e) (merge e {:follow-ups updated-follow-ups
                                                            :bookmarks updated-bookmarks})))))

(defn- exit [status msg]
  (println msg)
  (System/exit status))

(def cli-options
  [["-f" "--force-update"]
   ["-s" "--self-only"]])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (println "Running bookmarks migration with options: dry-run" (not (:force-update options)) "self-only" (:self-only options))
    (let [conn (db/init-conn c/db-options)]
      (try
        (create-temp-index conn)
        (let [orgs (org/list-orgs conn)]
          (doseq [o orgs]
            (let [entries (entries-for-org conn (:uuid o))]
              (println "Org:" (:uuid o) "-" (:name o))
              (doseq [e entries]
                (migrate-follow-ups-for-entry conn e (:self-only options) (not (:force-update options)))))))
        (delete-temp-index conn)
        (exit 0 "\n")

        (catch Exception e
          (println e)
          (delete-temp-index conn)
          (exit 1 "Bookmark migration exception."))))))