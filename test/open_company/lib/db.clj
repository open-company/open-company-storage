(ns open-company.lib.db
  (:require [oc.lib.rethinkdb.pool :as pool]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [open-company.config :as config]
            [open-company.resources.common :as common]
            [open-company.resources.section :as section]))

(defn postdate
  "Push the timestamps of the specified section back to 1 second before the collapse-edit-time time limit."
  [conn company-slug section-name]
  (if-let [section (section/get-section conn company-slug section-name)]
    (let [original-at (f/parse (:updated-at section))
          new-at (t/minus original-at (t/seconds (inc (* 60 config/collapse-edit-time))))]
      (common/update-resource
       conn
        section/table-name
        section/primary-key
        section
        section
        (f/unparse common/timestamp-format new-at)))))