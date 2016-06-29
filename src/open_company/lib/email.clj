(ns open-company.lib.email
  (:require [amazonica.aws.sqs :as sqs]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [open-company.config :as c]))

(def EmailTrigger
  {:to schema/Str
   :subject schema/Str
   :note schema/Str
   :snapshot {schema/Keyword schema/Any}})

(defn ctx->trigger [post-data ctx]
  {:pre [
    (string? (:to post-data))
    (string? (:subject post-data))
    (string? (:note post-data))
    (map? (:company ctx))
    (map? (:user ctx))
    (map? (:stakeholder-update ctx))]}
  {:to (:to post-data)
   :subject (:subject post-data)
   :note (:note post-data)
   :snapshot (:stakeholder-update ctx)})

(defn send-trigger! [trigger]
  (timbre/info "Request to send msg to " c/aws-sqs-email-queue "\n" (dissoc trigger :snapshot))
  (schema/validate EmailTrigger trigger)
  (timbre/info "Sending")
  (sqs/send-message
   {:access-key c/aws-access-key-id
    :secret-key c/aws-secret-access-key}
   c/aws-sqs-email-queue
   trigger))