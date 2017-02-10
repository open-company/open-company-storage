(ns oc.storage.api.entries
  "Liberator API for entry resources."
  (:require [compojure.core :as compojure :refer (defroutes OPTIONS GET POST PUT PATCH DELETE)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.api.common :as storage-common]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.entry :as entry-rep]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.entry :as entry-res]))

;; ----- Utility Functions -----

;; ----- Actions -----

;; ----- Validations -----

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

; A resource for operations on a particular entry
(defresource entry [conn org-slug board-slug topic-slug as-of]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get :patch :post :delete]

  ;; Media type client accepts
  :available-media-types [mt/entry-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/entry-media-type)
  
  ;; Media type client sends
  :known-content-type? (by-method {
                          :options true
                          :get true
                          :post (fn [ctx] (api-common/known-content-type? ctx mt/entry-media-type))
                          :patch (fn [ctx] (api-common/known-content-type? ctx mt/entry-media-type))
                          :delete true})

  ;; Authorization
  :allowed? (by-method {
    :options (fn [ctx] (storage-common/access-level-for conn org-slug (:user ctx)))
    :get (fn [ctx] (storage-common/access-level-for conn org-slug (:user ctx)))
    :post (fn [ctx] (storage-common/allow-authors conn org-slug board-slug (:user ctx)))
    :patch (fn [ctx] (storage-common/allow-authors conn org-slug board-slug (:user ctx)))
    :delete (fn [ctx] (storage-common/allow-authors conn org-slug board-slug (:user ctx)))})

  ;; Existentialism
  :exists? (fn [ctx] (if-let [entry (entry-res/get-entry conn (board-res/uuid-for conn org-slug board-slug) topic-slug as-of)]
                        {:existing-entry entry}
                        false))

  ;; Actions
  :delete! (println "POST!")
  :patch! (println "PATCH!")
  :delete! (println "DELETE!")

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
  :allowed? (fn [ctx] (storage-common/access-level-for conn org-slug board-slug (:user ctx)))

  ;; Existentialism
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
        (dispatch db-pool org-slug board-slug topic-slug as-of))
      (PATCH "/orgs/:org-slug/boards/:board-slug/topics/:topic-slug" [org-slug board-slug topic-slug as-of]
        (dispatch db-pool org-slug board-slug topic-slug as-of))
      (POST "/orgs/:org-slug/boards/:board-slug/topics/:topic-slug" [org-slug board-slug topic-slug as-of]
        (dispatch db-pool org-slug board-slug topic-slug as-of))
      (POST "/orgs/:org-slug/boards/:board-slug/topics/:topic-slug/" [org-slug board-slug topic-slug as-of]
        (dispatch db-pool org-slug board-slug topic-slug as-of))
      (DELETE "/orgs/:org-slug/boards/:board-slug/topics/:topic-slug" [org-slug board-slug topic-slug as-of]
        (dispatch db-pool org-slug board-slug topic-slug as-of)))))