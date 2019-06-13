(ns oc.storage.util.timestamp
  "Timestamp utility namespace"
  (:require [clj-time.format :as f]
            [oc.lib.db.common :as db-common]))

(defn valid-timestamp? [ts]
  (try
    (f/parse db-common/timestamp-format ts)
    true
    (catch IllegalArgumentException e
      false)))