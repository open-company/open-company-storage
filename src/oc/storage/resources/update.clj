(ns oc.storage.resources.update
  (:require [oc.storage.resources.common :as common]))

;; ----- RethinkDB metadata -----

(def table-name common/update-table-name)
(def primary-key :id)