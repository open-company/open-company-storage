(ns open-company.config
  "Namespace for the configuration parameters."
  (:require [environ.core :refer (env)]))

;; ----- RethinkDB -----

(defonce db-host (or (env :db-host) "localhost"))
(defonce db-port (or (env :db-port) 28015))
(defonce db-name (or (env :db-name) "open_company"))
(defonce db-pool-size (or (env :db-pool-size) 30))

(defonce db-map {:host db-host :port db-port :db db-name})
(defonce db-options (flatten (vec db-map))) ; k/v sequence as clj-rethinkdb wants it

;; ----- HTTP server -----

(defonce hot-reload (or (env :hot-reload) false))
(defonce api-server-port (Integer/parseInt (or (env :port) "3000")))

;; ----- Liberator -----

;; see header response, or http://localhost:3000/x-liberator/requests/ for trace results
(defonce liberator-trace (or (env :liberator-trace) false))

;; ----- Sentry -----

(defonce dsn (or (env :open-company-sentry-api) false))

;; ----- JWT -----

(defonce passphrase (env :open-company-auth-passphrase))