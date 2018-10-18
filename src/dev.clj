(ns dev
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :as ctnrepl]
            [oc.lib.db.pool :as pool]
            [oc.storage.config :as c]
            [oc.storage.util.search :as search]
            [oc.storage.app :as app]
            [oc.storage.components :as components]
            [oc.storage.async.auth-notification :as auth-notification]
            [oc.storage.async.storage-notification :as storage-notification]))

(defonce system nil)
(defonce conn nil)

(defn init
  ([] (init c/storage-server-port))
  ([port]
     (alter-var-root #'system (constantly (components/storage-system
                                           {:handler-fn app/app
                                            :port port
                                            :auth-sqs-queue c/aws-sqs-auth-queue
                                            :auth-sqs-msg-handler auth-notification/sqs-handler
                                            :storage-sqs-queue c/aws-sqs-storage-queue
                                            :storage-sqs-msg-handler storage-notification/sqs-handler
                                            :sqs-creds {:access-key c/aws-access-key-id
                                                        :secret-key c/aws-secret-access-key}})))))

(defn init-db []
  (alter-var-root #'system (constantly (components/db-only-auth-system {}))))

(defn bind-conn! []
  (alter-var-root #'conn (constantly (pool/claim (get-in system [:db-pool :pool])))))

(defn- start⬆ []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (component/stop s))))
  (println (str "\nWhen you're ready to start the system again, just type: (go)\n")))

(defn go-db []
  (init-db)
  (start⬆)
  (bind-conn!)
  (println (str "A DB connection is available with: conn\n"
                "When you're ready to stop the system, just type: (stop)\n")))

(defn go
  
  ([] (go c/storage-server-port))
  
  ([port]
  (init port)
  (start⬆)
  (bind-conn!)
  (app/echo-config port)
  (println (str "Now serving storage from the REPL.\n"
                "A DB connection is available with: conn\n"
                "When you're ready to stop the system, just type: (stop)\n"))
  port))

(defn reset []
  (stop)
  (ctnrepl/refresh :after 'user/go))

(defn send-data-to-search-index []
  (search/index-all-entries conn))