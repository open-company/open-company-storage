(ns open-company.lib.db
  (:require [open-company.db.pool :as pool]))

(defn test-startup
  "Start a minimal DB pool to support running sequential tests."
  []
  (reset! pool/rethinkdb-pool nil)
  (pool/start 1 1))