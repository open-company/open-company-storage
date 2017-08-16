(ns oc.storage.api.stories
  "Liberator API for story resources."
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
            [oc.storage.config :as config]
            [oc.storage.api.access :as access]            
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.story :as story-rep]
            [oc.storage.resources.common :as common-res]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.story :as story-res]))

;; ----- Validations -----

(defn- valid-new-story? [conn org-slug board-slug ctx]
  (if-let [board (board-res/get-board conn (org-res/uuid-for conn org-slug) board-slug)]
    (try
      ;; Create the new story from the URL and data provided
      (let [story-map (:data ctx)
            author (:user ctx)
            new-story (story-res/->story conn (:uuid board) story-map author)]
        {:new-story new-story :existing-board board})

      (catch clojure.lang.ExceptionInfo e
        [false, {:reason (.getMessage e)}])) ; Not a valid new story
    [false, {:reason "Invalid board."}])) ; couldn't find the specified board

; (defn- valid-entry-update? [conn entry-uuid entry-props]
;   (if-let [existing-entry (entry-res/get-entry conn entry-uuid)]
;     ;; Merge the existing entry with the new updates
;     (let [merged-entry (merge existing-entry (entry-res/clean entry-props))
;           updated-entry (update merged-entry :attachments #(entry-res/timestamp-attachments %))]
;       (if (lib-schema/valid? common-res/Entry updated-entry)
;         {:existing-entry existing-entry :updated-entry updated-entry}
;         [false, {:updated-entry updated-entry}])) ; invalid update
    
;     true)) ; no existing entry, so this will fail existence check later

;; ----- Actions -----

(defn- create-story [conn ctx story-for]
  (timbre/info "Creating story for:" story-for)
  (if-let* [new-story (:new-story ctx)
            story-result (story-res/create-story! conn new-story)] ; Add the story
    
    (do
      (timbre/info "Created story for:" story-for "as" (:uuid story-result))
      {:created-story story-result})

    (do (timbre/error "Failed creating story:" story-for) false)))

; (defn- update-entry [conn ctx entry-for]
;   (timbre/info "Updating entry:" entry-for)
;   (if-let* [updated-entry (:updated-entry ctx)
;             updated-result (entry-res/update-entry! conn (:uuid updated-entry) updated-entry (:user ctx))]
;     (do 
;       (timbre/info "Updated entry:" entry-for)
;       {:updated-entry updated-result})

;     (do (timbre/error "Failed updating entry:" entry-for) false)))

; (defn- delete-entry [conn ctx entry-for]
;   (timbre/info "Deleting entry:" entry-for)
;   (if-let* [board (:existing-board ctx)
;             entry (:existing-entry ctx)
;             _delete-result (entry-res/delete-entry! conn (:uuid entry))]
;     (do (timbre/info "Deleted entry:" entry-for) true)
;     (do (timbre/info "Failed deleting entry:" entry-for) false)))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

; A resource for operations on a particular entry
; (defresource entry [conn org-slug board-slug entry-uuid]
;   (api-common/open-company-anonymous-resource config/passphrase) ; verify validity of optional JWToken

;   :allowed-methods [:options :get :patch :delete]

;   ;; Media type client accepts
;   :available-media-types [mt/entry-media-type]
;   :handle-not-acceptable (api-common/only-accept 406 mt/entry-media-type)
  
;   ;; Media type client sends
;   :known-content-type? (by-method {
;     :options true
;     :get true
;     :patch (fn [ctx] (api-common/known-content-type? ctx mt/entry-media-type))
;     :delete true})

;   ;; Authorization
;   :allowed? (by-method {
;     :options true
;     :get (fn [ctx] (access/access-level-for conn org-slug board-slug (:user ctx)))
;     :patch (fn [ctx] (access/allow-authors conn org-slug board-slug (:user ctx)))
;     :delete (fn [ctx] (access/allow-authors conn org-slug board-slug (:user ctx)))})

;   ;; Validations
;   :processable? (by-method {
;     :options true
;     :get true
;     :patch (fn [ctx] (and (slugify/valid-slug? org-slug)
;                           (slugify/valid-slug? board-slug)
;                           (valid-entry-update? conn entry-uuid (:data ctx))))
;     :delete true})

;   ;; Existentialism
;   :exists? (fn [ctx] (if-let* [_slugs? (and (slugify/valid-slug? org-slug)
;                                             (slugify/valid-slug? board-slug))
;                                org (or (:existing-org ctx)
;                                        (org-res/get-org conn org-slug))
;                                org-uuid (:uuid org)
;                                board (or (:existing-board ctx)
;                                          (board-res/get-board conn org-uuid board-slug))
;                                entry (or (:existing-entry ctx)
;                                          (entry-res/get-entry conn org-uuid (:uuid board) entry-uuid))
;                                comments (or (:existing-comments ctx)
;                                             (entry-res/list-comments-for-entry conn (:uuid entry)))
;                                reactions (or (:existing-reactions ctx)
;                                             (entry-res/list-reactions-for-entry conn (:uuid entry)))]
;                         {:existing-org org :existing-board board :existing-entry entry
;                          :existing-comments comments :existing-reactions reactions}
;                         false))
  
;   ;; Actions
;   :patch! (fn [ctx] (update-entry conn ctx (s/join " " [org-slug board-slug entry-uuid])))
;   :delete! (fn [ctx] (delete-entry conn ctx (s/join " " [org-slug board-slug entry-uuid])))

;   ;; Responses
;   :handle-ok (by-method {
;     :get (fn [ctx] (entry-rep/render-entry org-slug board-slug
;                       (:existing-entry ctx)
;                       (:existing-comments ctx)
;                       (:existing-reactions ctx)
;                       (:access-level ctx)
;                       (-> ctx :user :user-id)))
;     :patch (fn [ctx] (entry-rep/render-entry org-slug board-slug
;                         (:updated-entry ctx)
;                         (:existing-comments ctx)
;                         (:existing-reactions ctx)
;                         (:access-level ctx)
;                         (-> ctx :user :user-id)))})
;   :handle-unprocessable-entity (fn [ctx]
;     (api-common/unprocessable-entity-response (schema/check common-res/Entry (:updated-entry ctx)))))

; A resource for operations on all entries of a particular board
(defresource story-list [conn org-slug board-slug]
  (api-common/open-company-anonymous-resource config/passphrase) ; verify validity of optional JWToken

  :allowed-methods [:options :get :post]

  ;; Media type client accepts
  :available-media-types (by-method {
                            :get [mt/story-collection-media-type]
                            :post [mt/story-media-type]})
  :handle-not-acceptable (by-method {
                            :get (api-common/only-accept 406 mt/story-collection-media-type)
                            :post (api-common/only-accept 406 mt/story-media-type)})

  ;; Media type client sends
  :known-content-type? (by-method {
                          :options true
                          :get true
                          :post (fn [ctx] (api-common/known-content-type? ctx mt/story-media-type))
                          :delete true})
  
  ;; Authorization
  :allowed? (by-method {
    :options true
    :get (fn [ctx] (access/access-level-for conn org-slug board-slug (:user ctx)))
    :post (fn [ctx] (access/allow-authors conn org-slug board-slug (:user ctx)))})

  ;; Validations
  :processable? (by-method {
    :options true
    :get true
    :post (fn [ctx] (and (slugify/valid-slug? org-slug)
                         (slugify/valid-slug? board-slug)
                         (valid-new-story? conn org-slug board-slug ctx)))})

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slugs? (and (slugify/valid-slug? org-slug)
                                            (slugify/valid-slug? board-slug))
                               org (org-res/get-org conn org-slug)
                               org-uuid (:uuid org)
                               board (board-res/get-board conn org-uuid board-slug)
                               board-uuid (:uuid board)
                               storyboard? (= (keyword (:type board)) :story)
                               stories (story-res/list-stories-by-board conn board-uuid)]
                        {:existing-stories stories :existing-board board :existing-org org}
                        false))

  ;; Actions
  :post! (fn [ctx] (create-story conn ctx (s/join " " [org-slug board-slug])))

  ;; Responses
  :handle-ok (fn [ctx] (story-rep/render-story-list (:existing-org ctx) board-slug
                          (:existing-stories ctx) (:access-level ctx) (-> ctx :user :user-id)))
  :handle-created (fn [ctx] (let [new-story (:created-story ctx)]
                              (api-common/location-response
                                (story-rep/url org-slug board-slug (:uuid new-story))
                                (story-rep/render-story (:existing-org ctx) board-slug new-story [] []
                                  :author (-> ctx :user :user-id))
                                mt/story-media-type)))
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (:reason ctx))))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Story list operations
      (ANY "/orgs/:org-slug/boards/:board-slug/stories"
        [org-slug board-slug]
        (pool/with-pool [conn db-pool] 
          (story-list conn org-slug board-slug)))
      (ANY "/orgs/:org-slug/boards/:board-slug/stories/"
        [org-slug board-slug]
        (pool/with-pool [conn db-pool] 
          (story-list conn org-slug board-slug)))
      ;; Story operations
      ; (ANY "/orgs/:org-slug/boards/:board-slug/stories/:story-uuid"
      ;   [org-slug board-slug story-uuid]
      ;   (pool/with-pool [conn db-pool] 
      ;     (story conn org-slug board-slug story-uuid)))
      ; (ANY "/orgs/:org-slug/boards/:board-slug/stories/:story-uuid/"
      ;   [org-slug board-slug story-uuid]
      ;   (pool/with-pool [conn db-pool]
      ;     (story conn org-slug board-slug story-uuid)))
      )))