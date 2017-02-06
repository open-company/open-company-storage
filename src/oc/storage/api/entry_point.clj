(ns oc.storage.api.entry-point
  "Liberator API for HATEOAS entry point to storage service."
  (:require [compojure.core :as compojure :refer (defroutes GET OPTIONS)]
            [liberator.core :refer (defresource)]
            [cheshire.core :as json]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.resources.org :as org-res]))

;; ----- Responses -----

(defn- render-entry-point [conn {:keys [user] :as _ctx}]

  ;; TODO: public/promoted orgs
  (let [orgs (if user
                ;; Auth'd
                (org-res/get-orgs-by-teams conn (:teams user) [:team-id :logo-url :logo-width :logo-height :created-at :updated-at])
                ;; Not auth'd
                [])]
    (org-rep/render-org-list orgs)))
    
;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource entry-point [conn]
  (api-common/anonymous-resource config/passphrase)

  :allowed-methods [:options :get]
  
  ;; Media type client accepts
  :allowed? (fn [ctx] (api-common/allow-anonymous ctx))
  :available-media-types ["application/json" mt/org-collection-media-type]
  :handle-not-acceptable (fn [_] (api-common/only-accept 406 ["application/json" mt/org-collection-media-type]))

  ;; Responses
  :handle-ok (fn [ctx] (api-common/json-response (render-entry-point conn ctx) 200 mt/org-collection-media-type)))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
     (OPTIONS "/" [] (pool/with-pool [conn db-pool] (entry-point conn)))
     (GET "/" [] (pool/with-pool [conn db-pool] (entry-point conn))))))