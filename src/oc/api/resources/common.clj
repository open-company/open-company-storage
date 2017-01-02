(ns oc.api.resources.common
  "Resources are any thing stored in the open company platform: orgs, dashboards, topics, updates"
  (:require [clojure.string :as s]
            [clojure.core.async :as async]
            [schema.core :as schema]
            [clj-time.format :as format]
            [clj-time.core :as time]
            [rethinkdb.query :as r]
            [oc.lib.slugify :as slug]
            [oc.api.config :as config]))

;; ----- RethinkDB metadata -----

(def org-table-name "orgs")
(def dashboard-table-name "dashboards")
(def topic-table-name "topics")
(def update-table-name "updates")

;; ----- Topic definitions -----

(def topics "All possible topic templates as a set" (set (:templates config/topics)))

(def topic-names "All topic names as a set of keywords" (set (map keyword (:topics config/topics))))

(def topics-by-name "All topic templates as a map from their name"
  (zipmap (map #(keyword (:topic-name %)) (:templates config/topics)) (:templates config/topics)))

(def custom-topic-name "Regex that matches properly named custom topics" #"^custom-.{4}$")

(defn topic-name? [topic-name]
  (or (topic-names (keyword topic-name)) (re-matches custom-topic-name (name topic-name))))

;; ----- Data Schemas -----

(def NonBlankString (schema/pred #(and (string? %) (not (s/blank? %)))))
(def ISO8601 (schema/pred #(re-matches #"^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?(([+-]\d\d:\d\d)|Z)?$/i" %)))

; Allow known section names and custom section names
(def TopicName (schema/pred topic-name?))

(def Slug (schema/pred slug/valid-slug?))

(def TopicOrder
  (schema/pred #(and
    (sequential? %) ; it is sequential
    (every? topic-name? %) ; everything in it is a topic name
    (= (count (set %)) (count %))))) ; there are no duplicates

(def Author {
  :name NonBlankString
  :user-id NonBlankString
  :image-url (schema/maybe schema/Str)
  (schema/optional-key :updated-at) ISO8601})

(def UpdateTopic {
  :slug TopicName
  :title NonBlankString
  :headline schema/Str
  :body schema/Str
  (schema/optional-key :image-url) (schema/maybe schema/Str)
  (schema/optional-key :image-height) schema/Num
  (schema/optional-key :image-width) schema/Num
  (schema/optional-key :data) [{}]
  (schema/optional-key :metrics) [{}]
  :author [Author]
  :created-at ISO8601
  :updated-at ISO8601})

(def Topic
  (merge UpdateTopic {
    :dashboard-slug NonBlankString
    :body-placeholder NonBlankString}))

(def AccessLevel (schema/pred #(#{:private :team :public} (keyword %))))

(def Dashboard {
  :slug Slug
  :name NonBlankString
  :company-slug Slug
  :access AccessLevel
  :promoted schema/Bool
  :authors [NonBlankString]
  :viewers [NonBlankString]
  :topics TopicOrder
  :update-template {:title schema/Str
                    :topics TopicOrder}
  :author Author
  :created-at ISO8601
  :updated-at ISO8601})

(def Organization {
  :slug Slug
  :name NonBlankString
  :org-id NonBlankString
  :currency schema/Str
  (schema/optional-key :logo-url) schema/Str
  (schema/optional-key :logo-width) schema/Int
  (schema/optional-key :logo-height) schema/Int
  :admins [NonBlankString]
  :created-at ISO8601
  :updated-at ISO8601})

(def ShareMedium (schema/pred #(#{:legacy :link :email :slack} (keyword %))))

(def Update {
  :slug Slug ; slug of the update, made from the slugified title and a short UUID
  :dashboard-slug Slug
  :company-slug Slug
  :currency schema/Str
  (schema/optional-key :logo-url) schema/Str
  (schema/optional-key :logo-width) schema/Int
  (schema/optional-key :logo-height) schema/Int          
  :title schema/Str
  :topics [UpdateTopic]
  :author Author ; user that created the update
  :medium ShareMedium
  (schema/optional-key :to) [schema/Str]
  (schema/optional-key :note) schema/Str
  :created-at ISO8601
  :updated-at ISO8601})