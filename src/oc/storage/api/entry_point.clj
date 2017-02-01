(ns oc.storage.api.entry-point
  "Liberator API for HATEOAS entry point to storage service."
  (:require [compojure.core :as compojure :refer (defroutes GET OPTIONS)]
            [liberator.core :refer (defresource)]
            [cheshire.core :as json]
            [oc.lib.rethinkdb.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]))

;; ----- Representations -----

(defn- render-entry-point [conn {:keys [user] :as _ctx}]

  (if user
    
    ;; auth'd settings
    (json/generate-string {"auth" true}
      {:pretty true})
    
    ;; not auth'd
    (json/generate-string {"auth" false} {:pretty true})))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource entry-point [conn]
  (api-common/anonymous-resource config/passphrase)

  :allowed-methods [:options :get]
  :allowed? (fn [ctx] (api-common/allow-anonymous ctx))
  :available-media-types ["application/json"]

  :handle-not-acceptable (fn [_] (api-common/only-accept 406 "application/json"))
  :handle-unsupported-media-type (fn [_] (api-common/only-accept 415 "application/json"))

  :handle-ok (fn [ctx] (render-entry-point conn ctx))

  :handle-options (api-common/options-response [:options :get]))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
     (OPTIONS "/" [] (pool/with-pool [conn db-pool] (entry-point conn)))
     (GET "/" [] (pool/with-pool [conn db-pool] (entry-point conn))))))