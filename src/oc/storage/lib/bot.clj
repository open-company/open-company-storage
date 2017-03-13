(ns oc.storage.lib.bot
  (:require [clojure.string :as s]
            [amazonica.aws.sqs :as sqs]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.storage.config :as config]))

(def KnownScript
  (schema/enum :onboard :onboard-user :onboard-user-authenticated :stakeholder-update))

(def BotTrigger
  {:script   {:id KnownScript :params {schema/Keyword (schema/maybe schema/Str)}}
   :receiver {:type (schema/enum :all-members :user :channel) (schema/optional-key :id) schema/Str}
   :bot      {:token lib-schema/NonBlankStr :id lib-schema/NonBlankStr}})

(defn- add-note [params ctx]
  (let [note (-> ctx :data :note)]
    (if-not (s/blank? note)
      (assoc params :update/note note)
      params)))

(defn ->trigger [org-slug update origin-url ctx]
  (let [slack-org-id (-> ctx :data :slack-org-id)
        channel (-> ctx :data :channel)
        everyone? (or (= channel "__everyone__") (nil? channel))]
    {:bot         (:bot ctx)
     :script      {:id "update", :params (-> (select-keys update [:slug :created-at])
                                            (clojure.set/rename-keys {:slug :update/slug :created-at :update/created-at})
                                            (add-note ctx)
                                            (assoc :org/slug org-slug)
                                            (assoc :env/origin origin-url))}
     :receiver    (if everyone?
                    {:type :all-members}
                    {:type :channel :id channel})}))

(defn send-trigger! [trigger]
  (timbre/info "Bot request to:" config/aws-sqs-bot-queue "\n" (dissoc trigger :entries))
  (schema/validate BotTrigger trigger)
  (timbre/info "Sending request to:" config/aws-sqs-bot-queue)
  (sqs/send-message
   {:access-key config/aws-access-key-id
    :secret-key config/aws-secret-access-key}
   config/aws-sqs-bot-queue
   trigger)
  (timbre/info "Request sent to:" config/aws-sqs-email-queue))