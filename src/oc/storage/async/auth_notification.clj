(ns oc.storage.async.auth-notification
  "
  Consume auth notifications about newly created users. This SQS queue
  is subscribed to the auth SNS topic.
  "
  (:require
   [clojure.core.async :as async :refer (<!! >!!)]
   [cheshire.core :as json]
   [oc.lib.sqs :as sqs]
   [oc.storage.config :as config]
   [taoensso.timbre :as timbre]))

;; ----- core.async -----

(defonce auth-notification-chan (async/chan 10000)) ; buffered channel

(defonce auth-notification-go (atom nil))

;; ----- SQS handling -----

(defn new-user-event
  [db-pool body]
  (timbre/debug body))

(defn- read-message-body
  "
  Try to parse as json, otherwise use read-string.
  "
  [msg]
  (try
    (json/parse-string msg true)
    (catch Exception e
      (read-string msg))))

(defn sqs-handler
  "Handle an incoming SQS message from the auth service."
  [msg done-channel]
  (let [msg-body (read-message-body (:body msg))
        error (if (:test-error msg-body) (/ 1 0) false)] ; a message testing Sentry error reporting
    (timbre/infof "Received message from SQS: %s\n" msg-body)
    (>!! auth-notification-chan msg-body))
  (sqs/ack done-channel msg))

;; ----- Event loop -----

(defn- auth-notification-loop
  "Start a core.async consumer of the auth notification channel."
  [db-pool]
  (reset! auth-notification-go true)
  (async/go (while @auth-notification-go
      (timbre/info "Waiting for message on auth notification channel...")
      (let [msg (<!! auth-notification-chan)]
        (timbre/trace "Processing message on auth notification channel...")
        (if (:stop msg)
          (do (reset! auth-notification-go false) (timbre/info "Auth notification stopped."))
          (try
            (when (:Message msg) ;; data change SNS message
              (let [msg-parsed (json/parse-string (:Message msg) true)]
                (new-user-event db-pool msg-parsed)))
            (timbre/trace "Processing complete.")
            (catch Exception e
              (timbre/error e))))))))

;; ----- Component start/stop -----

(defn start
 "Stop the core.async auth notification channel consumer."
 [sys]
 (let [db-pool (-> sys :db-pool :pool)]
   (timbre/info "Starting auth notification...")
   (auth-notification-loop db-pool)))

(defn stop
 "Stop the core.async auth notification channel consumer."
  []
  (when @auth-notification-go
    (timbre/info "Stopping auth notification...")
    (>!! auth-notification-chan {:stop true})))
