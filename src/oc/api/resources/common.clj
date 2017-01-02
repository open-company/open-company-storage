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
