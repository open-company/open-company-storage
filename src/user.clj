(ns user
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :as ctnrepl]
            [open-company.config :as c]
            [open-company.app :as app]
            [open-company.db.pool :as pool]
            [open-company.components :as components]))

(def system nil)
(def conn nil)

(defn init
  ([] (init c/api-server-port))
  ([port]
  (alter-var-root #'system (constantly (components/oc-system {:handler-fn app/app
                                                              :port port})))))

(defn bind-conn! []
  (alter-var-root #'conn (constantly (pool/claim (get-in system [:db-pool :pool])))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (component/stop s)))))

(defn go
  ([] (go c/api-server-port)) 
  ([port]
  (init port)
  (start)
  (bind-conn!)))

(defn reset []
  (stop)
  (ctnrepl/refresh :after 'user/go))

(comment

  (into {} system)

  (go)

  (do
    (clojure.tools.namespace.repl/set-refresh-dirs "src")
    (reset))

  )