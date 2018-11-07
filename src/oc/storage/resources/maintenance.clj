(ns oc.storage.resources.maintenance
  "fns to maintain storage resources."
  (:require [schema.core :as schema]
            [taoensso.timbre :as timbre]
            [clojure.pprint :as pp]
            [oc.lib.db.common :as db-common]
            [oc.lib.schema :as lib-schema]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.common :as common]))

(defn- update-entry [conn entry original-entry]
  (schema/validate common/Entry entry)
  (timbre/info "--> Updating entry from" (count (:shared original-entry)) "to" (count (:shared entry)))
  (db-common/update-resource conn common/entry-table-name :uuid original-entry entry (db-common/current-timestamp)))

(schema/defn ^:always-validate shared-limit-for-entry!
  "Given a RethinkDB connection, an entry map and a limit to apply to share cut the list of
   shared to the passed value keeping only the newest"
  [conn :- lib-schema/Conn entry :- common/Entry limit]
  (let [old-shared (:shared entry)
        grouped-shared (group-by :shared-at old-shared)
        unique-shared (into [] (map first (vals grouped-shared)))
        sorted-shared (reverse (sort-by :shared-at unique-shared))
        limited-shared (take limit sorted-shared)]
    (timbre/info "      Old shared" (count old-shared))
    (timbre/info "      Unique shared" (count unique-shared))
    (timbre/info "      Limited shared" (count limited-shared))
    (update-entry conn (assoc entry :shared limited-shared) entry)
    (timbre/info "      Cut!")))

(schema/defn ^:always-validate list-all-entries-by-org!
  [conn :- lib-schema/Conn org-uuid :- lib-schema/UniqueID]
  (db-common/read-resources conn common/entry-table-name :status-org-uuid [[:published org-uuid]]))

(schema/defn ^:always-validate shared-limit-for-org!
  "Give a RethinkDB connection and an org-uuid, load all the published entries of the org
   and call the shared-limit-for-entry on all that are exceeding the shared limit."
  [conn :- lib-schema/Conn org-uuid :- lib-schema/UniqueID limit]
  (timbre/info "  Cut for org" org-uuid)
  (let [entries (list-all-entries-by-org! conn org-uuid)]
    (timbre/info "  Entries count:" (count entries))
    (for [entry entries]
      (do
        (timbre/info "    Checking entry" (:uuid entry) "shared:" (count (:shared entry)))
        (when (> (count (:shared entry)) limit)
          (shared-limit-for-entry! conn entry limit))))))

(defn shared-limit!
  "List all the present orgs, for each load all the entries that have more then 20 shared entries
   and cut them to the 20 more recent values."
  [conn batch-length batch-offset & [limit]]
  (let [limit (or limit 50)]
    (timbre/info "Cut :shared lists for all entries to:" limit "(orgs from " (* batch-length batch-offset) "to" (* batch-length (inc batch-offset)) ")")
    (let [orgs (org-res/list-orgs conn)
          batches (into [] (partition batch-length orgs))
          batch (into [] (get batches batch-offset))]
      (for [org batch]
        (shared-limit-for-org! conn (:uuid org) limit)))))