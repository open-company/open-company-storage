(ns open-company.resources.common
  "Resources are any thing stored in the open company platform: companies, reports"
  (:require [clojure.string :as string]
            [clojure.set :as cset]
            [schema.core :as s]
            [clojure.walk :refer (keywordize-keys)]
            [clj-time.format :as format]
            [clj-time.core :as time]
            [rethinkdb.query :as r]
            [open-company.lib.slugify :as slug]
            [open-company.config :as config]
            [open-company.db.pool :as pool]))

;; ----- RethinkDB metadata -----

(def company-table-name "companies")
(def section-table-name "sections")

;; ----- Category/Section definitions -----

;; All the categories in default order
(def categories (:categories (keywordize-keys config/sections)))
(def category-names (set (map (comp keyword :name) (:categories (keywordize-keys config/sections)))))

(def ordered-sections
  (into {} (for [{:keys [sections name]} categories]
             [(keyword name) sections])))

(def category-section-tree
  ^ {:doc "Category->Section lookup structure"}
  (into {} (for [[cat sects] ordered-sections]
             [cat (mapv (comp keyword :section-name) sects)])))


(def section-category-tree
  ^ {:doc "Section->Category lookup structure"}
  (reduce (fn [acc [cat sects]]
            (merge acc (zipmap sects (repeat cat))))
          {}
          category-section-tree))

(defn sections-for
  "Return all the sections for the provided category name in order."
  [cat]
  (get category-section-tree (keyword cat)))

(defn category-for
  "Return the category for the provided section name in order."
  [section]
  (get section-category-tree (keyword section)))

;; All possible sections as a set
(def sections (set (map #(update % :section-name keyword) (flatten (vals ordered-sections)))))
(def section-names (set (flatten (vals category-section-tree))))

;; A set of all sections that can contain notes
(def notes-sections #{:growth :finances})

;; ----- Schemas -----

(def Slug (s/pred slug/valid-slug?))

(def SectionsOrder
  {s/Keyword [s/Keyword]})

(def Section
  {:section-name                 s/Keyword
   :title                        s/Str
   :description                  s/Str
   :company-slug                 s/Str
   (s/optional-key :body)        s/Str
   (s/optional-key :created-at)  s/Str
   (s/optional-key :updated-at)  s/Str
   s/Keyword                     s/Any})

(def InlineSections
  (into {} (for [sn section-names] [(s/optional-key sn) Section])))

;; TODO check for non-blank?
(def Company
  (merge {:name        s/Str
          :description s/Str
          :slug        Slug
          :currency    s/Str
          :org-id      s/Str
          :sections    SectionsOrder
          :categories  (s/pred #(cset/subset? (set %) category-names))
          (s/optional-key :home-page)   s/Str
          (s/optional-key :logo)        s/Str
          (s/optional-key :created-at)  s/Str
          (s/optional-key :updated-at)  s/Str}
         InlineSections))

(def User
  {:name        s/Str
   :org-id      s/Str
   :user-id     s/Str
   :avatar      s/Str
   :image       s/Str
   (s/optional-key :created-at)  s/Str
   (s/optional-key :updated-at)  s/Str
   s/Keyword    s/Any})

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
  (if (string/blank? (:real-name user))
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