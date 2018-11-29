(ns oc.storage.async.storage-notification
  "
  Consume storage notifications about new post requests.

  This SQS queue is feed by the Bot service.
  "
  (:require
   [clojure.core.async :as async :refer (<!! >!!)]
   [defun.core :refer (defun-)]
   [cheshire.core :as json]
   [oc.lib.sqs :as sqs]
   [oc.lib.db.pool :as pool]
   [taoensso.timbre :as timbre]))

;; ----- core.async -----

(defonce storage-notification-chan (async/chan 10000)) ; buffered channel

(defonce storage-notification-go (atom nil))

;; ----- SQS handling -----

(defun- slack-action-event
  ([db-pool body :guard #(= (:callback_id %) "add_post")]
  (timbre/info "Got message from Slack:" body))

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