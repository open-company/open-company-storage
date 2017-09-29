(ns oc.storage.async.change
  (:require [amazonica.aws.sqs :as sqs]
            [cheshire.core :as json]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.storage.config :as config]))

(def ChangeTrigger
  {:type (schema/enum "change")
   :container-id lib-schema/UniqueID
   :content-id lib-schema/UniqueID
   :change-at lib-schema/ISO8601})

(defn ->trigger [content]
  {:type "change"
   :content-id (:uuid content)
   :container-id (:board-uuid content)
   :change-at (or (:published-at content) (:created-at content))})

(defn send-trigger! [trigger]
  (timbre/info "Change notification request to queue:" config/aws-sqs-change-queue)
  (timbre/trace "Change request:" trigger)
  (schema/validate ChangeTrigger trigger)
  (timbre/info "Sending request to queue:" config/aws-sqs-change-queue)
  (sqs/send-message
    {:access-key config/aws-access-key-id
     :secret-key config/aws-secret-access-key}
    config/aws-sqs-change-queue
    (json/generate-string trigger {:pretty true}))
  (timbre/info "Request sent to:" config/aws-sqs-change-queue))