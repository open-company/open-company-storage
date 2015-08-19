(ns open-company.resources.common
  "Resources are any thing stored in the open company platform: companies, reports"
  (:require [clj-time.format :as format]
            [clj-time.core :as time]
            [rethinkdb.query :as r]
            [open-company.config :as c]))

;; ----- Properties common to all resources -----

(def
  reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{:created-at :updated-at :links})

;; ----- ISO 8601 timestamp -----

(def timestamp-format (format/formatters :date-time-no-ms))

(defn- current-timestamp
  "ISO 8601 string timestamp for the current time."
  []
  (format/unparse timestamp-format (time/now)))

;; ----- Resource CRUD funcitons -----

(defn create-resource
  "Create a resource in the DB, returning the property map for the resource."
  [table-name resource]
  (let [timestamp (current-timestamp)
        timed-resource (merge resource {
          :created-at timestamp
          :updated-at timestamp})
        insert (with-open [conn (apply r/connect c/db-options)]
                (-> (r/table table-name)
                (r/insert timed-resource)
                (r/run conn)))]
  (if (= 1 (:inserted insert))
    timed-resource
    (throw (RuntimeException. (str "RethinkDB insert failure: " insert))))))

(defn read-resource
  "Given a table name and a primary key value, delete the resource, retrieve it from the database, or return nil if it doesn't exist."
  [table-name primary-key]
  (with-open [conn (apply r/connect c/db-options)]
    (-> (r/table table-name)
      (r/get primary-key)
      (r/run conn))))

(defn update-resource
  "Given a table name, the name of the primary key, and the original and updated resource,
  update a resource in the DB, returning the property map for the resource."
  [table-name primary-key-name original-resource new-resource]
  (let [timestamp (current-timestamp)
        timed-resource (merge new-resource {
          :created-at (:created-at original-resource)
          :updated-at timestamp})
        update (with-open [conn (apply r/connect c/db-options)]
                (-> (r/table table-name)
                (r/get (original-resource primary-key-name))
                (r/replace timed-resource)
                (r/run conn)))]
    (if (or (= 1 (:replaced update)) (= 1 (:unchanged update)))
      timed-resource
      (throw (RuntimeException. (str "RethinkDB update failure: " update))))))

(defn delete-resource
  "Delete the specified resource and return `true`."
  ([table-name key-name key-value]
    (let [delete (with-open [conn (apply r/connect c/db-options)]
                  (-> (r/table table-name)
                  (r/get-all [key-value] {:index key-name})
                  (r/delete)
                  (r/run conn)))]
      (if (= 0 (:errors delete))
        true
        (throw (RuntimeException. (str "RethinkDB delete failure: " delete))))))
  ([table-name primary-key-value]
    (let [delete (with-open [conn (apply r/connect c/db-options)]
                  (-> (r/table table-name)
                  (r/get primary-key-value)
                  (r/delete)
                  (r/run conn)))]
      (if (= 1 (:deleted delete))
        true
        (throw (RuntimeException. (str "RethinkDB delete failure: " delete)))))))

;; ----- Operations on collections of resources -----

(defn delete-all-resources!
  "Use with caution! Failure can result in partial deletes of just some resources. Returns `true` if successful."
  [table-name]
  (let [delete (with-open [conn (apply r/connect c/db-options)]
                  (-> (r/table table-name)
                    (r/delete)
                    (r/run conn)))]
    (if (> (:errors delete) 0)
      (throw (RuntimeException. (str "RethinkDB delete failure: " delete)))
      true)))