(ns oc.storage.lib.email
  (:require [amazonica.aws.sqs :as sqs]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [oc.storage.config :as config]
            [oc.storage.resources.common :as common-res]))

(def EmailTrigger
  {:to [schema/Str]
   :subject schema/Str
   :note schema/Str
   :reply-to (schema/maybe schema/Str)
   :org-slug schema/Str
   :origin-url schema/Str
   :entries [common-res/UpdateEntry]})

(defn ->trigger [org-slug update origin-url user]
  {:to (vec (:to update))
   :subject (:subject update)
   :note (:note update)
   :reply-to (:email user)
   :org-slug org-slug
   :origin-url origin-url
   :entries (:entries update)})

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