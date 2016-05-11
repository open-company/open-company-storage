(ns open-company.lib.bot
  (:require [amazonica.aws.sqs :as sqs]
            [environ.core :as e]
            [manifold.deferred :as d]
            [aleph.http :as http]
            [medley.core :as med]
            [clojure.string :as string]
            [schema.core :as s]))

(def KnownScripts
  (s/enum :onboard :onboard-user :onboard-user-authenticated :stakeholder-update))

(def BotTrigger
  {:api-token s/Str
   :script   {:id KnownScripts :params {s/Keyword s/Str}}
   :receiver {:type (s/eq :channel) :id s/Str}
   :bot      {:token s/Str :id s/Str}})

(defn slack-api [method params]
  (-> (http/get (str "https://slack.com/api/" (name method))
                {:query-params params :as :json})
      (d/chain #(if (-> % :body :ok)
                  %
                  (throw (ex-info "Error after calling Slack API"
                                  {:method method :params params
                                   :response (select-keys % [:body :status])}))))))

(defn get-im-channel [token user-id]
  (-> @(slack-api :im.open {:token token :user user-id}) :body :channel :id))

(defn strip-prefix
  "Remove potential prefixes from supplied `id`"
  [id]
  (string/replace id #"^slack:" ""))

(defmulti adapt (fn [type _] type))

(defmethod adapt :company [_ m]
  (select-keys m [:name :slug :description :currency]))

(defmethod adapt :user [_ m]
  {:name (first (string/split (:real-name m) #"\ "))})

(defn script-params [{:keys [user company]}]
  (merge (med/map-keys #(keyword "company" (name %)) (adapt :company company))
         (med/map-keys #(keyword "user" (name %)) (adapt :user user))))

(defn ctx->trigger [script-id ctx]
  {:pre [(map? (:company ctx)) (map? (:user ctx))]}
  (let [rid (strip-prefix (-> ctx :user :user-id))
        bot (-> ctx :user :bot)]
    {:api-token   (:jwtoken ctx)
     :bot         bot
     :script      {:id script-id
                   :params (script-params ctx)}
     :receiver    {:type :channel
                   :id   (if (= \U (first rid))
                           (get-im-channel (:token bot) rid)
                           rid)}}))

(defn send-trigger! [trigger]
  (s/validate BotTrigger trigger)
  (prn trigger)
  (sqs/send-message
   {:access-key (e/env :aws-access-key)
    :secret-key (e/env :aws-secret-key)}
   (e/env :aws-sqs-queue)
   trigger))

(comment
  (get-im-channel tkn "U0JSATHT3")

  (send-trigger! {:abc :d})

  (-> @(slack :users.list {:token tkn}) :body)

  )