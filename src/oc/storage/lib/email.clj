(ns oc.storage.lib.email
  (:require [amazonica.aws.sqs :as sqs]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.storage.config :as config]
            [oc.storage.resources.common :as common-res]))

(def EmailTrigger
  {:type (schema/enum "story")
   :to [schema/Str]
   :subject schema/Str
   :note schema/Str
   :reply-to (schema/maybe schema/Str)
   :slug schema/Str
   :org-slug schema/Str
   :org-name schema/Str
   :logo-url (schema/maybe schema/Str)
   :origin-url schema/Str
   :created-at lib-schema/ISO8601})

(defn ->trigger [org-slug story origin-url user]
  {:type "story"
   :to (vec (:to story))
   :subject (:subject story)
   :note (:note story)
   :reply-to (:email user)
   :slug (:slug story)
   :org-slug org-slug
   :origin-url origin-url
   :org-name (:org-name story)
   :logo-url (:logo-url story)
   :currency (:currency story)
   :entries (:entries story)
   :created-at (:created-at story)})

(defn send-trigger! [trigger]
  (timbre/info "Email request to:" config/aws-sqs-email-queue "\n" (dissoc trigger :entries))
  (schema/validate EmailTrigger trigger)
  (timbre/info "Sending request to:" config/aws-sqs-email-queue)
  (sqs/send-message
   {:access-key config/aws-access-key-id
    :secret-key config/aws-secret-access-key}
   config/aws-sqs-email-queue
   trigger)
  (timbre/info "Request sent to:" config/aws-sqs-email-queue))