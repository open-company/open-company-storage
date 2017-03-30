(ns oc.storage.api.entries
  "Liberator API for entry resources."
  (:require [clojure.string :as s]
            [if-let.core :refer (if-let*)]
            [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (ANY)]
            [liberator.core :refer (defresource by-method)]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.lib.slugify :as slugify]
            [oc.storage.api.topics :as topics-api]
            [oc.storage.config :as config]
            [oc.storage.api.access :as access]            
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
        [false, {:reason (.getMessage e)}])) ; Not a valid new entry
    [false, {:reason "Invalid board."}])) ; couldn't find the specified board

(defn- valid-entry-update? [conn org-slug board-slug topic-slug as-of entry-props]
  (if-let [existing-entry (entry-res/get-entry conn :board
                            (board-res/uuid-for conn org-slug board-slug) topic-slug as-of)]
    ;; Merge the existing entry with the new updates
    (let [merged-entry (merge existing-entry (entry-res/clean entry-props))
          updated-entry (update merged-entry :attachments #(entry-res/timestamp-attachments %))]
      (if (lib-schema/valid? common-res/Entry updated-entry)
        {:existing-entry existing-entry :updated-entry updated-entry}
        [false, {:updated-entry updated-entry}])) ; invalid update
    
    true)) ; no existing entry, so this will fail existence check later

;; ----- Actions -----

(defn- create-entry [conn ctx entry-for]
  (timbre/info "Creating entry:" entry-for)
  (if-let* [new-entry (:new-entry ctx)
            entry-result (entry-res/create-entry! conn new-entry)] ; Add the entry
    
    (do
      (timbre/info "Created entry:" entry-for)
      {:created-entry entry-result})

    (do (timbre/error "Failed creating entry:" entry-for) false)))

(defn- update-entry [conn ctx entry-for]
  (timbre/info "Updating entry:" entry-for)
  (if-let* [updated-entry (:updated-entry ctx)
            updated-result (entry-res/update-entry! conn (:id updated-entry) updated-entry (:user ctx))]
    (do 
      (timbre/info "Updated entry:" entry-for)
      {:updated-entry updated-result})

    (do (timbre/error "Failed updating entry:" entry-for) false)))

(defn- delete-entry [conn ctx entry-for]
  (timbre/info "Deleting entry:" entry-for)
  (if-let* [board (:existing-board ctx)
            entry (:existing-entry ctx)
            _delete-result (entry-res/delete-entry! conn (:uuid board) (:topic-slug entry) (:created-at entry))]
    (do (timbre/info "Deleted entry:" entry-for) true)
    (do (timbre/info "Failed deleting entry:" entry-for) false)))

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
    :options true
    :get (fn [ctx] (access/access-level-for conn org-slug (:user ctx)))
    :patch (fn [ctx] (access/allow-authors conn org-slug board-slug (:user ctx)))
    :delete (fn [ctx] (access/allow-authors conn org-slug board-slug (:user ctx)))})

  ;; Validations
  :processable? (by-method {
    :options true
    :get true
    :patch (fn [ctx] (and (slugify/valid-slug? org-slug)
                          (slugify/valid-slug? board-slug)
                          (valid-entry-update? conn org-slug board-slug topic-slug as-of (:data ctx))))
    :delete true})

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slugs? (and (slugify/valid-slug? org-slug) (slugify/valid-slug? board-slug))
                               _valid-slug (nil? (schema/check common-res/TopicSlug topic-slug))
                               board (or (:existing-board ctx)
                                         (board-res/get-board conn (org-res/uuid-for conn org-slug) board-slug))
                               entry (or (:existing-entry ctx)
                                         (entry-res/get-entry conn :board (:uuid board) topic-slug as-of))]
                        {:existing-board board :existing-entry entry}
                        false))

  ;; Actions
  :patch! (fn [ctx] (update-entry conn ctx (s/join " " [org-slug board-slug topic-slug as-of])))
  :delete! (fn [ctx] (delete-entry conn ctx (s/join " " [org-slug board-slug topic-slug as-of])))

  ;; Responses
  :handle-ok (by-method {
    :get (fn [ctx] (entry-rep/render-entry org-slug board-slug (:existing-entry ctx) (:access-level ctx)))
    :patch (fn [ctx] (entry-rep/render-entry org-slug board-slug (:updated-entry ctx) (:access-level ctx)))})
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (schema/check common-res/Entry (:updated-entry ctx)))))

; A resource for operations on all entries of a particular topic
(defresource entry-list [conn org-slug board-slug topic-slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get :post :delete] ; :delete is handled by the topic resource

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
    :options true
    :get (fn [ctx] (access/access-level-for conn org-slug (:user ctx)))
    :post (fn [ctx] (access/allow-authors conn org-slug board-slug (:user ctx)))})

  ;; Validations
  :processable? (by-method {
    :options true
    :get true
    :post (fn [ctx] (and (slugify/valid-slug? org-slug)
                         (slugify/valid-slug? board-slug)
                         (valid-new-entry? conn org-slug board-slug topic-slug ctx)))})

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slugs? (and (slugify/valid-slug? org-slug) (slugify/valid-slug? board-slug))
                               _valid-slug (nil? (schema/check common-res/TopicSlug topic-slug))
                               board-uuid (board-res/uuid-for conn org-slug board-slug)
                               entries (entry-res/get-entries-by-topic conn board-uuid topic-slug)]
                        {:existing-entries entries}
                        false))

  ;; Actions
  :post! (fn [ctx] (create-entry conn ctx (s/join " " [org-slug board-slug topic-slug])))

  ;; Responses
  :handle-ok (fn [ctx] (entry-rep/render-entry-list org-slug board-slug topic-slug
                          (:existing-entries ctx) (:access-level ctx)))
  :handle-created (fn [ctx] (let [new-entry (:created-entry ctx)]
                              (api-common/location-response
                                (entry-rep/url org-slug board-slug topic-slug (:created-at new-entry))
                                (entry-rep/render-entry org-slug board-slug new-entry :author)
                                mt/entry-media-type)))
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (:reason ctx))))

;; ----- Routes -----

(defn- dispatch [db-pool org-slug board-slug topic-slug as-of rm]
  (pool/with-pool [conn db-pool] 
    (if as-of
      (entry conn org-slug board-slug topic-slug as-of)
      (if (= rm :delete)
        (topics-api/topic conn org-slug board-slug topic-slug)
        (entry-list conn org-slug board-slug topic-slug)))))

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Entry list, Entry and Topic operations (dispatched)
      (ANY "/orgs/:org-slug/boards/:board-slug/topics/:topic-slug" [org-slug board-slug topic-slug as-of :as {rm :request-method}]
        (dispatch db-pool org-slug board-slug topic-slug as-of rm))
      (ANY "/orgs/:org-slug/boards/:board-slug/topics/:topic-slug/" [org-slug board-slug topic-slug as-of :as {rm :request-method}]
        (dispatch db-pool org-slug board-slug topic-slug as-of rm)))))