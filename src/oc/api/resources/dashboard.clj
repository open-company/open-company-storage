(ns oc.api.resources.dashboard
  (:require [oc.api.resources.common :as common]))

;; ----- RethinkDB metadata -----

(def table-name common/dashboard-table-name)
(def primary-key :uuid)

;r.db('open_company_dev').table('sections').getAll('green-labs', {index: 'company-slug'}).group('section-name').max('created-at')