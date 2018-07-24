(ns oc.storage.resources.video
  (:require [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.db.common :as db-common]
            [oc.lib.slugify :as slugify]
            [oc.storage.config :as config]
            [oc.storage.resources.common :as common]))

;; ----- RethinkDB metadata -----
(def table-name common/video-table-name)
(def primary-key :uuid)

