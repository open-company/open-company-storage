(ns oc.storage.api.entries
  "Liberator API for entry resources."
  (:require [clojure.string :as s]
            [defun.core :refer (defun-)]
            [if-let.core :refer (if-let*)]
            [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (ANY)]
            [liberator.core :refer (defresource by-method)]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.db.pool :as pool]
            [oc.lib.db.common :as db-common]
            [oc.lib.api.common :as api-common]
            [oc.lib.slugify :as slugify]
            [oc.storage.config :as config]
            [oc.storage.api.access :as access]          
            [oc.storage.async.change :as change]
            [oc.storage.async.email :as email]
            [oc.storage.async.bot :as bot]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.entry :as entry-rep]
            [oc.storage.resources.common :as common-res]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.entry :as entry-res]))

;; ----- Utility functions -----

(defun- trigger-share-requests
  "Parallel recursive function to send share requests to AWS SQS."

  ;; Initial
  ([org entry user share-requests :guard seq?]
  (doall (pmap (partial trigger-share-requests org entry user) share-requests)))

  ;; Email share
  ([org entry user share-request :guard #(= "email" (:medium %)) ]
  (timbre/info "Triggering share: email for" (:uuid entry) "of" (:slug org))
  (email/send-trigger! (email/->trigger org entry share-request user)))

  ;; Slack share
  ([org entry user share-request :guard #(= "slack" (:medium %))]
  (timbre/info "Triggering share: slack for" (:uuid entry) "of" (:slug org))
  (bot/send-share-entry-trigger! (bot/->share-entry-trigger org entry share-request user))))

;; ----- Validations -----

(defn- valid-new-entry? [conn org-slug board-slug ctx]
  (if-let [board (board-res/get-board conn (org-res/uuid-for conn org-slug) board-slug)]
    (try
      ;; Create the new entry from the URL and data provided
      (let [entry-map (:data ctx)
            author (:user ctx)
            new-entry (entry-res/->entry conn (:uuid board) entry-map author)]
        {:new-entry new-entry :existing-board board})

      (catch clojure.lang.ExceptionInfo e
        [false, {:reason (.getMessage e)}])) ; Not a valid new entry
    [false, {:reason "Invalid board."}])) ; couldn't find the specified board

(defn- valid-entry-update? [conn entry-uuid entry-props]
  (if-let [existing-entry (entry-res/get-entry conn entry-uuid)]
    ;; Merge the existing entry with the new updates
    (let [new-board-slug (:board-slug entry-props) ; check if they are moving the entry
          new-board (when new-board-slug ; look up the board it's being moved to
                          (board-res/get-board conn (:org-uuid existing-entry) new-board-slug))
          new-board-uuid (:uuid new-board)
          props (if new-board-uuid
                  (assoc entry-props :board-uuid new-board-uuid)
                  (dissoc entry-props :board-uuid))
          updated-entry (merge existing-entry (entry-res/ignore-props props))]
      (if (lib-schema/valid? common-res/Entry updated-entry)
        {:existing-entry existing-entry :updated-entry updated-entry}
        [false, {:updated-entry updated-entry}])) ; invalid update
    
    true)) ; no existing entry, so this will fail existence check later

(defn- valid-share-requests? [conn entry-uuid share-props]
  (if-let* [existing-entry (entry-res/get-entry conn entry-uuid)
            ts (db-common/current-timestamp)
            _seq? (seq? share-props)
            share-requests (map #(assoc % :shared-at ts) share-props)]
    (if (every? #(lib-schema/valid? common-res/ShareRequest %) share-requests)
        {:existing-entry existing-entry :share-requests share-requests}
        [false, {:share-requests share-requests}]) ; invalid share request
    
    true)) ; no existing entry, so this will fail existence check later

;; ----- Actions -----

(defn- create-entry [conn ctx entry-for]
  (timbre/info "Creating entry for:" entry-for)
  (if-let* [new-entry (:new-entry ctx)
            entry-result (entry-res/create-entry! conn new-entry)] ; Add the entry
    
    (do
      (timbre/info "Created entry for:" entry-for "as" (:uuid entry-result))
      (change/send-trigger! (change/->trigger :add :entry entry-result))
      {:created-entry entry-result})

    (do (timbre/error "Failed creating entry:" entry-for) false)))

(defn- update-entry [conn ctx entry-for]
  (timbre/info "Updating entry for:" entry-for)
  (if-let* [updated-entry (:updated-entry ctx)
            updated-result (entry-res/update-entry! conn (:uuid updated-entry) updated-entry (:user ctx))]
    (do 
      (timbre/info "Updated entry for:" entry-for)
      (change/send-trigger! (change/->trigger :refresh :entry updated-result))
      {:updated-entry updated-result})

    (do (timbre/error "Failed updating entry:" entry-for) false)))

(defn- delete-entry [conn ctx entry-for]
  (timbre/info "Deleting entry for:" entry-for)
  (if-let* [board (:existing-board ctx)
            entry (:existing-entry ctx)
            _delete-result (entry-res/delete-entry! conn (:uuid entry))]
    (do
      (timbre/info "Deleted entry for:" entry-for)
      (change/send-trigger! (change/->trigger :delete :entry entry))
      true)
    (do (timbre/error "Failed deleting entry for:" entry-for) false)))

(defn- share-entry [conn ctx entry-for]
  (timbre/info "Sharing entry:" entry-for)
  (if-let* [org (:existing-org ctx)
            entry (:existing-entry ctx)
            user (:user ctx)
            share-requests (:share-requests ctx)
            shared {:shared (concat (or (:shared entry) []) share-requests)}
            update-result (entry-res/update-entry! conn (:uuid entry) shared user)]
    (do
      (when (and (seq? share-requests)(any? share-requests)) (trigger-share-requests org entry user share-requests))
      (timbre/info "Shared entry:" entry-for)
      {:updated-entry update-result})
    (do (timbre/error "Failed sharing entry:" entry-for) false)))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for operations on a particular entry
(defresource entry [conn org-slug board-slug entry-uuid]
  (api-common/open-company-anonymous-resource config/passphrase) ; verify validity of optional JWToken

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
    :get (fn [ctx] (access/access-level-for conn org-slug board-slug (:user ctx)))
    :patch (fn [ctx] (access/allow-authors conn org-slug board-slug (:user ctx)))
    :delete (fn [ctx] (access/allow-authors conn org-slug board-slug (:user ctx)))})

  ;; Validations
  :processable? (by-method {
    :options true
    :get true
    :patch (fn [ctx] (and (slugify/valid-slug? org-slug)
                          (slugify/valid-slug? board-slug)
                          (valid-entry-update? conn entry-uuid (:data ctx))))
    :delete true})

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slugs? (and (slugify/valid-slug? org-slug)
                                            (slugify/valid-slug? board-slug))
                               org (or (:existing-org ctx)
                                       (org-res/get-org conn org-slug))
                               org-uuid (:uuid org)
                               board (or (:existing-board ctx)
                                         (board-res/get-board conn org-uuid board-slug))
                               entry (or (:existing-entry ctx)
                                         (entry-res/get-entry conn org-uuid (:uuid board) entry-uuid))
                               comments (or (:existing-comments ctx)
                                            (entry-res/list-comments-for-entry conn (:uuid entry)))
                               reactions (or (:existing-reactions ctx)
                                            (entry-res/list-reactions-for-entry conn (:uuid entry)))]
                        {:existing-org org :existing-board board :existing-entry entry
                         :existing-comments comments :existing-reactions reactions}
                        false))
  
  ;; Actions
  :patch! (fn [ctx] (update-entry conn ctx (s/join " " [org-slug board-slug entry-uuid])))
  :delete! (fn [ctx] (delete-entry conn ctx (s/join " " [org-slug board-slug entry-uuid])))

  ;; Responses
  :handle-ok (by-method {
    :get (fn [ctx] (entry-rep/render-entry org-slug board-slug
                      (:existing-entry ctx)
                      (:existing-comments ctx)
                      (:existing-reactions ctx)
                      (:access-level ctx)
                      (-> ctx :user :user-id)))
    :patch (fn [ctx] (entry-rep/render-entry org-slug board-slug
                        (:updated-entry ctx)
                        (:existing-comments ctx)
                        (:existing-reactions ctx)
                        (:access-level ctx)
                        (-> ctx :user :user-id)))})
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (schema/check common-res/Entry (:updated-entry ctx)))))

