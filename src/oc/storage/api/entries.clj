(ns oc.storage.api.entries
  "Liberator API for entry resources."
  (:require [if-let.core :refer (if-let*)]
            [compojure.core :as compojure :refer (defroutes OPTIONS GET POST PUT PATCH DELETE)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.storage.config :as config]
            [oc.storage.api.common :as storage-common]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.entry :as entry-rep]
            [oc.storage.resources.common :as common-res]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.entry :as entry-res]))

;; ----- Validations -----

(defn- valid-new-entry? [conn org-slug board-slug topic-slug ctx]
  (if-let [board (board-res/get-board conn (org-res/uuid-for conn org-slug) board-slug)]
    (try
      ;; Create the new entry from the URL and data provided
      (let [entry-map (:data ctx)
            author (:user ctx)
            new-entry (entry-res/->entry conn (:uuid board) topic-slug entry-map author)]
        {:new-entry new-entry :existing-board board})

      (catch clojure.lang.ExceptionInfo e
        [false, (.getMessage e)])) ; Not a valid new entry
    [false, "Invalid board."])) ; couldn't find the specified board

(defn- valid-entry-update? [conn org-slug board-slug topic-slug as-of ctx]
  (if-let [existing-entry (entry-res/get-entry conn
                            (board-res/uuid-for conn org-slug board-slug) topic-slug as-of)]
    ;; Merge the existing entry with the new updates
    (let [entry-map (:data ctx)
          updated-entry (merge existing-entry (entry-res/clean entry-map))]
      (if (lib-schema/valid? common-res/Entry updated-entry)
        {:existing-entry existing-entry :updated-entry updated-entry}
        [false, {:updated-entry updated-entry}])) ; invalid updates
    
    true)) ; no existing entry, will fail existence check later

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

; A resource for operations on a particular entry
(defresource entry [conn org-slug board-slug topic-slug as-of]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get :patch :delete]

  ;; Media type client accepts
  :available-media-types [mt/entry-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/entry-media-type)
  
  ;; Media type client sends
  :known-content-type? (by-method {
                          :options true
                          :get true
                          :patch (fn [ctx] (api-common/known-content-type? ctx mt/entry-media-type))
                          :delete true})

  ;; Authorization
  :allowed? (by-method {
    :options (fn [ctx] (storage-common/access-level-for conn org-slug (:user ctx)))
    :get (fn [ctx] (storage-common/access-level-for conn org-slug (:user ctx)))
    :patch (fn [ctx] (storage-common/allow-authors conn org-slug board-slug (:user ctx)))
    :delete (fn [ctx] (storage-common/allow-authors conn org-slug board-slug (:user ctx)))})

  ;; Validations
  :processable? (by-method {
    :options true
    :get true
    :patch (fn [ctx] (valid-entry-update? conn org-slug board-slug topic-slug as-of ctx))
    :delete true})

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_valid-slug (nil? (schema/check common-res/TopicSlug topic-slug))
                               board (or (:existing-board ctx)
                                         (board-res/get-board conn (org-res/uuid-for conn org-slug) board-slug))
                               entry (or (:existing-entry ctx)
                                         (entry-res/get-entry conn (:uuid board) topic-slug as-of))]
                        {:existing-board board :existing-entry entry}
                        false))

  ;; Actions
  :patch! (fn [ctx] (if-let* [updated-entry (:updated-entry ctx)
                              result (entry-res/update-entry! conn (:id updated-entry) updated-entry (:user ctx))]
                      {:updated-entry result}
                      false))
  :delete! (fn [ctx] (let [board (:existing-board ctx)]
                        (entry-res/delete-entry! conn (:uuid board) topic-slug as-of)))

  ;; Responses
  :handle-ok (by-method {
    :get (fn [ctx] (entry-rep/render-entry org-slug board-slug (:existing-entry ctx) (:access-level ctx)))
    :patch (fn [ctx] (entry-rep/render-entry org-slug board-slug (:updated-entry ctx) (:access-level ctx)))})
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (schema/check common-res/Entry (:updated-entry ctx)))))

; A resource for operations on all entries of a particular topic
(defresource entry-list [conn org-slug board-slug topic-slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get :post]

  ;; Media type client accepts
  :available-media-types (by-method {
                            :get [mt/entry-collection-media-type]
                            :post [mt/entry-media-type]})
  :handle-not-acceptable (by-method {
                            :get (api-common/only-accept 406 mt/entry-collection-media-type)
                            :post (api-common/only-accept 406 mt/entry-media-type)})

  ;; Media type client sends
  :known-content-type? (by-method {
                          :options true
                          :get true
                          :post (fn [ctx] (api-common/known-content-type? ctx mt/entry-media-type))
                          :delete true})  
  
  ;; Authorization
  :allowed? (by-method {
    :options (fn [ctx] (storage-common/access-level-for conn org-slug (:user ctx)))
    :get (fn [ctx] (storage-common/access-level-for conn org-slug (:user ctx)))
    :post (fn [ctx] (storage-common/allow-authors conn org-slug board-slug (:user ctx)))})

  ;; Validations
  :processable? (by-method {
    :options true
    :get true
    :post (fn [ctx] (valid-new-entry? conn org-slug board-slug topic-slug ctx))})

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_valid-slug (nil? (schema/check common-res/TopicSlug topic-slug))
                               entries (entry-res/get-entries-by-topic conn (board-res/uuid-for conn org-slug board-slug) topic-slug)]
                        {:existing-entries entries}
                        false))

  ;; Actions
  :post! (fn [ctx] (if-let* [new-entry (:new-entry ctx)
                            entry-result (entry-res/create-entry! conn new-entry)] ; Add the entry
                      {:new-entry entry-result}
                      false))

  ;; Responses
  :handle-ok (fn [ctx] (entry-rep/render-entry-list org-slug board-slug topic-slug
                          (:existing-entries ctx) (:access-level ctx)))
  :handle-created (fn [ctx] (let [new-entry (:new-entry ctx)]
                              (api-common/location-response
                                (entry-rep/url org-slug board-slug topic-slug (:created-at new-entry))
                                (entry-rep/render-entry org-slug board-slug new-entry (:access-level ctx))
                                mt/entry-media-type))))

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