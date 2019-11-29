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
  Callback from Interaction to follow/unfollow posts for mentioned or commenting users...
  
  {:type 'inbox-action'
   :sub-type 'follow'
   :item-id '4321-4321-4321'
   :user-ids ['1234-1234-1234']}

  {:type 'inbox-action'
   :sub-type 'unfollow'
   :item-id '4321-4321-4321'
   :user-ids ['1234-1234-1234']}

  {:type 'inbox-action'
   :sub-type 'dismiss'
   :item-id '4321-4321-4321'
   :user-ids ['1234-1234-1234']
   :dismiss-at '2019-11-29T14:26:12Z'}
  "

  ([db-pool body :guard #(and (= (:type %) "inbox-action")
                              (or (= (:sub-type %) "follow")
                                  (= (:sub-type %) "unfollow")))]
  (timbre/debug "Got inbox-action message of type 'follow' from Interaction:" body)
  (pool/with-pool [conn db-pool]
    (if-let* [entry-uuid (:item-id body)
              user-ids (:user-ids body)
              dismiss-at (:dismiss-at body)
              entry-data (entry-res/get-entry conn entry-uuid)
              user-visibility (get entry-data :user-visibility {})
              new-entry-data (reduce (fn [uv user-id]
                                      (update-in uv [:user-visibility (keyword user-id)]
                                       #(merge % {:dismiss-at dismiss-at
                                                  :user-id user-id})))
                                     entry-data user-ids)
              entry-result (entry-res/update-entry! conn entry-uuid new-entry-data)]
      (do
        (timbre/info "Handled inbox-action dismiss for entry:" entry-result)
        (when (= (:status entry-data) :published)
          (notification/send-trigger! (notification/->trigger :add org board {:new entry-result} author nil))))
      (timbre/error "Failed handling" (:sub-type body) "message for item:" entry-uuid "and users" user-ids))))

  ([db-pool body :guard #(and (= (:type %) "inbox-action")
                              (= (:sub-type %) "dismiss"))]
  (timbre/debug "Got inbox-action message of type 'follow' from Interaction:" body)
  (pool/with-pool [conn db-pool]
    (if-let* [entry-uuid (:item-id body)
              user-ids (:user-ids body)
              entry-data (entry-res/get-entry conn entry-uuid)
              user-visibility (get entry-data :user-visibility {})
              new-entry-data (reduce (fn [uv user-id]
                                      (update-in uv [:user-visibility (keyword user-id)]
                                       #(merge % {:follow (= (:sub-type body) "follow")
                                                  :user-id user-id})))
                                     entry-data user-ids)
              entry-result (entry-res/update-entry! conn entry-uuid new-entry-data)]
      (do
        (timbre/info "Handled inbox-action" (:sub-type body) "for entry:" entry-result)
        (when (= (:status entry-data) :published)
          (notification/send-trigger! (notification/->trigger :add org board {:new entry-result} author nil))))
      (timbre/error "Failed handling follow message for item:" entry-uuid "and users" user-ids))))

  ([_ body] (timbre/debug "Skipped message:" body)))

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