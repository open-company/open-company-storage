(ns open-company.app
  "Namespace for the web application which serves the REST API."
  (:gen-class)
  (:require
    [liberator.dev :refer (wrap-trace)]
    [raven-clj.core :as raven]
    [raven-clj.ring :refer (wrap-sentry)]
    [ring.middleware.reload :refer (wrap-reload)]
    [ring.middleware.cors :refer (wrap-cors)]
    [org.httpkit.server :refer (run-server)]
    [compojure.core :refer (defroutes ANY)]
    [open-company.config :as c]
    [open-company.api.companies :refer (company-routes)]
    [open-company.api.reports :refer (report-routes)]))

(defroutes routes
  company-routes
  report-routes)

;; see: header response, or http://localhost:3000/x-liberator/requests/ for trace results
(defonce trace-app
  (if c/liberator-trace
    (wrap-trace routes :header :ui)
    routes))

(defonce cors-routes
  ;; Use CORS middleware to support in-browser JavaScript requests.
  (wrap-cors trace-app #".*"))

(defonce hot-reload-routes
  ;; Reload changed files without server restart
  (if c/hot-reload
    (wrap-reload #'cors-routes)
    cors-routes))

(defonce app
  ;; Temp test sentry config
  (raven/capture c/dsn {
    :message "Test Exception Message"
    :tags {:version "0.0.1"}
    :logger "main-logger"
    :extra {
      :my-key 1
      :some-other-value "foo bar"}})
  ;; Use sentry middleware to report runtime errors if we have a raven DSN.
  (if c/dsn
    (wrap-sentry hot-reload-routes c/dsn)
    hot-reload-routes))

(defn start [port]
  (run-server app {:port port :join? false})
    (println (str "\n" (slurp (clojure.java.io/resource "./open_company/assets/ascii_art.txt")) "\n"
      "Running on port: " port "\n"
      "Database: " c/db-name "\n"
      "Hot-reload: " c/hot-reload "\n"
      "Trace: " c/liberator-trace "\n"
      "Sentry: " c/dsn "\n\n"
      "Ready to serve...\n")))

(defn -main []
  (start c/web-server-port))