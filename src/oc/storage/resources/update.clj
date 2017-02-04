(ns oc.storage.resources.update
  (:require [oc.storage.resources.common :as common]))

;; ----- RethinkDB metadata -----

(def table-name common/update-table-name)
(def primary-key :id)

;; ----- Metadata -----

(def reserved-properties
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{})

;; ----- Utility functions -----

(defn- clean
  "Remove any reserved properties from the org."
  [update]
  (apply dissoc (common/clean update) reserved-properties))