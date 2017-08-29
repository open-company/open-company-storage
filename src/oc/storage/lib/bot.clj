(ns oc.storage.lib.bot
  (:require [clojure.string :as s]
            [amazonica.aws.sqs :as sqs]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.storage.config :as config]))

(def BotTrigger 
  "All Slack bot triggers have the following properties."
  {
    :type (schema/enum "share-snapshot")
    :bot {
       :token lib-schema/NonBlankStr
       :id lib-schema/NonBlankStr
    }
    :receiver {
      :type (schema/enum :all-members :user :channel)
      :slack-org-id lib-schema/NonBlankStr
      (schema/optional-key :id) schema/Str
  }})

(def ShareSnapshotTrigger 
  "A Slack bot trigger to share a snapshot."
  (merge BotTrigger {
    :type (schema/enum "share-snapshot")  
    :note (schema/maybe schema/Str)
    :org-slug lib-schema/NonBlankStr
    :org-name (schema/maybe schema/Str)
    :org-logo-url (schema/maybe schema/Str)
    :title (schema/maybe schema/Str)
    :secure-uuid lib-schema/UniqueID
    :published-at lib-schema/ISO8601
    :shared-at lib-schema/ISO8601}))

(defn- bot-for
  "Extract the right bot from the JWToken for the specified Slack org ID."
  [slack-org-id user]
  (let [slack-bots (flatten (vals (:slack-bots user)))
        slack-bots-by-slack-org (zipmap (map :slack-org-id slack-bots) slack-bots)
        slack-bot (get slack-bots-by-slack-org slack-org-id)]
    (if slack-bot
      {:token (:token slack-bot) :id (:id slack-bot)}
      false)))

(schema/defn ^:always-validate ->share-snapshot-trigger :- ShareSnapshotTrigger
  "Given a story for an org and a share request, create the share trigger."
  [org story share-request user]
  (let [slack-org-id (-> share-request :channel :slack-org-id)]
    {
      :type "share-snapshot"
      :receiver {
        :type :channel
        :slack-org-id slack-org-id
        :id (-> share-request :channel :channel-id)
      }
      :bot (bot-for slack-org-id user)
      :note (:note share-request)
      :org-slug (:slug org)
      :org-name (:name org)
      :org-logo-url (:logo-url org)
      :title (:title story)
      :secure-uuid (:secure-uuid story)
      :published-at (:published-at story)
      :shared-at (:shared-at share-request)
    }))

(defn- send-trigger! [trigger]
  (timbre/info "Bot request to queue:" config/aws-sqs-bot-queue)
  (timbre/trace "Bot request:" (dissoc trigger :entries))
  (timbre/info "Sending request to:" config/aws-sqs-bot-queue)
  (sqs/send-message
   {:access-key config/aws-access-key-id
    :secret-key config/aws-secret-access-key}
   config/aws-sqs-bot-queue
   trigger)
  (timbre/info "Request sent to:" config/aws-sqs-bot-queue))

(schema/defn ^:always-validate send-share-snapshot-trigger! [trigger :- ShareSnapshotTrigger]
  (send-trigger! trigger))