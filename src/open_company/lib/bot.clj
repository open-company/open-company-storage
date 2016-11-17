(ns open-company.lib.bot
  (:require [amazonica.aws.sqs :as sqs]
            [taoensso.timbre :as timbre]
            [medley.core :as med]
            [clojure.string :as string]
            [schema.core :as s]
            [open-company.config :as c]))

(def KnownScripts
  (s/enum :onboard :onboard-user :onboard-user-authenticated :stakeholder-update))

(def BotTrigger
  {:api-token s/Str
   :script   {:id KnownScripts :params {s/Keyword s/Str}}
   :receiver {:type (s/enum :all-members :user :channel) (s/optional-key :id) s/Str}
   :bot      {:token s/Str :id s/Str}})

(defmulti adapt (fn [type _] type))

(defmethod adapt :company [_ m]
  (select-keys m [:name :logo :slug :description :currency]))

(defmethod adapt :user [_ m]
  {:name (first (string/split (:real-name m) #"\ "))})

(defmethod adapt :stakeholder-update [_ m]
  (select-keys m [:slug :note :created-at :title]))

(defn- script-params
  "Turn `ctx` into params map for bot scripts. May contain superflous fields."
  [ctx]
  (->> [:company :user :stakeholder-update]
       (map (fn [k] (med/map-keys #(keyword (name k) (name %)) (adapt k (get ctx k)))))
       (apply merge)))

(defn- add-su-note [ctx]
  (let [note (-> ctx :data :note)]
    (if-not (string/blank? note)
      (assoc-in ctx [:stakeholder-update :note] note)
      ctx)))

(defn- add-origin [params ctx]
  (if-let [origin (get-in ctx [:request :headers "origin"])]
    (assoc params :env/origin origin)
    params))

(defn ctx->trigger [script-id ctx]
  {:pre [(map? (:company ctx)) (map? (:user ctx))]}
  (let [rid (-> ctx :user :user-id)
        bot (-> ctx :user :bot)
        channel (-> ctx :data :channel)
        everyone? (or (= channel "__everyone__") (nil? channel))]
    {:api-token   (:jwtoken ctx)
     :bot         bot
     :script      {:id script-id, :params (-> ctx add-su-note script-params (add-origin ctx))}
     :receiver    (cond 
                    (= script-id :onboard) {:type :user :id rid}
                    (and (= script-id :stakeholder-update) (not everyone?)) {:type :channel :id channel}
                    (= script-id :stakeholder-update) {:type :all-members})}))

(defn send-trigger! [trigger]
  (timbre/info "Request to send msg to " c/aws-sqs-bot-queue "\n" trigger)
  (s/validate BotTrigger trigger)
  (timbre/info "Sending")
  (sqs/send-message
   {:access-key c/aws-access-key-id
    :secret-key c/aws-secret-access-key}
   c/aws-sqs-bot-queue
   trigger))

(comment
  (get-im-channel tkn "U0JSATHT3")

  (send-trigger! {:abc :d})

  (:body @(slack :users.list {:token tkn}))

  )