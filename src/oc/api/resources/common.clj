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
