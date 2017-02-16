(ns oc.storage.lib.email
  (:require [amazonica.aws.sqs :as sqs]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [oc.storage.config :as config]))

(def EmailTrigger
  {:to [schema/Str]
   :subject schema/Str
   :note schema/Str
   :reply-to (schema/maybe schema/Str)
   :company-slug schema/Str
   :snapshot {schema/Keyword schema/Any}
   :origin-url schema/Str})

(defn ctx->trigger [post-data {company :company user :user su :stakeholder-update :as ctx}]
  {:pre [
    (sequential? (:to post-data))
    (string? (:subject post-data))
    (string? (:note post-data))
    (map? company)
    (map? user)
    (map? su)]}
  {:to (:to post-data)
   :subject (:subject post-data)
   :note (:note post-data)
   :reply-to (:email user)
   :company-slug (:slug company)
   :snapshot su
   :origin-url (get-in ctx [:request :headers "origin"])})

(defn send-trigger! [trigger]
  (timbre/info "Request to send msg to " config/aws-sqs-email-queue "\n" (dissoc trigger :snapshot))
  (schema/validate EmailTrigger trigger)
  (timbre/info "Sending")
  (sqs/send-message
   {:access-key config/aws-access-key-id
    :secret-key config/aws-secret-access-key}
   config/aws-sqs-email-queue
   trigger))