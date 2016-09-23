(ns open-company.db.migrations
  "Lein main to migrate RethinkDB data."
  (:require [open-company.config :as c]
            [oc.lib.rethinkdb.migrations :as m])
  (:gen-class))

(defn -main
  "
  Run create or migrate from lein:
  
  lein create-migration <name>

  lein migrate-db
  "
  [which & args]
  (cond 
    (= which "migrate") (m/migrate c/db-options c/migrations-dir)
    (= which "create") (apply m/create c/migrations-dir c/migration-template args)
    :else (println "Unknown action: " which)))