(ns oc.storage.async.storage-notification
  "
  Consume storage notifications about new post requests.

  This SQS queue is feed by the Bot service.
  "
  (:require
   [clojure.core.async :as async :refer (<!! >!!)]
   [defun.core :refer (defun-)]
   [if-let.core :refer (if-let*)]
   [cheshire.core :as json]
   [oc.lib.sqs :as sqs]
   [oc.lib.db.pool :as pool]
   [taoensso.timbre :as timbre]
   [oc.storage.async.notification :as notification]
   [oc.storage.resources.org :as org-res]
   [oc.storage.resources.board :as board-res]
   [oc.storage.resources.entry :as entry-res]))

;; ----- core.async -----

(defonce storage-notification-chan (async/chan 10000)) ; buffered channel

(defonce storage-notification-go (atom nil))

;; ----- SQS handling -----

(defun- slack-action-event

  "
  Callback from Bot (Slack dialog) to create a new post...
  
  {:type 'new-entry'
   :sub-type 'add_post'
   :response {:channel {:id 'C06SBQ3FD' :name 'general'}
              :response_url 'https://hooks.slack.com/app/T06SBMH60/553636756630/NvLBGZUOTcyXUOoQRAzvc9ID'
              :token 'aLbD1VFXN31DEgpFIvxu32JV'}
   :entry-parts {:status 'post'
                 :board-slug 'decisions'
                 :headline 'Foo'
                 :body 'Bar'}
   :team-id '6c05-43a8-949a'
   :author {:name 'Sean Johnson'
            :user-id 'f9aa-4d64-8b66'
            :avatar-url 'https://secure.gravatar.com/avatar/866a6350e399e67e749c6f2aef0b96c0.jpg?s=512&d=https%3A%2F%2Fa.slack-edge.com%2F00b63%2Fimg%2Favatars%2Fava_0020-512.png'}}

  {:type 'new-entry'
   :sub-type 'save_message'
   :response {:channel {:id 'C06SBQ3FD' :name 'general'}
              :response_url 'https://hooks.slack.com/app/T06SBMH60/553636756630/NvLBGZUOTcyXUOoQRAzvc9ID'
              :token 'aLbD1VFXN31DEgpFIvxu32JV'}
   :entry-parts {:status 'post'
                 :board-slug 'decisions'
                 :quote {:body 'Something profound in Slack.'
                         :message_ts '1550069782.471208'}
                 :headline 'Foo'
                 :body 'Bar'}
   :team-id '6c05-43a8-949a'
   :author {:name 'Sean Johnson'
            :user-id 'f9aa-4d64-8b66'
            :avatar-url 'https://secure.gravatar.com/avatar/866a6350e399e67e749c6f2aef0b96c0.jpg?s=512&d=https%3A%2F%2Fa.slack-edge.com%2F00b63%2Fimg%2Favatars%2Fava_0020-512.png'}}
  "
  ;; TODO:
  ;; org for multiple teams
  ;; original Slack message author?
  ;; format the post

  ([db-pool body :guard #(and (= (:type %) "new-entry")
                              (or (= (:sub-type %) "save_message_a") (= (:sub-type %) "save_message_b")))]
  (timbre/debug "Got 'save_message_a' message from Slack:" body)
  (pool/with-pool [conn db-pool]
    (if-let* [org-uuid (:uuid (first (org-res/list-orgs-by-teams conn [(:team-id body)] [:uuid])))
              org (org-res/get-org conn org-uuid)
              author (:author body)
              entry-parts (:entry-parts body)
              board (board-res/get-board conn org-uuid (:board-slug entry-parts))
              status (if (= (:status entry-parts) "draft") :draft :published)
              content (str "<p>Said in Slack: \"" (-> entry-parts :quote :body) "\"</p>"
                           "<p><b>" (:signpost entry-parts) ":</b> " (:body entry-parts) "</p>")
              entry-map {:headline (:headline entry-parts)
                         :body content
                         :status status}
              new-entry (entry-res/->entry conn (:uuid board) entry-map author)
              entry-result (entry-res/create-entry! conn new-entry)]
      (do
        (timbre/info "Posted entry:" entry-result)
        (when (= status :published)
          (notification/send-trigger! (notification/->trigger :add org board {:new entry-result} author nil)))
          ;; send bot a message to respond back to the user ephemerally)
      (timbre/error "Unable to post from Slack action for:" body)))))

  ([db-pool body :guard #(and (= (:type %) "new-entry")
                              (= (:sub-type %) "add_post"))]
  (timbre/debug "Got 'add_post' message from Slack:" body)
  (pool/with-pool [conn db-pool]
    (if-let* [org-uuid (:uuid (first (org-res/list-orgs-by-teams conn [(:team-id body)] [:uuid])))
              org (org-res/get-org conn org-uuid)
              author (:author body)
              entry-parts (:entry-parts body)
              board (board-res/get-board conn org-uuid (:board-slug entry-parts))
              status (if (= (:status entry-parts) "draft") :draft :published)
              entry-map {:headline (:headline entry-parts)
                         :body (:body entry-parts)
                         :status status}
              new-entry (entry-res/->entry conn (:uuid board) entry-map author)
              entry-result (entry-res/create-entry! conn new-entry)]
      (do
        (timbre/info "Posted entry:" entry-result)
        (when (= status :published)
          (notification/send-trigger! (notification/->trigger :add org board {:new entry-result} author nil)))
          ;; send bot a message to respond back to the user ephemerally)
      (timbre/error "Unable to post from Slack action for:" body)))))

  ([_ body] (timbre/debug "Skipped message from Slack:" body)))

(defn- read-message-body
  "Try to parse as json, otherwise use read-string."
  [msg]
  (try
    (json/parse-string msg true)
    (catch Exception e
      (read-string msg))))

(defn sqs-handler
  "Handle an incoming SQS message to the storage service."
  [msg done-channel]
  (let [msg-body (read-message-body (:body msg))
        error (if (:test-error msg-body) (/ 1 0) false)] ; a message testing Sentry error reporting
    (timbre/debugf "Received message from SQS (storage notification): %s\n" msg-body)
    (>!! storage-notification-chan msg-body))
  (sqs/ack done-channel msg))

;; ----- Event loop -----

(defn- storage-notification-loop
  "Start a core.async consumer of the storage notification channel."
  [db-pool]
  (reset! storage-notification-go true)
  (async/go (while @storage-notification-go
      (timbre/info "Waiting for message on storage notification channel...")
      (let [msg (<!! storage-notification-chan)]
        (timbre/trace "Processing message on storage notification channel...")
        (if (:stop msg)
          (do (reset! storage-notification-go false) (timbre/info "Storage notification stopped."))
          (try
            (when (= (:type msg) "new-entry")
              (timbre/trace "Storage notification handling:" msg)
              (slack-action-event db-pool msg))
            (timbre/trace "Processing complete.")
            (catch Exception e
              (timbre/error e))))))))

;; ----- Component start/stop -----

(defn start
 "Stop the core.async storage notification channel consumer."
 [sys]
 (let [db-pool (-> sys :db-pool :pool)]
   (timbre/info "Starting storage notification...")
   (storage-notification-loop db-pool)))

(defn stop
 "Stop the core.async storage notification channel consumer."
  []
  (when @storage-notification-go
    (timbre/info "Stopping storage notification...")
    (>!! storage-notification-chan {:stop true})))