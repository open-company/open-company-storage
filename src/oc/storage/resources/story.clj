(ns oc.storage.resources.story
  (:require [clojure.walk :refer (keywordize-keys)]
            [if-let.core :refer (if-let*)]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.db.common :as db-common]
            [oc.lib.slugify :as slugify]
            [oc.storage.config :as config]
            [oc.storage.resources.common :as common]
            [oc.storage.resources.board :as board-res]))

;; ----- RethinkDB metadata -----

(def table-name common/story-table-name)
(def primary-key :uuid)

; ;; ----- Metadata -----

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{:published-at})

; ;; ----- Utility functions -----

(defn- clean
  "Remove any reserved properties from the org."
  [update]
  (apply dissoc (common/clean update) reserved-properties))

; (schema/defn ^:always-validate entry-for :- common/UpdateEntry
;   "
;   Given an entry spec from a share request, get the specified entry from the DB.

;   Throws an exception if the entry isn't found.
;   "
;   [conn org-uuid entry]
;   (let [topic-slug (:topic-slug entry)
;         timestamp (:created-at entry)]
;     (if-let [entry (entry-res/get-entry conn org-uuid topic-slug timestamp)]
;       (dissoc entry :org-uuid :board-uuid :body-placeholder :slack-thread)
;       (throw (ex-info "Invalid entry." {:org-uuid org-uuid :topic-slug topic-slug :created-at timestamp})))))

;; ----- Slug -----

(defn- slug-for
  "Create a slug for the update from the slugified title and a short UUID."
  [title]
  (let [non-blank-title (if (clojure.string/blank? title) "update" title)]
    (str (slugify/slugify non-blank-title) "-" (subs (str (java.util.UUID/randomUUID)) 0 5))))

;; ----- Story CRUD -----

(schema/defn ^:always-validate ->story :- common/Story
  "
  Take a board UUID, a minimal map describing a Story, and a user (as the author) and
  'fill the blanks' with any missing properties.

  Throws an exception if the board specified in the story can't be found.
  "
  [conn board-uuid :- lib-schema/UniqueID story-props user :- lib-schema/User]
  {:pre [(db-common/conn? conn)
          (map? story-props)]}
  (if-let [board (board-res/get-board conn board-uuid)]
    (let [ts (db-common/current-timestamp)]
      (-> story-props
          keywordize-keys
          clean
          (assoc :uuid (db-common/unique-id))
          (assoc :slug (slug-for (:title story-props)))
          (assoc :org-uuid (:org-uuid board))
          (assoc :board-uuid board-uuid)
          (assoc :author [(assoc (lib-schema/author-for-user user) :updated-at ts)])
          (assoc :status :draft)
          (assoc :created-at ts)
          (assoc :updated-at ts)))
    (throw (ex-info "Invalid board uuid." {:board-uuid board-uuid})))) ; no board

(schema/defn ^:always-validate create-story! :- (schema/maybe common/Story)
  "
  Create a story for the board. Returns the newly created story.

  Throws a runtime exception if the provided story doesn't conform to the
  common/Story schema. Throws an exception if the board specified in the story can't be found.
  "
  ([conn story] (create-story! conn story (db-common/current-timestamp)))
  
  ([conn story :- common/Story ts :- lib-schema/ISO8601]
  {:pre [(db-common/conn? conn)]}
  (if-let* [board-uuid (:board-uuid story)
            board (board-res/get-board conn board-uuid)]
    (let [author (assoc (first (:author story)) :updated-at ts)] ; update initial author timestamp
      (db-common/create-resource conn table-name (assoc story :author [author]) ts)) ; create the story
    (throw (ex-info "Invalid board uuid." {:board-uuid (:board-uuid story)}))))) ; no board

(schema/defn ^:always-validate get-story :- (schema/maybe common/Story)
  "
  Given the UUID of the story, retrieve the story, or return nil if it doesn't exist.

  Or given the UUID of the org and board, and the slug of the story,
  retrieve the story, or return nil if it doesn't exist.
  "
  ([conn uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/read-resource conn table-name uuid))

  ([conn org-uuid :- lib-schema/UniqueID board-uuid :- lib-schema/UniqueID slug]
  {:pre [(db-common/conn? conn)
         (slugify/valid-slug? slug)]}
  (first (db-common/read-resources conn table-name :slug-board-uuid-org-uuid [[slug board-uuid org-uuid]]))))

(schema/defn ^:always-validate delete-story!
  "Given the UUID of the story, delete the story and all its interactions. Return `true` on success."
  [conn uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/delete-resource conn common/interaction-table-name :resource-uuid uuid)
  (db-common/delete-resource conn table-name uuid))

(schema/defn ^:always-validate list-comments-for-story
  "Given the UUID of the story, return a list of the comments for the story."
  [conn uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (filter :body (db-common/read-resources conn common/interaction-table-name "resource-uuid" uuid [:uuid :author :body])))

(schema/defn ^:always-validate list-reactions-for-story
  "Given the UUID of the story, return a list of the reactions for the story."
  [conn uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (filter :reaction (db-common/read-resources conn common/interaction-table-name "resource-uuid" uuid [:uuid :author :reaction])))

;; ----- Collection of stories -----

(schema/defn ^:always-validate list-stories-by-org
  "
  Given the UUID of the org, an order, one of `:asc` or `:desc`, a start date as an ISO8601 timestamp, 
  and a direction, one of `:before` or `:after`, return the published stories for the org with any interactions.
  "
  [conn org-uuid :- lib-schema/UniqueID order start :- lib-schema/ISO8601 direction]
  {:pre [(db-common/conn? conn)
          (#{:desc :asc} order)
          (#{:before :after} direction)]}
  (db-common/read-resources-and-relations conn table-name :status-org-uuid [[:published org-uuid]]
                                          "created-at" order start direction config/default-limit
                                          :interactions common/interaction-table-name :uuid :resource-uuid
                                          ["uuid" "body" "reaction" "author" "created-at" "updated-at"]))

(schema/defn ^:always-validate list-stories-by-board
  "Given the UUID of the board, return the stories for the board with any interactions."
  [conn board-uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/read-resources-and-relations conn table-name :status-board-uuid [[:published board-uuid]]
                                          :interactions common/interaction-table-name :uuid :resource-uuid
                                          ["uuid" "body" "reaction" "author" "created-at" "updated-at"]))

;; ----- Data about stories -----

(schema/defn ^:always-validate story-months-by-org
  "
  Given the UUID of the org, return an ordered sequence of all the months that have at least one published story.

  Response:

  [['2017' '06'] ['2017' '01'] [2016 '05']]

  Sequence is ordered, newest to oldest.
  "
  [conn org-uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/months-with-resource conn table-name :status-org-uuid [[:published org-uuid]] :created-at))

;; ----- Armageddon -----

(defn delete-all-stories!
  "Use with caution! Failure can result in partial deletes. Returns `true` if successful."
  [conn]
  {:pre [(db-common/conn? conn)]}
  ;; Delete all stories
  (db-common/delete-all-resources! conn table-name))