(ns open-company.config
  "Namespace for the configuration parameters."
  (:require [environ.core :refer (env)]))

;; ----- RethinkDB config -----

(defonce db-host (or (env :db-host) "localhost"))
(defonce db-port (or (env :db-port) 28015))
(defonce db-name (or (env :db-name) "open_company"))

(defonce db-map {:host db-host :port db-port :db db-name})
(defonce db-options (flatten (into [] db-map))) ; k/v sequence as clj-rethinkdb wants it

;; ----- Web server config -----

(defonce hot-reload (or (env :hot-reload) false))
(defonce web-server-port (Integer/parseInt (or (env :port) "3000")))

;; ----- Liberator config -----

;; see header response, or http://localhost:3000/x-liberator/requests/ for trace results
(defonce liberator-trace (or (env :liberator-trace) false))

;; ----- Sentry config -----

(defonce dsn (or (env :dsn) false))