(ns oc.api.resources.org
  (:require [oc.api.resources.common :as common]))

;; ----- RethinkDB metadata -----

(def table-name common/org-table-name)
(def primary-key :slug)

