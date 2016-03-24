(ns user
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :as ctnrepl]
            [taoensso.timbre :as timbre]
            
            [open-company.config :as c]
            [open-company.app :as app]
            [open-company.components :as components]))

(def system nil)

(defn init []
  (alter-var-root #'system (constantly (components/oc-system {:handler-fn app/app
                                                              :port 3000}))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (component/stop s)))))

(defn go []
  (init)
  (start))

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