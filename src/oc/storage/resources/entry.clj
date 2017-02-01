(ns oc.storage.resources.entry
  (:require [oc.storage.resources.common :as common]))

;; ----- RethinkDB metadata -----

(def table-name common/entry-table-name)
(def primary-key :id)