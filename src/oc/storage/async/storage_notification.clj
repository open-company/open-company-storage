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

  ([db-pool body :guard #(= (:callback_id %) "add_post")]
  ;; Callback from Slack action to create a new post
  ;;
  ;; {:action_ts "1543514783.571999"
  ;;  :callback_id "add_post"
  ;;  :channel {:id "C0AEP1RS9" :name "design"}
  ;;  :type "dialog_submission"
  ;;  :state "Body of the message they want to create a post from in Slack MD format."
  ;;  :token "aLbD1VFXN31DEgpFIvxu32JV"
  ;;  :team {:id "T06SBMH60", :domain "opencompanyhq"}
  ;;  :submission {:status "draft"
  ;;               :section "general"
  ;;               :title "Title of the new post"
  ;;               :note "Body of the note they provided (optional)"}
  ;;  :user {:id "U06SBTXJR", :name "sean"} ; user that added the post (not that created the message originally)
  ;;  :response_url "https://hooks.slack.com/app/T06SBMH60/491682460629/jnKlMtH6AfT2VuprRqbTM5QI"}
  ;;
  ;; Open issues:
  ;; org?
  ;; action author's user-id
  ;; original Slack message author?
  ;; how to format the post?
  ;;   w/ note
  ;;   w/o note
  (timbre/debug "Got message from Slack:" body)
  (pool/with-pool [conn db-pool]
    (if-let* [org (org-res/get-org conn "carrot")
              post (:submission body)
              board (board-res/get-board conn (:uuid org) (:section post))
              content (:state body)
              note (or (:note post) "")
              status (if (= (:status post) "draft") :draft :published)
              author {:avatar-url "https://secure.gravatar.com/avatar/866a6350e399e67e749c6f2aef0b96c0.jpg?s=512&d=https%3A%2F%2Fa.slack-edge.com%2F7fa9%2Fimg%2Favatars%2Fava_0020-512.png", :name "Sean Johnson", :user-id "564e-409b-b95c"}
              final-content (if (clojure.string/blank? note) content (str content "<br><br>" note))
              entry-map {:headline (:title post) :body final-content :status status}
              new-entry (entry-res/->entry conn (:uuid board) entry-map author)
              entry-result (entry-res/create-entry! conn new-entry)]
      (do
        (timbre/info "Posted entry:" entry-result)
        (when (= status :published)
          ; TODO (auto-share-on-publish conn ctx entry-result)
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
            (when (:Message msg) ;; Slack action SNS message
              (let [msg-parsed (json/parse-string (:Message msg) true)]
                (slack-action-event db-pool msg-parsed)))
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