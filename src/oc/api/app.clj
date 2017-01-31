(ns oc.api.app
  "Namespace for the web application which serves the REST API."
  (:gen-class)
  (:require
    [raven-clj.core :as sentry]
    [raven-clj.interfaces :as sentry-interfaces]
    [raven-clj.ring :as sentry-mw]
    [taoensso.timbre :as timbre]
    [liberator.dev :refer (wrap-trace)]
    [ring.middleware.params :refer (wrap-params)]
    [ring.middleware.reload :refer (wrap-reload)]
    [ring.middleware.cors :refer (wrap-cors)]
    [compojure.core :as compojure :refer (GET)]
    [com.stuartsierra.component :as component]
    [oc.lib.sentry-appender :as sa]
    [oc.lib.api.common :as api-common]
    [oc.api.components :as components]
    [oc.api.config :as c]))
    ; [open-company.api.entry :as entry-api]
    ; [open-company.api.companies :as comp-api]
    ; [open-company.api.sections :as sect-api]
    ; [open-company.api.stakeholder-updates :as su-api]))

;; ----- Unhandled Exceptions -----

;; Send unhandled exceptions to log and Sentry
;; See https://stuartsierra.com/2015/05/27/clojure-uncaught-exceptions
(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread ex]
     (timbre/error ex "Uncaught exception on" (.getName thread) (.getMessage ex))
     (when c/dsn
       (sentry/capture c/dsn (-> {:message (.getMessage ex)}
                                 (assoc-in [:extra :exception-data] (ex-data ex))
                                 (sentry-interfaces/stacktrace ex)))))))

;; ----- Request Routing -----

(defn routes [sys]
  (compojure/routes
    (GET "/ping" [] (api-common/text-response  "OpenCompany API server: OK" 200)) ; Up-time monitor
    (GET "/---error-test---" req (/ 1 0))
    (GET "/---500-test---" req {:status 500 :body "Testing bad things."})
    ; (entry-api/entry-routes sys)
    ; (comp-api/company-routes sys)
    ; (sect-api/section-routes sys)
    ; (su-api/stakeholder-update-routes sys)
    ))

;; ----- System Startup -----

;; Ring app definition
(defn app [sys]
  (cond-> (routes sys)
   true              wrap-params
   c/liberator-trace (wrap-trace :header :ui)
   true              (wrap-cors #".*")
   c/hot-reload      wrap-reload
   c/dsn             (sentry-mw/wrap-sentry c/dsn)))

(defn start
  "Start a development server"
  [port]

  ;; Stuff logged at error level goes to Sentry
  (if c/dsn
    (timbre/merge-config!
      {:level (keyword c/log-level)
       :appenders {:sentry (sa/sentry-appender c/dsn)}})
    (timbre/merge-config! {:level (keyword c/log-level)}))

    ;; Start the system
    (-> {:handler-fn app :port port}
      components/oc-system
      component/start)

  ;; Echo config information
  (println (str "\n" (slurp (clojure.java.io/resource "ascii_art.txt")) "\n"
    "OpenCompany API Server\n\n"
    "Running on port: " port "\n"
    "Database: " c/db-name "\n"
    "Database pool: " c/db-pool-size "\n"
    "AWS SQS bot queue: " c/aws-sqs-bot-queue "\n"
    "AWS SQS email queue: " c/aws-sqs-email-queue "\n"
    "Hot-reload: " c/hot-reload "\n"
    "Trace: " c/liberator-trace "\n"
    "Sentry: " c/dsn "\n\n"
    "Ready to serve...\n")))

(defn -main []
  (start c/api-server-port))