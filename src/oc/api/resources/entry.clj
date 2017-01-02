(ns oc.api.resources.entry
  (:require [oc.api.resources.common :as common]))

;; ----- RethinkDB metadata -----

(def table-name common/entry-table-name)
(def primary-key :id)