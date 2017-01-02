(ns oc.api.resources.topic
  (:require [oc.api.resources.common :as common]))

;; ----- RethinkDB metadata -----

(def table-name common/topic-table-name)
(def primary-key :id)