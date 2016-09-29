(ns open-company.config
  "Namespace for the configuration parameters."
  (:require [clojure.walk :refer (keywordize-keys)]
            [environ.core :refer (env)]
            [cheshire.core :as json]
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

;; ----- RethinkDB -----

(defonce migrations-dir "./src/open_company/db/migrations")
(defonce migration-template "./src/open_company/assets/migration.template.edn")

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

;; ----- Sentry -----

(defonce dsn (or (env :open-company-sentry-api) false))

;; ----- AWS SQS -----

(defonce aws-access-key-id (env :aws-access-key-id))
(defonce aws-secret-access-key (env :aws-secret-access-key))

(defonce aws-sqs-bot-queue (env :aws-sqs-bot-queue))
(defonce aws-sqs-email-queue (env :aws-sqs-email-queue))

;; ----- JWT -----

(defonce passphrase (env :open-company-auth-passphrase))

;; ----- OpenCompany -----

;; how long to wait before a sequential edit by the same author is considered a new version
(defonce collapse-edit-time (or (env :open-company-collapse-edit-time) (* 24 60))) ; 24 hours in minutes

(defonce sections (-> "open_company/assets/sections.json"
                    clojure.java.io/resource
                    slurp
                    json/decode
                    keywordize-keys))

;; ----- Logging (see https://github.com/ptaoussanis/timbre) -----

(def log-config
  "Example (+default) Timbre v4 config map.

  APPENDERS
    An appender is a map with keys:
      :min-level       ; Level keyword, or nil (=> no minimum level)
      :enabled?        ;
      :async?          ; Dispatch using agent? Useful for slow appenders
      :rate-limit      ; [[ncalls-limit window-ms] <...>], or nil
      :output-fn       ; Optional override for inherited (fn [data]) -> string
      :fn              ; (fn [data]) -> side effects, with keys described below

    An appender's fn takes a single data map with keys:
      :config          ; Entire config map (this map, etc.)
      :appender-id     ; Id of appender currently dispatching
      :appender        ; Entire map of appender currently dispatching

      :instant         ; Platform date (java.util.Date or js/Date)
      :level           ; Keyword
      :error-level?    ; Is level e/o #{:error :fatal}?
      :?ns-str         ; String, or nil
      :?file           ; String, or nil  ; Waiting on CLJ-865
      :?line           ; Integer, or nil ; Waiting on CLJ-865

      :?err_           ; Delay - first-arg platform error, or nil
      :vargs_          ; Delay - raw args vector
      :hostname_       ; Delay - string (clj only)
      :msg_            ; Delay - args string
      :timestamp_      ; Delay - string
      :output-fn       ; (fn [data]) -> formatted output string
                       ; (see `default-output-fn` for details)

      :context         ; *context* value at log time (see `with-context`)
      :profile-stats   ; From `profile` macro

  MIDDLEWARE
    Middleware are simple (fn [data]) -> ?data fns (applied left->right) that
    transform the data map dispatched to appender fns. If any middleware returns
    nil, NO dispatching will occur (i.e. the event will be filtered).

  The `example-config` source code contains further settings and details.
  See also `set-config!`, `merge-config!`, `set-level!`."

  {:level :info  ; e/o #{:trace :debug :info :warn :error :fatal :report}

   ;; Control log filtering by namespaces/patterns. Useful for turning off
   ;; logging in noisy libraries, etc.:
   ;; :ns-whitelist  [] #_["my-app.foo-ns"]
   ;; :ns-blacklist  [] #_["taoensso.*"]

   :middleware [] ; (fns [data]) -> ?data, applied left->right

   ;; Clj only:
   :timestamp-opts timbre/default-timestamp-opts ; {:pattern _ :locale _ :timezone _}

   :output-fn timbre/default-output-fn ; (fn [data]) -> string

   :appenders {:spit (appenders/spit-appender {:fname "/tmp/oc-api.log"})}})