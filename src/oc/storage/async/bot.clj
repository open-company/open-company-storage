(ns oc.storage.async.bot
  "Publish Slack bot triggers to AWS SQS."
  (:require [amazonica.aws.sqs :as sqs]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.storage.config :as config]))

(def BotTrigger 
  "All Slack bot triggers have the following properties."
  {
    :type (schema/enum "share-entry")
    :bot {
       :token lib-schema/NonBlankStr
       :id lib-schema/NonBlankStr
    }
    :receiver {
      :type (schema/enum :all-members :user :channel)
      :slack-org-id lib-schema/NonBlankStr
      (schema/optional-key :id) schema/Str
  }})

(def ShareEntryTrigger 
  "A Slack bot trigger to share an Entry."
  (merge BotTrigger {
    :type (schema/enum "share-entry")  
    :note (schema/maybe schema/Str)
    :org-slug lib-schema/NonBlankStr
    :org-name (schema/maybe schema/Str)
    :org-logo-url (schema/maybe schema/Str)
    :board-name (schema/maybe schema/Str)
    :headline (schema/maybe schema/Str)
    :body (schema/maybe schema/Str)
    :comment-count (schema/maybe schema/Str)
    :publisher lib-schema/Author
    :secure-uuid lib-schema/UniqueID
    :published-at lib-schema/ISO8601
    :auto-share schema/Bool
    :sharer lib-schema/Author
    :shared-at lib-schema/ISO8601
    }))

(defn- bot-for
  "Extract the right bot from the JWToken for the specified Slack org ID."
  [slack-org-id user]
  (let [slack-bots (flatten (vals (:slack-bots user)))
        slack-bots-by-slack-org (zipmap (map :slack-org-id slack-bots) slack-bots)
        slack-bot (get slack-bots-by-slack-org slack-org-id)]
    (if slack-bot
      {:token (:token slack-bot) :id (:id slack-bot)}
      false)))

(schema/defn ^:always-validate ->share-entry-trigger :- ShareEntryTrigger
  "Given an entry for an org and a share request, create the share trigger."
  [org board entry share-request user]
  (let [slack-org-id (-> share-request :channel :slack-org-id)
        comments (:existing-comments entry)
        comment-count (str (count comments))]
    {
      :type "share-entry"
      :receiver {
        :type :channel
        :slack-org-id slack-org-id
        :id (-> share-request :channel :channel-id)
      }
      :bot (bot-for slack-org-id user)
      :note (:note share-request)
      :org-slug (:slug org)
      :org-name (:name org)
      :board-name (:name board)
      :org-logo-url (:logo-url org)
      :headline (:headline entry)
      :body (:body entry)
      :comment-count comment-count
      :secure-uuid (:secure-uuid entry)
      :published-at (:published-at entry)
      :publisher (:publisher entry)
      :auto-share (if (:auto-share entry) true false)
      :sharer (lib-schema/author-for-user user)
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

(schema/defn ^:always-validate send-share-entry-trigger! [trigger :- ShareEntryTrigger]
  (send-trigger! trigger))