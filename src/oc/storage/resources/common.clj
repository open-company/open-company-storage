(ns oc.storage.resources.common
  "Resources are any thing stored in the open company platform: orgs, boards, and entries"
  (:require [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.slugify :as slug]))

;; ----- RethinkDB metadata -----

(def org-table-name "orgs")
(def board-table-name "boards")
(def entry-table-name "entries")
(def interaction-table-name "interactions")

;; ----- Properties common to all resources -----

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{:id :slug :uuid :board-uuid :org-uuid :author :links :created-at :updated-at})

;; ----- Data Schemas -----

(def Slug "Valid slug used to uniquely identify a resource in a visible URL." (schema/pred slug/valid-slug?))

(def Attachment {
  :file-name lib-schema/NonBlankStr
  :file-type (schema/maybe schema/Str)
  :file-size schema/Num
  :file-url lib-schema/NonBlankStr
  :created-at lib-schema/ISO8601})

(def ContributingAuthor
  "An author in a sequence of Authors involved in creating content."
  (merge lib-schema/Author {:updated-at lib-schema/ISO8601}))

(def AccessLevel (schema/pred #(#{:private :team :public} (keyword %))))

(def Board
  "An container of entries."
  {
  :uuid lib-schema/UniqueID
  :slug Slug
  :name lib-schema/NonBlankStr
  :org-uuid lib-schema/UniqueID
  :access AccessLevel
  :authors [lib-schema/UniqueID]
  :viewers [lib-schema/UniqueID]
  (schema/optional-key :slack-mirror) (schema/maybe lib-schema/SlackChannel)
  :author lib-schema/Author
  (schema/optional-key :draft) schema/Bool
  :created-at lib-schema/ISO8601
  :updated-at lib-schema/ISO8601})

(def Org {
  :uuid lib-schema/UniqueID
  :name lib-schema/NonBlankStr
  :slug Slug
  :team-id lib-schema/UniqueID
  (schema/optional-key :logo-url) (schema/maybe schema/Str)
  (schema/optional-key :logo-width) schema/Int
  (schema/optional-key :logo-height) schema/Int
  :promoted schema/Bool
  :authors [lib-schema/UniqueID]
  :author lib-schema/Author
  :created-at lib-schema/ISO8601
  :updated-at lib-schema/ISO8601})

(def ShareMedium (schema/pred #(#{:email :slack} (keyword %))))

(def ShareRequest {
  :medium ShareMedium
  (schema/optional-key :note) (schema/maybe schema/Str)
  
  ;; Email medium
  (schema/optional-key :to) [lib-schema/EmailAddress]
  (schema/optional-key :subject) (schema/maybe schema/Str)
  
  ;; Slack medium
  (schema/optional-key :channel) lib-schema/SlackChannel

  :shared-at lib-schema/ISO8601})

(def Status (schema/pred #(#{:draft :published} (keyword %))))

(def Entry 
  "An entry on a board."
  {
  :uuid lib-schema/UniqueID
  :secure-uuid lib-schema/UniqueID
  :org-uuid lib-schema/UniqueID
  :board-uuid lib-schema/UniqueID
  :topic-slug (schema/maybe Slug)
  :topic-name (schema/maybe lib-schema/NonBlankStr)
  
  :status Status

  :headline schema/Str
  :body schema/Str
  
  ;; Attachments
  (schema/optional-key :attachments) [Attachment]

  ;; Comment sync
  (schema/optional-key :slack-thread) lib-schema/SlackThread

  :author [ContributingAuthor]
  (schema/optional-key :published-at) lib-schema/ISO8601
  (schema/optional-key :publisher) lib-schema/Author
  (schema/optional-key :must-see) schema/Bool
  (schema/optional-key :shared) [ShareRequest]

  (schema/optional-key :video-id) (schema/maybe lib-schema/NonBlankStr)
  (schema/optional-key :video-transcript) (schema/maybe schema/Str)
  (schema/optional-key :video-processed) (schema/maybe schema/Bool)
  (schema/optional-key :video-error) (schema/maybe schema/Bool)
  (schema/optional-key :revision-id) schema/Int
  :created-at lib-schema/ISO8601
  :updated-at lib-schema/ISO8601})

(def NewBoard
  "A new board for creation, can have new or existing entries already."
  (merge Board {
    :entries [Entry]}))

(def User
  "User info to notify via email/slack"
  {:updated-at lib-schema/ISO8601
   :email schema/Str
   :last-name schema/Str
   :avatar-url schema/Str
   :user-id lib-schema/UniqueID
   :first-name schema/Str
   :status schema/Str
   :created-at lib-schema/ISO8601
   schema/Keyword schema/Any}) ; and whatever else