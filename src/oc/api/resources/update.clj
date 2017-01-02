(ns oc.api.resources.update
  (:require [oc.api.resources.common :as common]))

;; ----- RethinkDB metadata -----

(def table-name common/update-table-name)
(def primary-key :id)