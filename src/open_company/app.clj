(ns open-company.app
  "Namespace for the web application which serves the REST API."
  (:gen-class)
  (:require
    [liberator.dev :refer (wrap-trace)]
    [ring.middleware.reload :as reload]
    [compojure.core :refer (defroutes ANY)]
    [org.httpkit.server :refer (run-server)]
    [open-company.config :as c]
    [open-company.api.companies :refer (company-routes)]
    [open-company.api.reports :refer (report-routes)]
    [compojure.route :as route]))

(defroutes routes
  company-routes
  report-routes)

;; see: header response, or http://localhost:3000/x-liberator/requests/ for trace results
(def trace-app
  (if c/liberator-trace
    (wrap-trace routes :header :ui)
    routes))

(def app
  (if c/hot-reload
    (reload/wrap-reload trace-app)
    trace-app))

(defn start [port]
  (run-server app {:port port :join? false})
  (println (str "\nOpen Company API: "
    "running on port - " port
    ", database - " c/db-name
    ", hot-reload - " c/hot-reload
    ", trace - " c/liberator-trace)))

(defn -main []
  (start c/web-server-port))