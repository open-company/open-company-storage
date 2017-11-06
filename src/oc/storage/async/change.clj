(ns oc.storage.async.change
  "Publish change service triggers to AWS SQS."
  (:require [amazonica.aws.sqs :as sqs]
            [cheshire.core :as json]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.storage.config :as config]))

;; ----- Data schema -----

(def ChangeTrigger
  "
  A trigger for one of the various types of changes that are published:

  add - the specified content-id is newly created, this happens when a board or entry is added
  update - the content-id should be refreshed, this happens when a board or entry is updated
  delete - the specified content-id is deleted, this happens when a board or entry is removed
  "
  {:change-type (schema/enum :add :update :delete)
   :resource-type (schema/enum :board :entry)
   :container-id lib-schema/UniqueID
   :content-id lib-schema/UniqueID
   :change-at lib-schema/ISO8601})

;; ----- Notification triggering -----

(defn- resource-type [content]
  (cond
    (:secure-uuid content) :entry
    :else :board))

(defn ->trigger [change-type content]
  {:change-type change-type
   :resource-type (resource-type content)
   :container-id (or (:board-uuid content) (:org-uuid content))
   :content-id (:uuid content)
   :change-at (or (:published-at content) (:created-at content))})

(defn send-trigger! [trigger]
  (timbre/info "Change notification request of" (:type trigger)
               "for" (:content-id trigger) "to queue" config/aws-sqs-change-queue)
  (timbre/trace "Change request:" trigger)
  (schema/validate ChangeTrigger trigger)
  (timbre/info "Sending request to queue:" config/aws-sqs-change-queue)
  (sqs/send-message
    {:access-key config/aws-access-key-id
     :secret-key config/aws-secret-access-key}
    config/aws-sqs-change-queue
    (json/generate-string trigger {:pretty true}))
  (timbre/info "Request sent to:" config/aws-sqs-change-queue))