(ns oc.storage.resources.entry
  (:require [clojure.walk :refer (keywordize-keys)]
            [if-let.core :refer (if-let* when-let*)]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.db.common :as db-common]
            [oc.lib.slugify :as slugify]
            [oc.storage.config :as config]
            [oc.storage.resources.common :as common]
            [oc.storage.resources.board :as board-res]))

(def temp-uuid "9999-9999-9999")

;; ----- RethinkDB metadata -----

(def table-name common/entry-table-name)
(def versions-table-name (str "versions_" common/entry-table-name))
(def versions-primary-key :version-uuid)
(def primary-key :uuid)

;; ----- Metadata -----

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  (clojure.set/union common/reserved-properties #{:board-slug :topic-slug :published-at :publisher :secure-uuid}))

(def ignored-properties
  "Properties of a resource that are ignored during an update."
  (disj reserved-properties :board-uuid :status))

;; ----- Utility functions -----

(defn clean
  "Remove any reserved properties from the entry."
  [entry]
  (apply dissoc entry reserved-properties))

(defn ignore-props
  "Remove any ignored properties from the entry."
  [entry]
  (apply dissoc entry ignored-properties))

(defn- publish-props
  "Provide properties for an initially published entry."
  [entry timestamp author]
  (if (= (keyword (:status entry)) :published)
    (-> entry
      (assoc :published-at timestamp)
      (assoc :publisher author))
    entry))

(defn timestamp-attachments
  "Add a `:created-at` timestamp with the specified value to any attachment that's missing it."
  ([attachments] (timestamp-attachments attachments (db-common/current-timestamp)))
 
  ([attachments timestamp]
  (map #(if (:created-at %) % (assoc % :created-at timestamp)) attachments)))

;; ----- Entry CRUD -----

(schema/defn ^:always-validate ->entry :- common/Entry
  "
  Take a board UUID, a minimal map describing an Entry, and a user (as the author) and
  'fill the blanks' with any missing properties.

  Throws an exception if the board specified in the entry can't be found.
  "
  [conn board-uuid :- lib-schema/UniqueID entry-props user :- lib-schema/User]
  {:pre [(db-common/conn? conn)
         (map? entry-props)]}
  (if-let [board (if (= board-uuid temp-uuid) 
                    {:org-uuid temp-uuid} ; board doesn't exist yet, we're checking if this entry will be valid
                    (board-res/get-board conn board-uuid))]
    (let [topic-name (:topic-name entry-props)
          topic-slug (when topic-name (slugify/slugify topic-name))
          ts (db-common/current-timestamp)
          author (lib-schema/author-for-user user)]
      (-> entry-props
          keywordize-keys
          clean
          (assoc :uuid (db-common/unique-id))
          (assoc :secure-uuid (db-common/unique-id))
          (update :status #(or % "draft"))
          (assoc :topic-slug topic-slug)
          (update :topic-name #(or % nil))
          (update :headline #(or % ""))
          (update :body #(or % ""))
          (update :attachments #(timestamp-attachments % ts))
          (assoc :org-uuid (:org-uuid board))
          (assoc :board-uuid board-uuid)
          (assoc :author [(assoc author :updated-at ts)])
          (assoc :created-at ts)
          (assoc :updated-at ts)
          (publish-props ts author)))
    (throw (ex-info "Invalid board uuid." {:board-uuid board-uuid})))) ; no board

(declare update-entry)

(defn- create-version [conn updated-entry original-entry]
  (let [ts (db-common/current-timestamp)
        revision-id (:revision-id original-entry)
        revision-id-new (inc revision-id)
        revision (-> original-entry
                     (assoc :version-uuid (str (:uuid original-entry)
                                               "-v" revision-id))
                     (assoc :revision-date ts)
                     (assoc :revision-author (first (:author original-entry))))]
    (when-not (:deleted original-entry)
      (update-entry conn
                    (assoc updated-entry :revision-id revision-id-new)
                    updated-entry
                    ts))
    (db-common/create-resource conn versions-table-name revision ts)))

(declare get-entry)

(defn- delete-version [conn uuid]
  (let [entry (get-entry conn uuid)
        revision-id (if (zero? (:revision-id entry))
                      (inc (:revision-id entry))
                      (:revision-id entry))]
    (create-version conn entry (-> entry
                                   (assoc :revision-id revision-id)
                                   (assoc :deleted true)))))

(schema/defn ^:always-validate create-entry! :- (schema/maybe common/Entry)
  "
  Create an entry for the board. Returns the newly created entry.

  Throws a runtime exception if the provided entry doesn't conform to the
  common/Entry schema. Throws an exception if the board specified in the entry can't be found.
  "
  ([conn entry :- common/Entry] (create-entry! conn entry (db-common/current-timestamp)))

  ([conn entry :- common/Entry ts :- lib-schema/ISO8601]
  {:pre [(db-common/conn? conn)]}
  (if-let* [board-uuid (:board-uuid entry)
            board (board-res/get-board conn board-uuid)]
    (let [stamped-entry (if (= (keyword (:status entry)) :published)
                              (assoc entry :published-at ts)
                              entry)
          author (assoc (first (:author entry)) :updated-at ts)] ; update initial author timestamp
      ;; create the entry
      (let [new-entry (db-common/create-resource conn table-name (assoc stamped-entry :author [author]) ts)]
           ;; first version
           (update-entry conn (assoc new-entry :revision-id 0) new-entry ts)))
    (throw (ex-info "Invalid board uuid." {:board-uuid (:board-uuid entry)}))))) ; no board

(schema/defn ^:always-validate get-entry :- (schema/maybe common/Entry)
  "
  Given the UUID of the entry, retrieve the entry, or return nil if it doesn't exist.

  Or given the UUID of the org, board, entry, retrieve the entry, or return nil if it doesn't exist. This variant 
  is used to confirm that the entry belongs to the specified org and board.
  "
  ([conn uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/read-resource conn table-name uuid))

  ([conn org-uuid :- lib-schema/UniqueID board-uuid :- lib-schema/UniqueID uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (first (db-common/read-resources conn table-name :uuid-board-uuid-org-uuid [[uuid board-uuid org-uuid]]))))

(schema/defn ^:always-validate get-entry-by-secure-uuid :- (schema/maybe common/Entry)
  "
  Given the secure UUID of the entry, retrieve the entry, or return nil if it doesn't exist.

  Or given the UUID of the org, and entry, retrieve the entry, or return nil if it doesn't exist. This variant 
  is used to confirm that the entry belongs to the specified org.
  "
  ([conn secure-uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (first (db-common/read-resources conn table-name :secure-uuid secure-uuid)))

  ([conn org-uuid :- lib-schema/UniqueID secure-uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (first (db-common/read-resources conn table-name :secure-uuid-org-uuid [[secure-uuid org-uuid]]))))

(schema/defn ^:always-validate get-entry-by-video :- (schema/maybe common/Entry)
  "
  Given the video token in the entry, retrieve the entry, or return nil if it doesn't exist.
  "
  ([conn video-id :- schema/Str]
  {:pre [(db-common/conn? conn)]}
  (first (db-common/read-resources conn table-name :video-id video-id))))

(defn- new-topic-name [entry original-entry]
  (let [new-topic-name (:topic-name entry)]
    (if (clojure.string/blank? new-topic-name)
      (when-not (contains? entry :topic-name)
        (:topic-name original-entry)) ;; keep prior topic name
      ;; new topic name provided
      new-topic-name)))

(defn- update-entry [conn entry original-entry ts]
  (let [topic-name (new-topic-name entry original-entry)
        topic-slug (when topic-name (slugify/slugify topic-name))
        merged-entry (merge original-entry (ignore-props entry))
        topic-named-entry (assoc merged-entry :topic-name topic-name)
        slugged-entry (assoc topic-named-entry :topic-slug topic-slug)
        attachments (:attachments merged-entry)
        updated-entry (assoc slugged-entry :attachments (timestamp-attachments attachments ts))]
    (schema/validate common/Entry updated-entry)
    (db-common/update-resource conn table-name primary-key original-entry updated-entry ts)))

(schema/defn ^:always-validate update-entry-no-user! :- (schema/maybe common/Entry)
  "
  Given the UUID of the entry, an updated entry property map, update the entry
  and return the updated entry on success.

  Throws an exception if the merge of the prior entry and the updated entry property map doesn't conform
  to the common/Entry schema.
  "
  [conn uuid :- lib-schema/UniqueID entry]
  {:pre [(db-common/conn? conn)
         (map? entry)]}
  (if-let [original-entry (get-entry conn uuid)]
    (update-entry conn entry original-entry (db-common/current-timestamp))))

(schema/defn ^:always-validate update-entry! :- (schema/maybe common/Entry)
  "
  Given the UUID of the entry, an updated entry property map, and a user (as the author), update the entry and
  return the updated entry on success.

  Throws an exception if the merge of the prior entry and the updated entry property map doesn't conform
  to the common/Entry schema.
  "
  [conn uuid :- lib-schema/UniqueID entry user :- lib-schema/User]
  {:pre [(db-common/conn? conn)         
         (map? entry)]}
  (if-let [original-entry (get-entry conn uuid)]
    (let [authors (:author original-entry)
          ts (db-common/current-timestamp)
          updated-authors (concat authors [(assoc (lib-schema/author-for-user user) :updated-at ts)])
          updated-entry (assoc entry :author updated-authors)]
      (let [updated-entry (update-entry conn updated-entry original-entry ts)]
        ;; copy current version to versions table, increment revision uuid
        (create-version conn updated-entry original-entry)
        updated-entry))))

(defn upsert-entry!
  "
  If entry is found update otherwise create the new entry.
  "
  [conn entry user]
  (if-let [original-entry (get-entry conn (:uuid entry))]
    (update-entry! conn (:uuid entry) entry user)
    (create-entry! conn entry)))

(defn error-video-data [conn entry]
  (update-entry-no-user! conn
                         (:uuid entry)
                         (-> entry
                             (assoc :video-processed nil)
                             (assoc :video-transcript nil)
                             (assoc :video-error true))))

(defn update-video-data [conn video entry]
  (let [video-processed (if (:video-processed entry)
                          true
                          (> (:state video) 4)) ;; is video processed?
        video-transcript-data (get-in video [:original_stream :audio_transcription :text])
        video-transcript (or video-transcript-data
                             (:video-transcript entry))
        video-error (if video-processed
                      false
                      (:video-error entry))]
    (update-entry-no-user! conn
                           (:uuid entry)
                           (-> entry
                               (assoc :video-error video-error)
                               (assoc :video-processed video-processed)
                               (assoc :video-transcript video-transcript)))))

(schema/defn ^:always-validate publish-entry! :- (schema/maybe common/Entry)
  "
  Given the UUID of the entry, an optional updated entry map, and a user (as the publishing author),
  publish the entry and return the updated entry on success.
  "
  ([conn uuid :- lib-schema/UniqueID user :- lib-schema/User] (publish-entry! conn uuid {} user))

  ([conn uuid :- lib-schema/UniqueID entry-props user :- lib-schema/User]
  {:pre [(db-common/conn? conn)
         (map? entry-props)]}
  (if-let [original-entry (get-entry conn uuid)]
    (let [authors (:author original-entry)
          ts (db-common/current-timestamp)
          publisher (lib-schema/author-for-user user)
          merged-entry (merge original-entry entry-props {:status :published
                                                          :published-at ts
                                                          :publisher publisher
                                                          :secure-uuid (db-common/unique-id)})
          updated-authors (conj authors (assoc publisher :updated-at ts))
          entry-update (assoc merged-entry :author updated-authors)]
      (schema/validate common/Entry entry-update)
      (let [updated-entry (db-common/update-resource conn table-name primary-key original-entry entry-update ts)]
        ;; copy current version to versions table, increment revision uuid
        (create-version conn updated-entry entry-update)
        ;; Delete the draft entry's interactions
        (db-common/delete-resource conn common/interaction-table-name :resource-uuid uuid)
        updated-entry)))))

(schema/defn ^:always-validate delete-entry!
  "Given the UUID of the entry, delete the entry and all its interactions. Return `true` on success."
  [conn uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/delete-resource conn common/interaction-table-name :resource-uuid uuid)
  ;; update versions table as deleted (logical delete)
  (delete-version conn uuid)
  (db-common/delete-resource conn table-name uuid))

(schema/defn ^:always-validate list-comments-for-entry
  "Given the UUID of the entry, return a list of the comments for the entry."
  [conn uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (filter :body (db-common/read-resources conn common/interaction-table-name "resource-uuid" uuid [:uuid :author :body :created-at])))

(schema/defn ^:always-validate list-reactions-for-entry
  "Given the UUID of the entry, return a list of the reactions for the entry."
  [conn uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (filter :reaction (db-common/read-resources conn common/interaction-table-name "resource-uuid" uuid [:uuid :author :reaction :created-at])))

;; ----- Collection of entries -----

(schema/defn ^:always-validate list-entries-by-org
  "
  Given the UUID of the org, an order, one of `:asc` or `:desc`, a start date as an ISO8601 timestamp, 
  and a direction, one of `:before` or `:after`, return the published entries for the org with any interactions.
  "
  [conn org-uuid :- lib-schema/UniqueID order start :- lib-schema/ISO8601 direction allowed-boards :- [lib-schema/UniqueID] {:keys [must-see count] :or {must-see false count false}}]
  {:pre [(db-common/conn? conn)
          (#{:desc :asc} order)
          (#{:before :after} direction)]}
  (let [filter-map (if-not must-see
                     [{:fn :contains :value allowed-boards :field :board-uuid}]
                     [{:fn :contains :value allowed-boards :field :board-uuid}
                      {:fn :eq :field :must-see :value (boolean (#{true "true"} must-see))}]
                     )]
    (db-common/read-resources-and-relations conn table-name
      :status-org-uuid [[:published org-uuid]]
      "published-at" order start direction config/default-activity-limit
      filter-map
      :interactions common/interaction-table-name :uuid :resource-uuid
      ["uuid" "headline" "body" "reaction" "author"
       "published-at" "created-at" "updated-at"] {:count count})))

(schema/defn ^:always-validate list-entries-by-org-author
  "
  Given the UUID of the org, an order, one of `:asc` or `:desc`, a start date as an ISO8601 timestamp, 
  and a direction, one of `:before` or `:after`, and an optional status of `:draft` or `:published` (the default)
  return the entries by the author with any interactions.
  "
  ([conn org-uuid :- lib-schema/UniqueID user-id {:keys [count] :or {count false}}]
    (list-entries-by-org-author conn org-uuid user-id :published {:count count}))

  ([conn org-uuid :- lib-schema/UniqueID user-id :- lib-schema/UniqueID status {:keys [count] :or {count false}}]
  {:pre [(db-common/conn? conn)
         (#{:published :draft} status)]}
  (db-common/read-resources-and-relations conn table-name :status-org-uuid-author-id [[status org-uuid user-id]]
                                          :interactions common/interaction-table-name :uuid :resource-uuid
                                          ["uuid" "headline" "body" "reaction" "author" "created-at" "updated-at"] {:count count})))

(schema/defn ^:always-validate list-entries-by-board
  "Given the UUID of the board, return the published entries for the board with any interactions."
  [conn board-uuid :- lib-schema/UniqueID {:keys [count] :or {count false}}]
  {:pre [(db-common/conn? conn)]}
  (db-common/read-resources-and-relations conn table-name :status-board-uuid [[:published board-uuid]]
                                          :interactions common/interaction-table-name :uuid :resource-uuid
                                          ["uuid" "headline" "body" "reaction" "author" "created-at" "updated-at"] {:count count}))

(schema/defn ^:always-validate list-all-entries-by-board
  "Given the UUID of the board, return all the entries for the board."
  [conn board-uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/read-resources conn table-name :board-uuid [board-uuid] ["uuid" "status"]))

;; ----- Data about entries -----

(schema/defn ^:always-validate entry-months-by-org
  "
  Given the UUID of the org, return an ordered sequence of all the months that have at least one entry.

  Response:

  [['2017' '06'] ['2017' '01'] [2016 '05']]

  Sequence is ordered, newest to oldest.
  "
  [conn org-uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/months-with-resource conn table-name :org-uuid org-uuid :published-at))

;; ----- Armageddon -----

(defn delete-all-entries!
  "Use with caution! Failure can result in partial deletes. Returns `true` if successful."
  [conn]
  {:pre [(db-common/conn? conn)]}
  ;; Delete all interactions and entries
  (db-common/delete-all-resources! conn common/interaction-table-name)
  (db-common/delete-all-resources! conn versions-table-name)
  (db-common/delete-all-resources! conn table-name))