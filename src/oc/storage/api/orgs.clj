(ns oc.storage.api.orgs
  "Liberator API for team resources."
  (:require [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (defroutes OPTIONS GET POST PUT PATCH DELETE)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.rethinkdb.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.resources.org :as org-res]
            [oc.storage.representations.board :as board-rep]
            [oc.storage.resources.board :as board-res]))

;; ----- Utility Functions -----

;; ----- Actions -----

;; ----- Validations -----

(defn allow-team-members
  "Return true if the JWToken user is a member of the org's team."
  [conn {teams :teams} slug]
  (if-let [org (org-res/get-org conn slug)]
    ((set teams) (:team-id org))
    false))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for operations on a particular org
(defresource org [conn slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get]

  ;; Media type client accepts
  :available-media-types [mt/org-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/org-media-type)
  
  ;; Authorization
  :allowed? (fn [ctx] (allow-team-members conn (:user ctx) slug))

  :exists? (fn [ctx] (if-let [org (org-res/get-org conn slug)]
                        {:existing-org org}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (org-rep/render-org (:existing-org ctx))))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Team operations
      (OPTIONS "/orgs/:slug" [slug] (pool/with-pool [conn db-pool] (org conn slug)))
      (GET "/orgs/:slug" [slug] (pool/with-pool [conn db-pool] (org conn slug))))))