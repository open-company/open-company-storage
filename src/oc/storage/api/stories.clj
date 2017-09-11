(ns oc.storage.api.stories
  "Liberator API for story resources."
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
            [oc.storage.lib.email :as email]
            [oc.storage.lib.bot :as bot]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.story :as story-rep]
            [oc.storage.resources.common :as common-res]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.story :as story-res]))

;; ----- Utility functions -----

(defn- comments
  "Return a sequence of just the comments for an entry."
  [{interactions :interactions}]
  (filter :body interactions))

(defn- reactions
  "Return a sequence of just the reactions for an entry."
  [{interactions :interactions}]
  (filter :reaction interactions))

(defn- related-stories [conn org board story-uuid access user-id]
  
  (let [now (db-common/current-timestamp)
        ;; Get the 3 latest stories for the board
        latest-stories (db-common/read-resources-and-relations
                          conn story-res/table-name :status-board-uuid [[:published (:uuid board)]]
                          :published-at :desc now :before 3
                          :interactions common-res/interaction-table-name :uuid :resource-uuid
                          ["uuid" "body" "reaction" "author" "created-at" "updated-at"])
        ;; Eliminate the "current" story if it's one of the 3
        related-stories (filter #(not= (:uuid %) story-uuid) latest-stories)]
    ;; Return the 2 latest stories that remain as rendered stories
    (map #(story-rep/render-story-for-collection org board % (comments %) (reactions %) access user-id)
      (take 2 related-stories))))

(defun- trigger-share-requests
  "Parallel recursive function to send share requests to AWS SQS."

  ;; Initial
  ([org story user share-requests :guard seq?]
  (doall (pmap (partial trigger-share-requests org story user) share-requests)))

  ;; Email share
  ([org story user share-request :guard #(= "email" (:medium %)) ]
  (timbre/info "Triggering share: email for" (:uuid story) "of" (:slug org))
  (email/send-trigger! (email/->trigger org story share-request user)))

  ;; Slack share
  ([org story user share-request :guard #(= "slack" (:medium %))]
  (timbre/info "Triggering share: slack for" (:uuid story) "of" (:slug org))
  (bot/send-share-snapshot-trigger! (bot/->share-snapshot-trigger org story share-request user))))

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

(defn- valid-story-update? [conn story-uuid story-props]
  (if-let [existing-story (story-res/get-story conn story-uuid)]
    ;; Merge the existing story with the new updates
    (let [new-storyboard-slug (:storyboard-slug story-props) ; check if they are moving the story
          new-storyboard (when new-storyboard-slug ; look up the board it's being moved to
                          (board-res/get-board conn (:org-uuid existing-story) new-storyboard-slug))
          new-storyboard-uuid (when (= (:type new-storyboard) "story") (:uuid new-storyboard))
          props (if new-storyboard-uuid
                  (assoc story-props :board-uuid new-storyboard-uuid)
                  (dissoc story-props :board-uuid))
          updated-story (merge existing-story (story-res/ignore-props props))]
      (if (lib-schema/valid? common-res/Story updated-story)
        {:existing-story existing-story :updated-story updated-story}
        [false, {:updated-story updated-story}])) ; invalid update
    
    true)) ; no existing story, so this will fail existence check later

(defn- valid-share-requests? [conn story-uuid share-props]
  (if-let* [existing-story (story-res/get-story conn story-uuid)
            ts (db-common/current-timestamp)
            _seq? (seq? share-props)
            share-requests (map #(assoc % :shared-at ts) share-props)]
    (if (every? #(lib-schema/valid? common-res/ShareRequest %) share-requests)
        {:existing-story existing-story :share-requests share-requests}
        [false, {:share-requests share-requests}]) ; invalid share request
    
    true)) ; no existing story, so this will fail existence check later

;; ----- Actions -----

(defn- create-story [conn ctx story-for]
  (timbre/info "Creating story for:" story-for)
  (if-let* [new-story (:new-story ctx)
            story-result (story-res/create-story! conn new-story)] ; Add the story
    
    (do
      (timbre/info "Created story for:" story-for "as" (:uuid story-result))
      {:created-story story-result})

    (do (timbre/error "Failed creating story:" story-for) false)))

(defn- update-story [conn ctx story-for]
  (timbre/info "Updating story:" story-for)
  (if-let* [updated-story (:updated-story ctx)
            updated-result (story-res/update-story! conn (:uuid updated-story) updated-story (:user ctx))]
    (do 
      (timbre/info "Updated story:" story-for)
      {:updated-story updated-result})

    (do (timbre/error "Failed updating story:" story-for) false)))

(defn- delete-story [conn ctx story-for]
  (timbre/info "Deleting story:" story-for)
  (if-let* [story (:existing-story ctx)
            _delete-result (story-res/delete-story! conn (:uuid story))]
    (do (timbre/info "Deleted story:" story-for) true)
    (do (timbre/error "Failed deleting story:" story-for) false)))

(defn- publish-story [conn ctx story-for]
  (timbre/info "Publishing story:" story-for)
  (if-let* [org (:existing-org ctx)
            story (:existing-story ctx)
            user (:user ctx)
            share-requests (or (:share-requests ctx) true)
            shared (if (seq? share-requests)
                      {:shared (concat (or (:shared story) []) share-requests)}
                      true)
            publish-result (if (map? shared)
                            (story-res/publish-story! conn (:uuid story) shared user)
                            (story-res/publish-story! conn (:uuid story) user))]
    (do
      (when (and (seq? share-requests)(any? share-requests))
        (trigger-share-requests org publish-result user share-requests))
      (timbre/info "Published story:" story-for)
      {:updated-story publish-result})
    (do (timbre/error "Failed publishing story:" story-for) false)))

(defn- share-story [conn ctx story-for]
  (timbre/info "Sharing story:" story-for)
  (if-let* [org (:existing-org ctx)
            story (:existing-story ctx)
            user (:user ctx)
            share-requests (:share-requests ctx)
            shared {:shared (concat (or (:shared story) []) share-requests)}
            update-result (story-res/update-story! conn (:uuid story) shared user)]
    (do
      (when (and (seq? share-requests)(any? share-requests)) (trigger-share-requests org story user share-requests))
      (timbre/info "Shared story:" story-for)
      {:updated-story update-result})
    (do (timbre/error "Failed sharing story:" story-for) false)))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for operations on a particular story
(defresource story [conn org-slug board-slug story-uuid]
  (api-common/open-company-anonymous-resource config/passphrase) ; verify validity of optional JWToken

  :allowed-methods [:options :get :patch :delete]

  ;; Media type client accepts
  :available-media-types [mt/story-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/story-media-type)
  
  ;; Media type client sends
  :known-content-type? (by-method {
    :options true
    :get true
    :patch (fn [ctx] (api-common/known-content-type? ctx mt/story-media-type))
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
                          (valid-story-update? conn story-uuid (:data ctx))))
    :delete true})

  ;; Existentialism
  :exists? (fn [ctx] (let [draft? (and (:user ctx) (= board-slug (:slug board-res/default-drafts-storyboard)))]
                        (if-let* [_slugs? (and (slugify/valid-slug? org-slug)
                                               (slugify/valid-slug? board-slug))
                                  org (or (:existing-org ctx)
                                          (org-res/get-org conn org-slug))
                                  org-uuid (:uuid org)
                                  story (or (:existing-story ctx) (story-res/get-story conn story-uuid))
                                  board (or (:existing-board ctx)
                                            (if draft?
                                              (board-res/get-board conn (:board-uuid story))
                                              (board-res/get-board conn org-uuid board-slug)))
                                  _matches? (and (= org-uuid (:org-uuid story))
                                                 (= org-uuid (:org-uuid board)))
                                  _storyboard? (= (keyword (:type board)) :story)
                                  comments (or (:existing-comments ctx)
                                               (story-res/list-comments-for-story conn (:uuid story)))
                                  reactions (if draft? ; drafts don't have reactions
                                              []
                                              (or (:existing-reactions ctx)
                                                  (story-res/list-reactions-for-story conn (:uuid story))))]
                        {:existing-org org :existing-board board :existing-story story
                         :existing-comments comments :existing-reactions reactions}
                        false)))
  
  ;; Actions
  :patch! (fn [ctx] (update-story conn ctx (s/join " " [org-slug board-slug story-uuid])))
  :delete! (fn [ctx] (delete-story conn ctx (s/join " " [org-slug board-slug story-uuid])))

  ;; Responses
  :handle-ok (by-method {
    :get (fn [ctx] (let [org (:existing-org ctx)
                         board (:existing-board ctx)
                         draft? (= board-slug (:slug board-res/default-drafts-storyboard))
                         story (:existing-story ctx)
                         access (:access-level ctx)
                         user-id (-> ctx :user :user-id)]
                      (if draft? ; drafts don't have related stories
                        (story-rep/render-story org board story
                                                (:existing-comments ctx)
                                                nil ; no reactions
                                                access user-id)
                        (story-rep/render-story org board story
                                                (:existing-comments ctx)
                                                (:existing-reactions ctx)
                                                (related-stories conn org board (:uuid story) access user-id)
                                                access user-id))))
    :patch (fn [ctx] (story-rep/render-story (:existing-org ctx)
                                             (:existing-board ctx)
                                             (:updated-story ctx)
                                             (:existing-comments ctx)
                                             (:existing-reactions ctx)
                                             (:access-level ctx)
                                             (-> ctx :user :user-id)))})
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (schema/check common-res/Story (:updated-story ctx)))))

;; A resource for operations on all stories of a particular board
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
  :exists? (fn [ctx] (let [user (:user ctx)
                           draft? (and user (= board-slug (:slug board-res/default-drafts-storyboard)))]
                        (if-let* [_slugs? (and (slugify/valid-slug? org-slug)
                                               (slugify/valid-slug? board-slug))
                                  org (org-res/get-org conn org-slug)
                                  org-uuid (:uuid org)
                                  board (if draft?
                                          (board-res/drafts-storyboard org-uuid user)
                                          (or (:existing-board ctx) (board-res/get-board conn org-uuid board-slug)))
                                  board-uuid (:uuid board)
                                  storyboard? (= (keyword (:type board)) :story)
                                  stories (if draft?
                                            (story-res/list-stories-by-org-author conn org-uuid (:user-id user))
                                            (story-res/list-stories-by-board conn board-uuid))]
                        {:existing-stories stories :existing-board board :existing-org org}
                        false)))

  ;; Actions
  :post! (fn [ctx] (create-story conn ctx (s/join " " [org-slug board-slug])))

  ;; Responses
  :handle-ok (fn [ctx] (story-rep/render-story-list (:existing-org ctx) board-slug
                          (:existing-stories ctx) (:access-level ctx) (-> ctx :user :user-id)))
  :handle-created (fn [ctx] (let [new-story (:created-story ctx)
                                  org (:existing-org ctx)
                                  org-uuid (:uuid org)
                                  user (:user ctx) 
                                  draft-board (board-res/drafts-storyboard org-uuid user)]
                              (api-common/location-response
                                (story-rep/url org-slug board-slug (:uuid new-story))
                                (story-rep/render-story org draft-board new-story [] []
                                  :author (:uuid user))
                                mt/story-media-type)))
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (:reason ctx))))

