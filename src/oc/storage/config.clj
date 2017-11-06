(ns oc.storage.config
  "Namespace for the configuration parameters."
  (:require [environ.core :refer (env)]))

(defn- bool
  "Handle the fact that we may have true/false strings, when we want booleans."
  [val]
  (boolean (Boolean/valueOf val)))

;; ----- System -----

(defonce processors (.availableProcessors (Runtime/getRuntime)))
(defonce core-async-limit (+ 42 (* 2 processors)))

(defonce prod? (= "production" (env :env)))
(defonce intro? (not prod?))

;; ----- Sentry -----

(defonce dsn (or (env :open-company-sentry-storage) false))

;; ----- Logging (see https://github.com/ptaoussanis/timbre) -----

(defonce log-level (or (env :log-level) :info))

;; ----- RethinkDB -----

(defonce migrations-dir "./src/oc/storage/db/migrations")
(defonce migration-template "./src/oc/storage/assets/migration.template.edn")

(defonce db-host (or (env :db-host) "localhost"))
(defonce db-port (or (env :db-port) 28015))
(defonce db-name (or (env :db-name) "open_company_storage"))
(defonce db-pool-size (or (env :db-pool-size) (- core-async-limit 21))) ; conservative with the core.async limit

(defonce db-map {:host db-host :port db-port :db db-name})
(defonce db-options (flatten (vec db-map))) ; k/v sequence as clj-rethinkdb wants it

;; ----- HTTP server -----

(defonce hot-reload (bool (or (env :hot-reload) false)))
(defonce storage-server-port (Integer/parseInt (or (env :port) "3001")))

;; ----- URLs -----

(defonce auth-server-url (or (env :auth-server-url) "http://localhost:3003"))
(defonce interaction-server-url (or (env :interaction-server-url) "http://localhost:3002"))
(defonce interaction-server-ws-url (or (env :interaction-server-ws-url) "ws://localhost:3002"))
(defonce change-server-ws-url (or (env :change-server-ws-url) "ws://localhost:3006"))

;; ----- Liberator -----

;; see header response, or http://localhost:3001/x-liberator/requests/ for trace results
(defonce liberator-trace (bool (or (env :liberator-trace) false)))
(defonce pretty? (not prod?)) ; JSON response as pretty?

;; ----- AWS SNS / SQS -----

(defonce aws-access-key-id (env :aws-access-key-id))
(defonce aws-secret-access-key (env :aws-secret-access-key))

(defonce aws-sqs-bot-queue (env :aws-sqs-bot-queue))
(defonce aws-sqs-email-queue (env :aws-sqs-email-queue))
(defonce aws-sqs-change-queue (env :aws-sqs-change-queue))

(defonce aws-sns-storage-topic-arn (env :aws-sns-storage-topic-arn))

;; ----- JWT -----

(defonce passphrase (env :open-company-auth-passphrase))

;; ----- OpenCompany -----

(defonce default-new-org (read-string (slurp (clojure.java.io/resource "default-new-org.edn"))))

(defonce topics #{
  "CEO Update"
  "Competition"
  "Customers"
  "Finances"
  "Key Challenges"
  "Key Metrics"
  "Lessons Learned"
  "Team and Hiring"
  "Press"
  "Sales"})

(defonce default-reactions ["👌" "👀" "💥"])

(defonce default-activity-limit 20)

(defonce whats-new-board (env :whats-new-board))