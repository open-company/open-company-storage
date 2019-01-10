(ns oc.storage.async.notification
  "
  Async publish of notification events to AWS SNS.
  "
  (:require [clojure.core.async :as async :refer (<! >!!)]
            [defun.core :refer (defun)]
            [taoensso.timbre :as timbre]
            [cheshire.core :as json]
            [amazonica.aws.sns :as sns]
            [amazonica.aws.kinesisfirehose :as fh]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.time :as oc-time]
            [oc.lib.text :as str]
            [oc.storage.config :as config]
            [oc.storage.resources.common :as common-res]))

;; ----- core.async -----

(defonce notification-chan (async/chan 10000)) ; buffered channel

(defonce notification-go (atom true))

;; ----- Utility functions -----

(defn- resource-type [content]
  (cond
    (:secure-uuid content) :entry
    (:org-uuid content) :board
    :else :org))

;; ----- Data schema -----

(defn- notification-type? [notification-type] (#{:add :update :delete :nux} notification-type))

(defn- resource-type? [resource-type] (#{:org :board :entry} resource-type))

(def NotificationTrigger
  "
  A trigger for one of the various types of notifications that are published:

  add - the content is newly created, this happens when a board or entry is added
  update - the content should be refreshed, this happens when a board or entry is updated
  delete - the specified content-id is deleted, this happens when a board or entry is removed

  The notification trigger contains the type of resource as `resource-type` and the content as `new` and/or
  `old` in a key called `content`.

  The user whose actions triggered the notification is included as `user`.

  A timestamp for when the notice was created is included as `notification-at`.
  "
  {:notification-type (schema/pred notification-type?)
   :resource-type (schema/pred resource-type?)
   (schema/optional-key :org) common-res/Org
   (schema/optional-key :board) common-res/Board
   :content {
    (schema/optional-key :new) (schema/conditional #(= (resource-type %) :entry) common-res/Entry
                                                   #(= (resource-type %) :board) common-res/Board
                                                   :else common-res/Org)
    (schema/optional-key :notifications) (schema/maybe [common-res/User])
    (schema/optional-key :old) (schema/conditional #(= (resource-type %) :entry) common-res/Entry
                                                   #(= (resource-type %) :board) common-res/Board
                                                   :else common-res/Org)
    (schema/optional-key :nux-boards) [lib-schema/NonBlankStr]}
   :user (schema/maybe lib-schema/User) ; occassionaly we have a non-user updating an entry, such as the Ziggeo callback
   (schema/optional-key :note) (schema/maybe schema/Str)
   :notification-at lib-schema/ISO8601})

;; ----- Event handling -----

(defn- handle-notification-message
  [trigger]
  (timbre/debug "Notification request of:" (:notification-type trigger)
               "for:" (trigger :current :uuid) "to topic:" config/aws-sns-storage-topic-arn)
  (timbre/trace "Notification request:" trigger)
  (schema/validate NotificationTrigger trigger)
  (timbre/info "Sending request to topic:" config/aws-sns-storage-topic-arn)
  (let [subject (str (name (:notification-type trigger))
                     " on " (name (:resource-type trigger))
                     ": " (-> trigger :current :uuid))]
    (try
      (sns/publish
       {:access-key config/aws-access-key-id
        :secret-key config/aws-secret-access-key}
       :topic-arn config/aws-sns-storage-topic-arn
       :subject subject
       :message (json/generate-string trigger {:pretty true}))
      (catch Exception e
        (timbre/info "SNS failed with: " e)
        ;; If an exception occurred write to the kinesis firehouse.
        (fh/put-record
          config/aws-kinesis-stream-name
          {:subject subject
           :Message (json/generate-string trigger {:pretty true})}))))
  (timbre/info "Request sent to topic:" config/aws-sns-storage-topic-arn))

;; ----- Event loop -----

(defn- notification-loop []
  (reset! notification-go true)
  (timbre/info "Starting notification...")
  (async/go (while @notification-go
    (timbre/debug "Notification waiting...")
    (let [message (<! notification-chan)]
      (timbre/debug "Processing message on notification channel...")
      (if (:stop message)
        (do (reset! notification-go false) (timbre/info "Notification stopped."))
        (async/thread
          (try
            (handle-notification-message message)
          (catch Exception e
            (timbre/error e)))))))))

;; ----- Notification triggering -----

(defun ->trigger 
  ([:nux org nux-content user]
    {:notification-type :nux
     :resource-type :org
     :user user
     :content nux-content
     :notification-at (oc-time/current-timestamp)})
  ([notification-type content user] (->trigger notification-type nil nil content user nil))
  ([notification-type org content user] (->trigger notification-type org nil content user nil))
  ([notification-type org content user note] (->trigger notification-type org nil content user note))
  ([notification-type org board content user note]
  (let [notice {:notification-type notification-type
                :resource-type (resource-type (or (:old content) (:new content)))
                :content content
                :user user
                :notification-at (oc-time/current-timestamp)}
        note-notice (if note (assoc notice :note (str/strip-xss-tags note)) notice)
        org-notice (if org (assoc note-notice :org org) note-notice)
        final-notice (if board (assoc org-notice :board board) org-notice)]
      final-notice)))

(schema/defn ^:always-validate send-trigger! [trigger :- NotificationTrigger]
  (if (clojure.string/blank? config/aws-sns-storage-topic-arn)
    (timbre/debug "Skipping a notification for:" (or (-> trigger :content :old :uuid)
                                                     (-> trigger :content :new :uuid)))
    (do
      (timbre/debug "Triggering a notification for:" (or (-> trigger :content :old :uuid)
                                                         (-> trigger :content :new :uuid)))
      (>!! notification-chan trigger))))

;; ----- Component start/stop -----

(defn start
  "Start the core.async event loop."
  []
  (when-not (clojure.string/blank? config/aws-sns-storage-topic-arn) ; do we care about getting SNS notifications?
    (notification-loop)))

(defn stop
  "Stop the the core.async event loop."
  []
  (when @notification-go
    (timbre/info "Stopping notification...")
    (>!! notification-chan {:stop true})))