;; A resource for operations to publish a particular story
(defresource publish [conn org-slug board-slug story-uuid]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :post]

  ;; Authorization
  :allowed? (by-method {
    :options true
    :post (fn [ctx] (access/allow-authors conn org-slug (:user ctx)))})

  ;; Media type client accepts
  :available-media-types (by-method {
                            :post [mt/story-media-type]})
  :handle-not-acceptable (by-method {
                            :post (api-common/only-accept 406 mt/story-media-type)})

  ;; Possibly no data to handle
  :malformed? (by-method {
    :options false
    :post (fn [ctx] (api-common/malformed-json? ctx true))}) ; allow nil
  :processable? (by-method {
    :options true
    :post (fn [ctx] (let [share-props (:data ctx)]
                      (or (nil? share-props) ; no share is fine
                          (valid-share-requests? conn story-uuid share-props))))})
  :new? false 
  :respond-with-entity? true

  ;; Existentialism
  :can-post-to-missing? false
  :exists? (fn [ctx] (if-let* [_slugs? (and (slugify/valid-slug? org-slug)
                                            (slugify/valid-slug? board-slug))
                               org (or (:existing-org ctx)
                                       (org-res/get-org conn org-slug))
                               org-uuid (:uuid org)
                               story (or (:existing-story ctx)
                                         (story-res/get-story conn story-uuid))
                               board (board-res/get-board conn (:board-uuid story))
                               _matches? (and (= org-uuid (:org-uuid story))
                                              (= org-uuid (:org-uuid board))
                                              (= :draft (keyword (:status story))))] ; sanity check
                        {:existing-org org :existing-board board :existing-story story}
                        false))
  
  ;; Actions
  :post! (fn [ctx] (publish-story conn ctx (s/join " " [org-slug board-slug story-uuid])))

  ;; Responses
  :handle-ok (fn [ctx] (story-rep/render-story (:existing-org ctx)
                                               (:existing-board ctx)
                                               (:updated-story ctx)
                                               [] ; no comments
                                               [] ; no reactions
                                               (:access-level ctx)
                                               (-> ctx :user :user-id))))

