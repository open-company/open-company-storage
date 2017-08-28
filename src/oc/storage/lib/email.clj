(ns oc.storage.lib.email
  (:require [amazonica.aws.sqs :as sqs]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.storage.config :as config]))

(def EmailTrigger
  {:type (schema/enum "story")
   :to [lib-schema/EmailAddress]
   :subject (schema/maybe schema/Str)
   :note (schema/maybe schema/Str)
   :reply-to (schema/maybe schema/Str)
   :org-slug lib-schema/NonBlankStr
   :org-name (schema/maybe schema/Str)
   :org-logo-url (schema/maybe schema/Str)
   :title (schema/maybe schema/Str)
   :published-at lib-schema/ISO8601
   :shared-at lib-schema/ISO8601})

(defn ->trigger [org story share-request user]
  {:type "story"
   :to (vec (:to share-request))
   :subject (:subject share-request)
   :note (:note share-request)
   :reply-to (:email user)
   :org-slug (:slug org)
   :org-name (:name org)
   :org-logo-url (:logo-url org)
   :title (:title story)
   :published-at (:published-at story)
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