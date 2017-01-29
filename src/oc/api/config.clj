(ns oc.api.config
  "Namespace for the configuration parameters."
  (:require [clojure.walk :refer (keywordize-keys)]
            [environ.core :refer (env)]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]))

(defn- bool
  "Handle the fact that we may have true/false strings, when we want booleans."
  [val]
  (boolean (Boolean/valueOf val)))

;; ----- System -----

(defonce processors (.availableProcessors (Runtime/getRuntime)))
(defonce core-async-limit (+ 42 (* 2 processors)))

(defonce prod? (= "production" (env :env)))

;; ----- Sentry -----

(defonce dsn (or (env :open-company-sentry-api) false))

;; ----- Logging (see https://github.com/ptaoussanis/timbre) -----

(defonce log-level (or (env :log-level) :info))

;; ----- RethinkDB -----

(defonce migrations-dir "./src/oc/api/db/migrations")
(defonce migration-template "./src/oc/api/assets/migration.template.edn")

(defonce db-host (or (env :db-host) "localhost"))
(defonce db-port (or (env :db-port) 28015))
(defonce db-name (or (env :db-name) "open_company"))
(defonce db-pool-size (or (env :db-pool-size) (- core-async-limit 21))) ; conservative with the core.async limit

(defonce db-map {:host db-host :port db-port :db db-name})
(defonce db-options (flatten (vec db-map))) ; k/v sequence as clj-rethinkdb wants it

;; ----- HTTP server -----

(defonce hot-reload (bool (or (env :hot-reload) false)))
(defonce api-server-port (Integer/parseInt (or (env :port) "3000")))

;; ----- Liberator -----

;; see header response, or http://localhost:3000/x-liberator/requests/ for trace results
(defonce liberator-trace (bool (or (env :liberator-trace) false)))

;; ----- AWS SQS -----

(defonce aws-access-key-id (env :aws-access-key-id))
(defonce aws-secret-access-key (env :aws-secret-access-key))

(defonce aws-sqs-bot-queue (env :aws-sqs-bot-queue))
(defonce aws-sqs-email-queue (env :aws-sqs-email-queue))

;; ----- JWT -----

(defonce passphrase (env :open-company-auth-passphrase))

;; ----- OpenCompany -----

(defonce topics (-> "oc/api/assets/topics.edn"
                    clojure.java.io/resource
                    slurp
                    read-string
                    keywordize-keys))