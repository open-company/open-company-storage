(ns oc.storage.async.email
  "Publish email triggers to AWS SQS."
  (:require [amazonica.aws.sqs :as sqs]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.storage.config :as config]))

(def EmailTrigger
  {:type (schema/enum "share-entry")
   :to [lib-schema/EmailAddress]
   :subject (schema/maybe schema/Str)
   :note (schema/maybe schema/Str)
   :reply-to (schema/maybe schema/Str)
   :sharer-name (schema/maybe schema/Str)
   :org-slug lib-schema/NonBlankStr
   :org-name (schema/maybe schema/Str)
   :org-logo-url (schema/maybe schema/Str)
   :org-logo-width (schema/maybe schema/Int)
   :org-logo-height (schema/maybe schema/Int)
   :board-name (schema/maybe schema/Str)
   :headline schema/Str
   :body (schema/maybe schema/Str)
   :secure-uuid lib-schema/UniqueID
   :publisher lib-schema/Author
   :published-at lib-schema/ISO8601
   :shared-at lib-schema/ISO8601})

(defn ->trigger [org board entry share-request user]
  {:type "share-entry"
   :to (vec (:to share-request))
   :subject (:subject share-request)
   :note (:note share-request)
   :reply-to (:email user)
   :sharer-name (:name user)
   :org-slug (:slug org)
   :org-name (:name org)
   :org-logo-url (:logo-url org)
   :org-logo-width (:logo-width org)
   :org-logo-height (:logo-height org)
   :board-name (:name board)
   :headline (:headline entry)
   :body (:body entry)
   :secure-uuid (:secure-uuid entry)
   :publisher (lib-schema/author-for-user (:publisher entry))
   :published-at (:published-at entry)
   :shared-at (:shared-at share-request)})

(defn send-trigger! [trigger]
  (timbre/info "Email request to queue:" config/aws-sqs-email-queue)
  (timbre/trace "Email request:" (dissoc trigger :entries))
  (schema/validate EmailTrigger trigger)
  (timbre/info "Sending request to queue:" config/aws-sqs-email-queue)
  (sqs/send-message
   {:access-key config/aws-access-key-id
    :secret-key config/aws-secret-access-key}
   config/aws-sqs-email-queue
   trigger)
  (timbre/info "Request sent to:" config/aws-sqs-email-queue))