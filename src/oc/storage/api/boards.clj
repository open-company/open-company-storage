(ns oc.storage.api.boards
  "Liberator API for board resources."
  (:require [if-let.core :refer (if-let*)]
            [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (defroutes ANY OPTIONS POST)]
            [liberator.core :refer (defresource by-method)]
            [cheshire.core :as json]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.api.common :as storage-common]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.board :as board-rep]
            [oc.storage.representations.entry :as entry-rep]
            [oc.storage.resources.common :as common-res]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.entry :as entry-res]))

;; ----- Utility functions -----

(defn- assemble-board [conn org-slug slug ctx]
  (let [board (or (:updated-board ctx) (:existing-board ctx))
        topic-slugs (map name (:topics board)) ; slug for each active topic
        entries (entry-res/get-entries-by-board conn (:uuid board)) ; latest entry for each topic
        selected-entries (select-keys entries topic-slugs) ; active entries
        selected-entry-reps (zipmap topic-slugs
                              (map #(entry-rep/render-entry-for-collection org-slug slug
                                      (get selected-entries %) (:access-level ctx))
                                topic-slugs))
                                 archived-entries (clojure.set/difference (set (keys entries)) (set topic-slugs))
                                 archived (map #(identity {:slug % :title (:title (get entries %))}) archived-entries)]
    (merge (assoc board :archived archived) selected-entry-reps)))

;; ----- Validations -----

(defn- valid-new-board? [conn org-slug {board-map :data author :user}]
  (if-let [org (org-res/get-org conn org-slug)]
    (try
      {:new-board (board-res/->board (:uuid org) board-map author) :existing-org org}

      (catch clojure.lang.ExceptionInfo e
        [false, {:reason (.getMessage e)}])) ; Not a valid new board
    [false, {:reason :invalid-org}])) ; couldn't find the specified org

(defn- valid-board-update? [conn org-slug slug board-props]
  (if-let* [org (org-res/get-org conn org-slug)
            board (board-res/get-board conn (:uuid org) slug)]
    (let [updated-board (merge board (board-res/clean board-props))]
      (if (lib-schema/valid? common-res/Board updated-board)
        {:existing-org org :existing-board board :updated-board updated-board}
        [false, {:updated-board updated-board}])) ; invalid update
    true)) ; No org or board, so this will fail existence check later

;; ----- Actions -----

(defn- create-board [conn {access-level :access-level new-board :new-board} org-slug]
  (timbre/info "Creating board for org:" org-slug)
  (if-let* [access (if (= :author access-level) :team :private)
            board-result (board-res/create-board! conn (assoc new-board :access access))] ; Add the board
    
    (do
      (timbre/info "Created board:" (:uuid board-result) "for org:" org-slug)
      {:new-board board-result})
    
    (do (timbre/error "Failed creating board for org:" org-slug) false)))

(defn- update-board [conn ctx org-slug slug]
  (timbre/info "Updating board:" slug "of org:" org-slug)
  (if-let* [updated-board (:updated-board ctx)
            updated-result (board-res/update-board! conn (:uuid updated-board) updated-board)]
    (do 
      (timbre/info "Updated board:" slug "of org:" org-slug)
      {:updated-board updated-result})

    (do (timbre/error "Failed updating board:" slug "of org:" org-slug) false)))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for operations on a particular board
(defresource board [conn org-slug slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get :patch]

  ;; Media type client accepts
  :available-media-types [mt/board-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/board-media-type)

  ;; Media type client sends
  :known-content-type? (by-method {
    :options true
    :get true
    :patch (fn [ctx] (api-common/known-content-type? ctx mt/board-media-type))})
  
  ;; Authorization
  :allowed? (by-method {
    :options true
    :get (fn [ctx] (storage-common/access-level-for conn org-slug slug (:user ctx)))
    :patch (fn [ctx] (storage-common/allow-authors conn org-slug slug (:user ctx)))})

  ;; Validations
  :processable? (by-method {
    :options true
    :get true
    :patch (fn [ctx] (valid-board-update? conn org-slug slug (:data ctx)))})

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [org (or (:existing-org ctx) (org-res/get-org conn org-slug))
                               board (or (:existing-board ctx) (board-res/get-board conn (:uuid org) slug))]
                        {:existing-org org :existing-board board}
                        false))

  ;; Actions
  :patch! (fn [ctx] (update-board conn ctx org-slug slug))

  ;; Responses
  :handle-ok (fn [ctx] (let [board (assemble-board conn org-slug slug ctx)]
                          (board-rep/render-board org-slug board (:access-level ctx))))
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (schema/check common-res/Board (:updated-board ctx)))))


;; A resource for operations on a list of boards
(defresource board-list [conn org-slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :post]

  ;; Media type client accepts
  :available-media-types [mt/board-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/board-media-type)
  
  ;; Media type client sends
  :known-content-type? (by-method {
                          :options true
                          :post (fn [ctx] (api-common/known-content-type? ctx mt/board-media-type))})

  ;; Authorization
  :allowed? (by-method {
    :options true
    :post (fn [ctx] (storage-common/allow-members conn org-slug (:user ctx)))})

  ;; Validations
  :processable? (by-method {
    :options true
    :post (fn [ctx] (valid-new-board? conn org-slug ctx))})

  ;; Actions
  :post! (fn [ctx] (create-board conn ctx org-slug))

  ;; Responses
  :handle-created (fn [ctx] (let [new-board (:new-board ctx)
                                  board-slug (:slug new-board)]
                              (api-common/location-response
                                (board-rep/url org-slug board-slug)
                                (board-rep/render-board org-slug new-board (:access-level ctx))
                                mt/board-media-type)))
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (:reason ctx))))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Board operations
      (ANY "/orgs/:org-slug/boards/:slug" [org-slug slug] (pool/with-pool [conn db-pool] (board conn org-slug slug)))
      ;; Board creation
      (OPTIONS "/orgs/:org-slug/boards/" [org-slug] (pool/with-pool [conn db-pool] (board-list conn org-slug)))
      (POST "/orgs/:org-slug/boards/" [org-slug] (pool/with-pool [conn db-pool] (board-list conn org-slug))))))