;; A resource for operations on all entries of a particular board
(defresource entry-list [conn org-slug board-slug]
  (api-common/open-company-anonymous-resource config/passphrase) ; verify validity of optional JWToken

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
    :options true
    :get (fn [ctx] (access/access-level-for conn org-slug board-slug (:user ctx)))
    :post (fn [ctx] (access/allow-authors conn org-slug board-slug (:user ctx)))})

  ;; Validations
  :processable? (by-method {
    :options true
    :get true
    :post (fn [ctx] (and (slugify/valid-slug? org-slug)
                         (slugify/valid-slug? board-slug)
                         (valid-new-entry? conn org-slug board-slug ctx)))})

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slugs? (and (slugify/valid-slug? org-slug)
                                            (slugify/valid-slug? board-slug))
                               org-uuid (org-res/uuid-for conn org-slug)
                               board (board-res/get-board conn org-uuid board-slug)
                               board-uuid (:uuid board)
                               entries (entry-res/list-entries-by-board conn board-uuid)]
                        {:existing-entries entries :existing-board board}
                        false))

  ;; Actions
  :post! (fn [ctx] (create-entry conn ctx (s/join " " [org-slug board-slug])))

  ;; Responses
  :handle-ok (fn [ctx] (entry-rep/render-entry-list org-slug board-slug
                          (:existing-entries ctx) (:access-level ctx) (-> ctx :user :user-id)))
  :handle-created (fn [ctx] (let [new-entry (:created-entry ctx)]
                              (api-common/location-response
                                (entry-rep/url org-slug board-slug (:uuid new-entry))
                                (entry-rep/render-entry org-slug board-slug new-entry [] []
                                  :author (-> ctx :user :user-id))
                                mt/entry-media-type)))
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (:reason ctx))))

