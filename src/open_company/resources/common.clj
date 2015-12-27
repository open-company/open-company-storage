(ns open-company.resources.common
  "Resources are any thing stored in the open company platform: companies, reports"
  (:require [clojure.string :as s]
            [clojure.walk :refer (keywordize-keys)]
            [if-let.core :refer (if-let*)]
            [defun :refer (defun)]
            [clj-time.format :as format]
            [clj-time.core :as time]
            [rethinkdb.query :as r]
            [open-company.config :as config]
            [open-company.db.pool :as pool]))

;; ----- RethinkDB metadata -----

(def company-table-name "companies")
(def section-table-name "sections")

;; ----- Category/Section definitions -----

;; All the categories in default order
(defonce categories (vec (map #(keyword (:name %)) (:categories (keywordize-keys config/sections)))))

(defun sections-for
  "Return all the sections for the provided category name in order."

  ([category-name :guard string?] (sections-for (keyword category-name)))

  ([category-name :guard keyword?]
  (if-let* [categories (:categories (keywordize-keys config/sections))
            category (some #(if (= category-name (keyword (:name %))) %) categories)]
    (vec (map #(keyword (:name %)) (:sections category))))))

;; Categories as keys in a map with the value a vector of sections in each category in order
(def ordered-sections (zipmap categories (map sections-for categories)))

(defun category-for
  "Return the category name for the provided section-name."

  ([section-name :guard string?] (category-for (keyword section-name)))

  ([section-name :guard keyword?]
  (some #(if ((set (ordered-sections %)) (keyword section-name)) % false) (keys ordered-sections))))

;; All possible sections as a set
(defonce sections (set (flatten (vals ordered-sections))))

;; A set of all sections that can contain notes
(def notes-sections #{:growth :finances})

;; ----- Properties common to all resources -----

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{:created-at :updated-at :author :links :revisions})

;; ----- ISO 8601 timestamp -----

(def timestamp-format (format/formatters :date-time))

(defn current-timestamp
  "ISO 8601 string timestamp for the current time."
  []
  (format/unparse timestamp-format (time/now)))

;; ----- Utility functions -----

(defn updated-at-order
  "Return items in a sequence sorted by their :updated-at key."
  [coll]
  (sort #(compare (:updated-at %2) (:updated-at %1)) coll))

(defn clean
  "Remove any reserved properties from the resource."
  [resource]
  (apply dissoc resource reserved-properties))

(defn- name-for
  "Replace :name in the map with :real-name if it's not blank."
  [user]
  (if (s/blank? (:real-name user))
    user
    (assoc user :name (:real-name user))))

(defn author-for-user
  "Extract the :avatar (Slack) / :image (import), :user-id and :name (the author fields) from the JWToken claims."
  [user]
  (-> user
    (name-for)
    (select-keys [:avatar :image :user-id :name])
    (clojure.set/rename-keys {:avatar :image})))

;; ----- Resource CRUD funcitons -----

(defn create-resource
  "Create a resource in the DB, returning the property map for the resource."
  [table-name resource timestamp]
  (let [timed-resource (merge resource {
          :created-at timestamp
          :updated-at timestamp})
        insert (pool/with-connection [conn]
                (-> (r/table table-name)
                (r/insert timed-resource)
                (r/run conn)))]
  (if (= 1 (:inserted insert))
    timed-resource
    (throw (RuntimeException. (str "RethinkDB insert failure: " insert))))))

(defn read-resource
  "
  Given a table name and a primary key value, retrieve the resource from the database,
  or return nil if it doesn't exist.
  "
  [table-name primary-key-value]
  (pool/with-connection [conn]
    (-> (r/table table-name)
      (r/get primary-key-value)
      (r/run conn))))

(defn read-resources
  "
  Given a table name, and an optional index name and value, and an optional set of fields, retrieve
  the resources from the database.
  "
  ([table-name fields]
  (pool/with-connection [conn]
    (-> (r/table table-name)
      (r/with-fields fields)
      (r/run conn))))

  ([table-name index-name index-value]
  (pool/with-connection [conn]
    (-> (r/table table-name)
      (r/get-all [index-value] {:index index-name})
      (r/run conn))))

  ([table-name index-name index-value fields]
  (pool/with-connection [conn]
    (-> (r/table table-name)
      (r/get-all [index-value] {:index index-name})
      (r/pluck fields)
      (r/run conn)))))

(defn update-resource
  "Given a table name, the name of the primary key, and the original and updated resource,
  update a resource in the DB, returning the property map for the resource."
  ([table-name primary-key-name original-resource new-resource]
  (update-resource table-name primary-key-name original-resource new-resource (current-timestamp)))

  ([table-name primary-key-name original-resource new-resource timestamp]
  (let [timed-resource (merge new-resource {
          primary-key-name (original-resource primary-key-name)
          :created-at (:created-at original-resource)
          :updated-at timestamp})
        update (pool/with-connection [conn]
              (-> (r/table table-name)
              (r/get (original-resource primary-key-name))
              (r/replace timed-resource)
              (r/run conn)))]
  (if (or (= 1 (:replaced update)) (= 1 (:unchanged update)))
    timed-resource
    (throw (RuntimeException. (str "RethinkDB update failure: " update)))))))

(defn delete-resource
  "Delete the specified resource and return `true`."
  ([table-name primary-key-value]
  (let [delete (pool/with-connection [conn]
                (-> (r/table table-name)
                (r/get primary-key-value)
                (r/delete)
                (r/run conn)))]
    (if (= 1 (:deleted delete))
      true
      (throw (RuntimeException. (str "RethinkDB delete failure: " delete))))))

  ([table-name key-name key-value]
  (let [delete (pool/with-connection [conn]
                (-> (r/table table-name)
                (r/get-all [key-value] {:index key-name})
                (r/delete)
                (r/run conn)))]
    (if (zero? (:errors delete))
      true
      (throw (RuntimeException. (str "RethinkDB delete failure: " delete)))))))

;; ----- Operations on collections of resources -----

(defn delete-all-resources!
  "Use with caution! Failure can result in partial deletes of just some resources. Returns `true` if successful."
  [table-name]
  (let [delete (pool/with-connection [conn]
                  (-> (r/table table-name)
                    (r/delete)
                    (r/run conn)))]
    (if (pos? (:errors delete))
      (throw (RuntimeException. (str "RethinkDB delete failure: " delete)))
      true)))