;; A resource for operations to share a particular story
(defresource share [conn org-slug board-slug story-uuid]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :post]

  ;; Authorization
  :allowed? (by-method {
    :options true
    :post (fn [ctx] (access/allow-authors conn org-slug (:user ctx)))})
  
  ;; Media type client accepts
  :available-media-types (by-method {
                            :post [mt/story-media-type]})
  :handle-not-acceptable (by-method {
                            :post (api-common/only-accept 406 mt/story-media-type)})

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
    :post (fn [ctx] (valid-share-requests? conn story-uuid (:data ctx)))})

  ;; Existentialism
  :can-post-to-missing? false
  :exists? (fn [ctx] (if-let* [_slugs? (and (slugify/valid-slug? org-slug)
                                            (slugify/valid-slug? board-slug))
                               org (or (:existing-org ctx)
                                       (org-res/get-org conn org-slug))
                               org-uuid (:uuid org)
                               story (or (:existing-story ctx)
                                         (story-res/get-story conn story-uuid))
                               board (board-res/get-board conn (:board-uuid story))
                               comments (or (:existing-comments ctx)
                                            (story-res/list-comments-for-story conn (:uuid story)))
                               reactions (or (:existing-reactions ctx)
                                             (story-res/list-reactions-for-story conn (:uuid story)))
                               _matches? (and (= org-uuid (:org-uuid story))
                                              (= org-uuid (:org-uuid board))
                                              (= :published (keyword (:status story))))] ; sanity check
                        {:existing-org org :existing-board board :existing-story story
                         :existing-comments comments :existing-reactions reactions}
                        false))
  
  ;; Actions
  :post! (fn [ctx] (share-story conn ctx (s/join " " [org-slug board-slug story-uuid])))

  ;; Responses
  :handle-ok (fn [ctx] (story-rep/render-story (:existing-org ctx)
                                               (:existing-board ctx)
                                               (:updated-story ctx)
                                               (:existing-comments ctx)
                                               (:existing-reactions ctx)
                                               (:access-level ctx)
                                               (-> ctx :user :user-id)))
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (map #(schema/check common-res/ShareRequest %) (:share-requests ctx)))))

;; A resource for access to a particular story by its secure UUID
(defresource story-access [conn org-slug secure-uuid]
  (api-common/anonymous-resource config/passphrase) ; verify validity of optional JWToken

  :allowed-methods [:options :get]

  ;; Authorization
  :allowed? true

  ;; Media type client accepts
  :available-media-types (by-method {
                            :get [mt/story-media-type]})
  :handle-not-acceptable (by-method {
                            :get (api-common/only-accept 406 mt/story-media-type)})

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slug? (slugify/valid-slug? org-slug)
                               org (or (:existing-org ctx)
                                       (org-res/get-org conn org-slug))
                               org-uuid (:uuid org)
                               story (or (:existing-story ctx)
                                         (story-res/get-story-by-secure conn org-uuid secure-uuid))
                               board (board-res/get-board conn (:board-uuid story))
                               access-level (or (:access-level (access/access-level-for org board (:user ctx))) :public)
                               reactions? (or (= :author access-level) (= :viewer access-level))
                               comments (if reactions?
                                          (or (:existing-comments ctx)
                                              (story-res/list-comments-for-story conn (:uuid story)))
                                          [])
                               reactions (if reactions?
                                          (or (:existing-reactions ctx)
                                              (story-res/list-reactions-for-story conn (:uuid story)))
                                          [])
                               _matches? (= org-uuid (:org-uuid board))] ; sanity check
                        {:existing-org org :existing-board board :existing-story story
                         :existing-comments comments :existing-reactions reactions :access-level access-level}
                        false))
  
  ;; Responses
  :handle-ok (fn [ctx] (story-rep/render-story (:existing-org ctx)
                                               (:existing-board ctx)
                                               (:existing-story ctx)
                                               (:existing-comments ctx)
                                               (:existing-reactions ctx)
                                               (:access-level ctx)
                                               :secure)))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Secure Story access
      (ANY "/orgs/:org-slug/stories/:secure-uuid"
        [org-slug secure-uuid]
        (pool/with-pool [conn db-pool] 
          (story-access conn org-slug secure-uuid)))
      (ANY "/orgs/:org-slug/stories/:secure-uuid/"
        [org-slug secure-uuid]
        (pool/with-pool [conn db-pool] 
          (story-access conn org-slug secure-uuid)))
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
      (ANY "/orgs/:org-slug/boards/:board-slug/stories/:story-uuid"
        [org-slug board-slug story-uuid]
        (pool/with-pool [conn db-pool] 
          (story conn org-slug board-slug story-uuid)))
      (ANY "/orgs/:org-slug/boards/:board-slug/stories/:story-uuid/"
        [org-slug board-slug story-uuid]
        (pool/with-pool [conn db-pool]
          (story conn org-slug board-slug story-uuid)))
      (ANY "/orgs/:org-slug/boards/:board-slug/stories/:story-uuid/publish"
        [org-slug board-slug story-uuid]
        (pool/with-pool [conn db-pool]
          (publish conn org-slug board-slug story-uuid)))
      (ANY "/orgs/:org-slug/boards/:board-slug/stories/:story-uuid/publish/"
        [org-slug board-slug story-uuid]
        (pool/with-pool [conn db-pool]
          (publish conn org-slug board-slug story-uuid)))
      (ANY "/orgs/:org-slug/boards/:board-slug/stories/:story-uuid/share"
        [org-slug board-slug story-uuid]
        (pool/with-pool [conn db-pool]
          (share conn org-slug board-slug story-uuid)))
      (ANY "/orgs/:org-slug/boards/:board-slug/stories/:story-uuid/share/"
        [org-slug board-slug story-uuid]
        (pool/with-pool [conn db-pool]
          (share conn org-slug board-slug story-uuid))))))