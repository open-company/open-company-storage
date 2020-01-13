(ns oc.storage.util.migrate-bookmarks
  "Get all the existing uncompleted follow-ups and create a bookmark for every one of them.
   To perform a dry run and simulate the update use:

  lein run -m oc.storage.util.migrate-bookmarks

  To actually update the entries:

  lein run -m oc.storage.util.migrate-bookmarks -f

  Use -c/--complete-follow-ups to mark follow-up as completed when creating the bookmark.

  Use -s/--self-only to create bookmarks only for self created follow-ups."

  (:require [rethinkdb.query :as r]
            [clojure.tools.cli :refer (parse-opts)]
            [oc.lib.db.migrations :as m]
            [oc.lib.db.pool :as db]
            [oc.lib.db.common :as db-common]
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

(defn- unique-entries-for-org [conn org-uuid]
  (let [all-entries (db-common/read-resources conn entry/table-name temp-index-name
                     [[org-uuid :published false]] [:uuid :follow-ups :bookmarks])
        all-uuids (vec (set (map :uuid all-entries)))
        unique-entries (map (fn [entry-uuid]
                              (first (filter #(= (:uuid %) entry-uuid) all-entries)))
                        all-uuids)]
    (println "   count all-entries" (count all-entries))
    (println "   count all-uuids" (count all-uuids))
    (println "   count unique-entries" (count unique-entries))
    unique-entries))

(defn index-of
  "Given a collection and a function return the index that make the function truely."
  [s f]
  (loop [idx 0 items s]
    (cond
      (empty? items) nil
      (f (first items)) idx
      :else (recur (inc idx) (rest items)))))

(defn- migrate-follow-ups-for-entry [conn e self-only? dry-run? complete-follow-ups?]
  (let [follow-ups (:follow-ups e)
        new-bookmarks (remove nil? (mapv #(when (and (not (:completed? %))
                                           (or (not self-only?)
                                               (and self-only?
                                                    (= (-> % :assignee :user-id) (-> % :author :user-id)))))
                                    (:assignee %))
                            follow-ups))
        updated-bookmarks (vec (clojure.set/union (set (map :user-id new-bookmarks)) (:bookmarks e)))
        updated-follow-ups (when complete-follow-ups?
                             (mapv #(if (and (not (:completed? %))
                                               (or (not self-only?)
                                                   (and self-only?
                                                        (= (-> % :assignee :user-id) (-> % :author :user-id)))))
                                        (assoc % :completed? true)
                                        %)
                                follow-ups))]
    (println "  Updating entry:" (:uuid e))
    (println "    adding bookmarks:" updated-bookmarks)
    (when complete-follow-ups?
      (println "    updating follow-ups:")
      (doseq [f updated-follow-ups]
        (println "     -" (:uuid f) "completed?" (:completed? f) "self?" (= (-> f :assignee :user-id) (-> f :author :user-id)))))
    (when-not dry-run?
      (if complete-follow-ups?
        (println "    Actually updating entry's bookmarks and follow-ups:" (:uuid e))
        (println "    Actually updating entry's bookmarks:" (:uuid e)))
      (entry/update-entry-no-user! conn (:uuid e) (merge e (if complete-follow-ups?
                                                             {:follow-ups updated-follow-ups
                                                              :bookmarks updated-bookmarks}
                                                             {:bookmarks updated-bookmarks}))))))

(defn- exit [status msg]
  (println msg)
  (System/exit status))

(def cli-options
  [["-f" "--force-update"]
   ["-c" "--complete-follow-ups"] ;; Use with -f
   ["-s" "--self-only"]])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (println "Running bookmarks migration with options: dry-run" (not (:force-update options))
      "self-only" (:self-only options)
      "complete-follow-ups" (:complete-follow-ups options))
    (let [conn (db/init-conn c/db-options)]
      (try
        (create-temp-index conn)
        (let [orgs (org/list-orgs conn)]
          (println " Total orgs:" (count orgs))
          (doseq [o orgs]
            (println " Org:" (:uuid o) "-" (:name o))
            (let [entries (unique-entries-for-org conn (:uuid o))]
              (doseq [e entries]
                (migrate-follow-ups-for-entry conn e
                 (:self-only options)                 ; Only self created follow-ups
                 (not (:force-update options))        ; Make changes in DB, no dry-run
                 (:complete-follow-ups options)       ; Complete the relative follow-up,
                 )))))                                ; use this to keep them to re-run the import
        (delete-temp-index conn)
        (exit 0 "\n")

        (catch Exception e
          (println e)
          (delete-temp-index conn)
          (exit 1 "Bookmark migration exception."))))))
