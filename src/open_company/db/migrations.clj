;; Very loosely patterned after https://github.com/yogthos/migratus
(ns open-company.db.migrations
  "Migrate RethinkDB data."
  (:require [clojure.string :as s]
            [clj-time.core :as t]
            [clj-time.coerce :as coerce]
            [rethinkdb.query :as r]
            [open-company.config :as c])
  (:gen-class))

(defonce migrations-dir "./src/open_company/db/migrations")
(defonce migration-template "./src/open_company/assets/migration.template.clj")

(defn- run-migrations
  "Given a list of migrations that haven't been run, run them."
  [conn db-name migrations]
  [])

(defn- report-migrations 
  [migrations]
  (let [m-count (count migrations)]
    (cond 
      (= 0 m-count) (println "No new migrations to run.")
      (= 1 m-count) (println "1 new migration to run.")
      :else (println (str m-count " new migrations to run.")))))

(defn- new-migrations
  "Given a list of migrations that exist, return just the ones that haven't been run on this DB."
  [conn db-name migrations]
  migrations)

(defn- migration-list
  "Return a list of all the migrations that exist."
  []
  [])

(defn migrate 
  "Run any migrations that haven't already been run on this DB."
  ([] (with-open [conn (apply r/connect c/db-options)]
        (migrate conn c/db-name)))
  ([conn db-name]
  (->> (migration-list)
       (new-migrations conn db-name)
       report-migrations
       (run-migrations conn db-name))))

(defn create
  "Create a new migration with the current date and the name."
  [migration-name]
  (let [timestamp (str (coerce/to-long (t/now)))
        full-name (str timestamp "-" migration-name)
        file-name (str (s/join java.io.File/separator [migrations-dir full-name]) ".clj")
        template (slurp migration-template)
        contents (-> template
                    (s/replace #"MIGRATION-NAME" migration-name)
                    (s/replace #"MIGRATION-SLUG" full-name))]
    (spit file-name contents)))

(defn -main
  [which & args]
  (cond 
    (= which "migrate") (migrate)
    (= which "create") (apply create args)
    :else (println "Unknown action: " which)))

(comment
  ;; REPL testing

  (require '[open-company.db.migrations :as m] :reload)

  (m/create "test-it")

  (m/migrate)

  )