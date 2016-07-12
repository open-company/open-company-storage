;; Very loosely patterned after https://github.com/yogthos/migratus
(ns open-company.db.migrations
  "
  Migrate RethinkDB data.

  Usage:

  lein create-migration <name>

  lein migrate-db
  "
  (:require [clojure.string :as s]
            [clj-time.core :as t]
            [clj-time.coerce :as coerce]
            [rethinkdb.query :as r]
            [open-company.config :as c]
            [open-company.lib.slugify :as slug])
  (:gen-class))

(defonce migrations-dir "./src/open_company/db/migrations")
(defonce migration-template "./src/open_company/assets/migration.clj.template")

(defn- migration-file-name [migration-name]
  (str (s/join java.io.File/separator [migrations-dir migration-name]) ".clj"))

(defn- store-migration [conn migration-name]
  "Keep the migration name in the migration table so we don't run it again."
  (assert (= 1 (:inserted (-> (r/table "migrations")
                              (r/insert {:name migration-name})
                              (r/run conn))))))

(defn- run-migration [conn migration-name]
  "Run the migration specified by the migration name."
  (println "\nRunning migration: " migration-name)
  (let [file-name (migration-file-name migration-name)
        bare-name (s/join "-" (rest (s/split migration-name #"_"))) ; strip the timestamp
        function-name (str "open-company.db.migrations." bare-name "/up")] ; function name
    (println "Loading name: " file-name)
    (load-file file-name)
    (println "Running function: " function-name)
    ((ns-resolve *ns* (symbol function-name)) conn))) ; run the migration

(defn- run-migrations
  "Given a list of migrations that haven't been run, run them. Abort if any doesn't succeed."
  [conn migration-names]
  (doseq [migration-name migration-names]
    (assert (true? (run-migration conn migration-name)))
    (store-migration conn migration-name))
  :ok)

(defn- report-migrations
  "Report on how many migrations need to be run."
  [migrations]
  (let [m-count (count migrations)]
    (cond 
      (= 0 m-count) (println "No new migrations to run.")
      (= 1 m-count) (println "1 new migration to run.")
      :else (println (str m-count " new migrations to run."))))
  migrations)

(defn- new-migrations
  "Given a list of migrations that exist, return just the ones that haven't been run on this DB."
  [conn migrations]
  (let [migration-slugs (set (map #(second (re-matches #".*\/(.*).clj$" %)) (map str migrations))) ; from the filesystem
        existing-slugs (set (map :name (-> (r/table "migrations") (r/run conn))))] ; from the DB
    (sort (clojure.set/difference migration-slugs existing-slugs))))

(defn migrate 
  "Run any migrations that haven't already been run on this DB."
  ([] (with-open [conn (apply r/connect c/db-options)]
        (migrate conn)))
  ([conn]
  (->> (rest (file-seq (clojure.java.io/file migrations-dir)))
       (new-migrations conn)
       report-migrations
       (run-migrations conn))))

(defn create
  "Create a new migration with the current date and the name."
  [provided-name]
  (let [timestamp (str (coerce/to-long (t/now)))
        migration-name (slug/slugify provided-name 256)
        full-name (str timestamp "-" migration-name)
        file-name (migration-file-name (s/replace full-name #"-" "_"))
        template (slurp migration-template)
        contents (s/replace template #"MIGRATION-NAME" migration-name)]
    (spit file-name contents)))

(defn -main
  "Run create or migrate from lein."
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