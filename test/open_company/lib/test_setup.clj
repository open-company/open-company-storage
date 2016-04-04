(ns open-company.lib.test-setup
  (:require [com.stuartsierra.component :as component]
            [open-company.components :as components]
            [open-company.app :as app]))

(def test-system (atom nil))

(defn setup-system! []
  (let [sys (components/oc-system {:handler-fn app/app :port 3000})]
    ;; We don't need the server since we're mocking the requests
    (reset! test-system (component/start (dissoc sys :server)))))

(defn teardown-system! []
  (component/stop @test-system)
  (reset! test-system nil))