;; A resource for operations to share a particular entry
(defresource share [conn org-slug board-slug entry-uuid]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :post]

  ;; Authorization
  :allowed? (by-method {
    :options true
    :post (fn [ctx] (access/allow-authors conn org-slug (:user ctx)))})
  
  ;; Media type client accepts
  :available-media-types (by-method {
                            :post [mt/entry-media-type]})
  :handle-not-acceptable (by-method {
                            :post (api-common/only-accept 406 mt/entry-media-type)})

  ;; Media type client sends
  :known-content-type? (by-method {
                          :options true
                          :post (fn [ctx] (api-common/known-content-type? ctx mt/share-request-media-type))})

  ;; Data handling
  :new? false 
  :respond-with-entity? true

  ;; Validations
  :processable? (by-method {
    :options true
    :post (fn [ctx] (valid-share-requests? conn entry-uuid (:data ctx)))})

  ;; Existentialism
  :can-post-to-missing? false
  :exists? (fn [ctx] (if-let* [_slugs? (and (slugify/valid-slug? org-slug)
                                            (slugify/valid-slug? board-slug))
                               org (or (:existing-org ctx)
                                       (org-res/get-org conn org-slug))
                               org-uuid (:uuid org)
                               entry (or (:existing-entry ctx)
                                         (entry-res/get-entry conn entry-uuid))
                               board (board-res/get-board conn (:board-uuid entry))
                               _matches? (and (= org-uuid (:org-uuid entry))
                                              (= org-uuid (:org-uuid board))
                                              (= :published (keyword (:status entry)))) ; sanity check
                               comments (or (:existing-comments ctx)
                                            (entry-res/list-comments-for-entry conn (:uuid entry)))
                               reactions (or (:existing-reactions ctx)
                                             (entry-res/list-reactions-for-entry conn (:uuid entry)))] 
                        {:existing-org org :existing-board board :existing-entry entry
                         :existing-comments comments :existing-reactions reactions}
                        false))
  
  ;; Actions
  :post! (fn [ctx] (share-entry conn ctx (s/join " " [org-slug board-slug entry-uuid])))

  ;; Responses
  :handle-ok (fn [ctx] (entry-rep/render-entry (:existing-org ctx)
                                               (:existing-board ctx)
                                               (:updated-entry ctx)
                                               (:existing-comments ctx)
                                               (:existing-reactions ctx)
                                               (:access-level ctx)
                                               (-> ctx :user :user-id)))
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (map #(schema/check common-res/ShareRequest %) (:share-requests ctx)))))


;; A resource for access to a particular entry by its secure UUID
(defresource entry-access [conn org-slug secure-uuid]
  (api-common/anonymous-resource config/passphrase) ; verify validity of optional JWToken

  :allowed-methods [:options :get]

  ;; Authorization
  :allowed? true

  ;; Media type client accepts
  :available-media-types (by-method {
                            :get [mt/entry-media-type]})
  :handle-not-acceptable (by-method {
                            :get (api-common/only-accept 406 mt/entry-media-type)})

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slug? (slugify/valid-slug? org-slug)
                               org (or (:existing-org ctx)
                                       (org-res/get-org conn org-slug))
                               org-uuid (:uuid org)
                               entry (or (:existing-entry ctx)
                                         (entry-res/get-entry-by-secure-uuid conn org-uuid secure-uuid))
                               board (board-res/get-board conn (:board-uuid entry))
                               _matches? (= org-uuid (:org-uuid board)) ; sanity check
                               access-level (or (:access-level (access/access-level-for org board (:user ctx))) :public)
                               comments (if (or (= :author access-level) (= :viewer access-level))
                                          (or (:existing-comments ctx)
                                              (entry-res/list-comments-for-entry conn (:uuid entry)))
                                          [])
                               reactions (if (or (= :author access-level) (= :viewer access-level))
                                          (or (:existing-reactions ctx)
                                              (entry-res/list-reactions-for-entry conn (:uuid entry)))
                                          [])]
                        {:existing-org org :existing-board board :existing-entry entry
                         :existing-comments comments :existing-reactions reactions :access-level access-level}
                        false))
  
  ;; Responses
  :handle-ok (fn [ctx] (entry-rep/render-entry (:existing-org ctx)
                                               (:slug (:existing-board ctx))
                                               (:existing-entry ctx)
                                               (:existing-comments ctx)
                                               (:existing-reactions ctx)
                                               (:access-level ctx)
                                               (-> ctx :user :user-id)
                                               :secure)))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Secure UUID access
      (ANY "/orgs/:org-slug/entries/:secure-uuid"
        [org-slug secure-uuid]
        (pool/with-pool [conn db-pool] 
          (entry-access conn org-slug secure-uuid)))
      (ANY "/orgs/:org-slug/entries/:secure-uuid/"
        [org-slug secure-uuid]
        (pool/with-pool [conn db-pool] 
          (entry-access conn org-slug secure-uuid)))
      ;; Entry list operations
      (ANY "/orgs/:org-slug/boards/:board-slug/entries"
        [org-slug board-slug]
        (pool/with-pool [conn db-pool] 
          (entry-list conn org-slug board-slug)))
      (ANY "/orgs/:org-slug/boards/:board-slug/entries/"
        [org-slug board-slug]
        (pool/with-pool [conn db-pool] 
          (entry-list conn org-slug board-slug)))
      ;; Entry operations
      (ANY "/orgs/:org-slug/boards/:board-slug/entries/:entry-uuid"
        [org-slug board-slug entry-uuid]
        (pool/with-pool [conn db-pool] 
          (entry conn org-slug board-slug entry-uuid)))
      (ANY "/orgs/:org-slug/boards/:board-slug/entries/:entry-uuid/"
        [org-slug board-slug entry-uuid]
        (pool/with-pool [conn db-pool]
          (entry conn org-slug board-slug entry-uuid)))
      (ANY "/orgs/:org-slug/boards/:board-slug/entries/:entry-uuid/share"
        [org-slug board-slug entry-uuid]
        (pool/with-pool [conn db-pool]
          (share conn org-slug board-slug entry-uuid)))
      (ANY "/orgs/:org-slug/boards/:board-slug/entries/:entry-uuid/share/"
        [org-slug board-slug entry-uuid]
        (pool/with-pool [conn db-pool]
          (share conn org-slug board-slug entry-uuid))))))