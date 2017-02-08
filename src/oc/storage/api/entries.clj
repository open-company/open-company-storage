(ns oc.storage.api.entries
  "Liberator API for entry resources."
  (:require [if-let.core :refer (if-let*)]
            [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (defroutes OPTIONS GET POST PUT PATCH DELETE)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.entry :as entry-rep]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.entry :as entry-res]))

;; ----- Utility Functions -----

;; ----- Actions -----

;; ----- Validations -----

(defn allow-team-members
  "Return true if the JWToken user is a member of the org's team."
  [conn {teams :teams} org-slug]
  (if-let [org (org-res/get-org conn org-slug)]
    ((set teams) (:team-id org))
    false))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

; A resource for operations on a particular entry
(defresource entry [conn org-slug board-slug topic-slug as-of]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get]

  ;; Media type client accepts
  :available-media-types [mt/entry-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/entry-media-type)
  
  ;; Authorization
  :allowed? (fn [ctx] (allow-team-members conn (:user ctx) org-slug)) ; TODO filter out private boards

  :exists? (fn [ctx] (if-let [entry (entry-res/get-entry conn (board-res/uuid-for conn org-slug board-slug) topic-slug as-of)]
                        {:existing-entry entry}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (entry-rep/render-entry org-slug board-slug (:existing-entry ctx))))

; A resource for operations on a particular entry
(defresource entry-list [conn org-slug board-slug topic-slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get]

  ;; Media type client accepts
  :available-media-types [mt/entry-collection-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/entry-collection-media-type)
  
  ;; Authorization
  :allowed? (fn [ctx] (allow-team-members conn (:user ctx) org-slug)) ; TODO filter out private boards

  :exists? (fn [ctx] (if-let [entries (entry-res/get-entries-by-topic conn (board-res/uuid-for conn org-slug board-slug) topic-slug)]
                        {:existing-entries entries}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (entry-rep/render-entry-list org-slug board-slug topic-slug (:existing-entries ctx))))

;; ----- Routes -----

(defn- dispatch [db-pool org-slug board-slug topic-slug as-of]
  (pool/with-pool [conn db-pool] 
    (if as-of
      (entry conn org-slug board-slug topic-slug as-of)
      (entry-list conn org-slug board-slug topic-slug))))

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Board operations
      (OPTIONS "/orgs/:org-slug/boards/:board-slug/topics/:topic-slug" [org-slug board-slug topic-slug as-of]
        (dispatch db-pool org-slug board-slug topic-slug as-of))
      (GET "/orgs/:org-slug/boards/:board-slug/topics/:topic-slug" [org-slug board-slug topic-slug as-of]
        (dispatch db-pool org-slug board-slug topic-slug as-of)))))