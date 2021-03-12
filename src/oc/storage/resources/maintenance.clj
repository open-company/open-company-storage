(ns oc.storage.resources.maintenance
  "fns to maintain storage resources."
  (:require [schema.core :as schema]
            [taoensso.timbre :as timbre]
            [oc.lib.db.common :as db-common]
            [oc.lib.schema :as lib-schema]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.common :as common]))

(defn- update-entry [conn entry original-entry dry-run]
  (schema/validate common/Entry entry)
  (when-not dry-run
    (timbre/info "--> Updating entry from" (count (:shared original-entry)) "to" (count (:shared entry)))
    (db-common/update-resource conn common/entry-table-name :uuid original-entry entry (db-common/current-timestamp))))

(schema/defn ^:always-validate shared-dedup-and-limit-for-entry!
  "Given a RethinkDB connection, an entry map and a limit to apply to share cut the list of
   shared to the passed value keeping only the newest"
  [conn entry :- common/Entry limit dry-run]
  {:pre [(db-common/conn? conn)]}
  (let [old-shared (:shared entry)
        grouped-shared (group-by :shared-at old-shared)
        unique-shared (vec (map first (vals grouped-shared)))
        sorted-shared (reverse (sort-by :shared-at unique-shared))
        limited-shared (take limit sorted-shared)]
    (timbre/info "      Old shared" (count old-shared))
    (timbre/info "      Unique shared" (count unique-shared))
    (timbre/info "      Limited shared" (count limited-shared))
    (update-entry conn (assoc entry :shared limited-shared) entry dry-run)
    (timbre/info "      Cut!")))

(schema/defn ^:always-validate list-all-entries-by-org!
  [conn org-uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/read-resources conn common/entry-table-name :status-org-uuid [[:published org-uuid]]))

(schema/defn ^:always-validate shared-limit-for-org!
  "Give a RethinkDB connection and an org-uuid, load all the published entries of the org
   and call the shared-limit-for-entry on all that are exceeding the shared limit."
  [conn org-uuid :- lib-schema/UniqueID limit dry-run]
  {:pre [(db-common/conn? conn)]}
  (let [entries (list-all-entries-by-org! conn org-uuid)]
    (timbre/info "    Entries count:" (count entries))
    (for [entry entries]
      (do
        (timbre/info "    Checking entry" (:uuid entry) "shared:" (count (:shared entry)))
        (when (> (count (:shared entry)) 1)
          (shared-dedup-and-limit-for-entry! conn entry limit dry-run))))))

(defn shared-limit!
  "Given a batch size and offset load the orgs of that batch and run the shared limit,
  use :dry-run option to see the eventual output, to actually update you need to specify
  :dry-run false.
  :batch-length - how many org per batch
  :batch-offset - the number of the batch: (tot-org / batch-length) * batch-offset
  :dry-run - do not update, only print
  :limit - number of shared to keep, default to 50"
  [conn {:keys [batch-length batch-offset limit dry-run]}]
  (let [batch-length (or batch-length 20)
        batch-offset (or batch-offset 0)
        dry-run (if (nil? dry-run)
                  true
                  dry-run)
        limit (or limit 50)]
    (timbre/info "Cut :shared lists for all entries to:" limit "(orgs from " (* batch-length batch-offset) "to" (dec (* batch-length (inc batch-offset))) ")")
    (when dry-run
      (timbre/info "---- Dry-run not updating ----"))
    (let [orgs (org-res/list-orgs conn)
          partition-length (min batch-length (count orgs))
          batches (vec (partition-all partition-length orgs))
          batch (vec (get batches batch-offset))]
      (timbre/info "Total orgs:" (count orgs) " batch: " batch-offset "/" (count batches))
      (timbre/info " Current batch size:" (count batch))
      (for [org batch]
        (do
          (timbre/info "  Checking:" (:slug org))
          (shared-limit-for-org! conn (:uuid org) limit dry-run))))))