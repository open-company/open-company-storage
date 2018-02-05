(ns oc.lib.test-setup
  (:require [com.stuartsierra.component :as component]
            [oc.storage.components :as components]
            [oc.storage.app :as app]))

(def test-system (atom nil))

(defn setup-system! []
  (let [sys (components/storage-system {:handler-fn app/app :port 3001})]
    ;; We don't need the server since we're mocking the requests
    (reset! test-system (component/start (dissoc sys :server)))))

(defn teardown-system! []
  (component/stop @test-system)
  (reset! test-system nil))