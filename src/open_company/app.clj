(ns open-company.app
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
    [compojure.core :as compojure]
    [com.stuartsierra.component :as component]
    [open-company.components :as components]
    [open-company.config :as c]
    [open-company.api.entry :as entry-api]
    [open-company.api.companies :as comp-api]
    [open-company.api.sections :as sect-api]))

;; See https://stuartsierra.com/2015/05/27/clojure-uncaught-exceptions
(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread ex]
     (timbre/error ex "Uncaught exception on" (.getName thread))
     (sentry/capture c/dsn (-> {:message (.getMessage ex)}
                               (assoc-in [:extra :exception-data] (ex-data ex))
                               (sentry-interfaces/stacktrace ex))))))

(defn routes [sys]
  (compojure/routes
   (entry-api/entry-routes sys)
   (comp-api/company-routes sys)
   (sect-api/section-routes sys)))

(defn app [sys]
  (cond-> (routes sys)
   true              wrap-params
   c/liberator-trace (wrap-trace :header :ui)
   true              (wrap-cors #".*")
   c/hot-reload      wrap-reload
   c/dsn             (sentry-mw/wrap-sentry c/dsn)))

(declare handler)
(when c/prod?
  (timbre/set-config! c/log-config)
  (timbre/info "Starting production system without HTTP server")
  (def handler
    (-> (components/oc-system {:handler-fn app})
        (dissoc :server)
        component/start
        (get-in [:handler :handler])))
  (timbre/info "Started"))

(defn start [port]
  (timbre/set-config! c/log-config)

  (-> {:handler-fn app :port port}
      components/oc-system
      component/start)

  (println (str "\n" (slurp (clojure.java.io/resource "open_company/assets/ascii_art.txt")) "\n"
    "OpenCompany API Server\n"
    "Running on port: " port "\n"
    "Database: " c/db-name "\n"
    "Database pool: " c/db-pool-size "\n"
    "Hot-reload: " c/hot-reload "\n"
    "Trace: " c/liberator-trace "\n"
    "Sentry: " c/dsn "\n\n"
    "Ready to serve...\n")))

(defn -main []
  (start c/api-server-port))