(ns open-company.app
  "Namespace for the web application which serves the REST API."
  (:gen-class)
  (:require
    [liberator.dev :refer (wrap-trace)]
    [ring.middleware.reload :as reload]
    [compojure.core :refer (defroutes ANY)]
    [org.httpkit.server :refer (run-server)]
    [open-company.config :refer (liberator-trace hot-reload web-server-port)]
    [open-company.api.companies :refer (company-routes)]
    [compojure.route :as route]))

(defroutes routes
  company-routes)

;; see: header response, or http://localhost:3000/x-liberator/requests/ for trace results
(def trace-app
  (if liberator-trace
    (wrap-trace routes :header :ui)
    routes))

(def app
  (if hot-reload
    (reload/wrap-reload trace-app)
    trace-app))

(defn start [port]
  (run-server app {:port port :join? false})
  (println (str "\nOpen Company API: running on port - " port ", hot-reload - " hot-reload ", trace - " liberator-trace)))

(defn -main []
  (start web-server-port))