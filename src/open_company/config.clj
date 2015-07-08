(ns open-company.config
  "Namespace for the configuration parameters."
  (:require [environ.core :refer (env)]))

;; ----- RethinkDB config -----

(defonce db-host (or (env :db-host) "localhost"))
(defonce db-port (or (env :db-host) "28015"))
(defonce db-name (or (env :db-name) "opencompany"))

;; ----- Web server config -----

(defonce hot-reload (or (env :hot-reload) false))
(defonce web-server-port (Integer/parseInt (or (env :port) "3000")))

;; ----- Liberator config -----

(defonce liberator-trace (or (env :liberator-trace) false))