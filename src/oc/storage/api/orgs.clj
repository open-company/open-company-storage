(ns oc.storage.api.orgs
  "Liberator API for team resources."
  (:require [if-let.core :refer (if-let*)]
            [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (ANY OPTIONS POST DELETE)]
            [liberator.core :refer (defresource by-method)]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [oc.lib.slugify :as slugify]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.api.access :as access]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.representations.board :as board-rep]
            [oc.storage.resources.common :as common-res]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.update :as update-res]))

;; ----- Actions -----

(defn create-org [conn ctx]
  (timbre/info "Creating org.")
  (if-let* [new-org (:new-org ctx)
            org-result (org-res/create-org! conn new-org)] ; Add the org

    ;; Org creation succeeded, so create the default boards
    (let [uuid (:uuid org-result)
          author (:user ctx)]
      (timbre/info "Created org:" uuid)
      (timbre/info "Creating default boards for org:" uuid)
      {:new-org (assoc org-result :boards
                  (map
                    #(board-res/create-board! conn
                      (board-res/->board uuid {:name %} author))
                    board-res/default-boards))})
    
    (do (timbre/error "Failed creating org.") false)))

(defn- update-org [conn ctx slug]
  (timbre/info "Updating org:" slug)
  (if-let* [updated-org (:updated-org ctx)
            update-result (org-res/update-org! conn slug updated-org)]
    (do
      (timbre/info "Updated org:" slug)
      {:updated-org update-result})

    (do (timbre/error "Failed updating org:" slug) false)))

(defn- add-author [conn ctx slug user-id]
  (timbre/info "Adding author:" user-id "to org:" slug)
  (if-let [updated-org (org-res/add-author conn slug user-id)]
    (do
      (timbre/info "Added author:" user-id "to org:" slug)
      {:updated-org updated-org})
    
    (do
      (timbre/error "Failed adding author:" user-id "to org:" slug)
      false)))

(defn- remove-author [conn ctx slug user-id]
  (timbre/info "Removing author:" user-id "from org:" slug)
  (if-let [updated-org (org-res/remove-author conn slug user-id)]
    (do
      (timbre/info "Removed author:" user-id "from org:" slug)
      {:updated-org updated-org})
    
    (do
      (timbre/error "Failed removing author:" user-id "to org:" slug)
      false)))

;; ----- Validations -----

(defn- valid-new-org? [conn ctx]
  (try
    ;; Create the new org from the data provided
    (let [org-map (:data ctx)
          author (:user ctx)]
      {:new-org (org-res/->org org-map author)})

    (catch clojure.lang.ExceptionInfo e
      [false, {:reason (.getMessage e)}]))) ; Not a valid new org

(defn- valid-org-update? [conn slug org-props]
  (if-let [org (org-res/get-org conn slug)]
    (let [updated-org (merge org (org-res/ignore-props org-props))]
      (if (lib-schema/valid? common-res/Org updated-org)
        {:existing-org org :updated-org updated-org}
        [false, {:updated-org updated-org}])) ; invalid update
    true)) ; No org for this slug, so this will fail existence check later

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for operations on a particular Org
(defresource org [conn slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get :patch]

  ;; Media type client accepts
  :available-media-types [mt/org-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/org-media-type)

  ;; Media type client sends
  :known-content-type? (by-method {
    :options true
    :get true
    :patch (fn [ctx] (api-common/known-content-type? ctx mt/org-media-type))})
  
  ;; Authorization
  :allowed? (by-method {
    :options true
    :get (fn [ctx] (access/access-level-for conn slug (:user ctx)))
    :patch (fn [ctx] (access/allow-authors conn slug (:user ctx)))})

  ;; Validations
  :processable? (by-method {
    :options true
    :get true
    :patch (fn [ctx] (valid-org-update? conn slug (:data ctx)))})

  ;; Existentialism
  :exists? (fn [ctx] (if-let [org (or (:existing-org ctx) (org-res/get-org conn slug))]
                        {:existing-org org}
                        false))

  ;; Actions
  :patch! (fn [ctx] (update-org conn ctx slug))

  ;; Responses
  :handle-ok (fn [ctx] (let [user (:user ctx)
                             user-id (:user-id user)
                             org (or (:updated-org ctx) (:existing-org ctx))
                             org-id (:uuid org)
                             boards (board-res/list-boards-by-org conn org-id [:created-at :updated-at]) ; TODO Filter out private boards
                             board-reps (map #(board-rep/render-board-for-collection slug %) boards)
                             authors (:authors org)
                             author-reps (map #(org-rep/render-author-for-collection org %) authors)
                             update-count (count (update-res/list-updates-by-author conn org-id user-id))]
                          (org-rep/render-org (-> org
                                                (assoc :boards board-reps)
                                                (assoc :authors author-reps)
                                                (assoc :update-count update-count))
                                              (:access-level ctx))))
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (schema/check common-res/Org (:updated-org ctx)))))

;; A resource for the authors of a particular org
(defresource author [conn org-slug user-id]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :post :delete]

  ;; Media type client accepts
  :available-media-types [mt/org-author-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/org-author-media-type)

  ;; Media type client sends
  :malformed? (by-method {
    :options false
    :post (fn [ctx] (access/malformed-user-id? ctx))
    :delete false})
  :known-content-type? (by-method {
    :options true
    :post (fn [ctx] (api-common/known-content-type? ctx mt/org-author-media-type))
    :delete true})

  ;; Authorization
  :allowed? (by-method {
    :options true
    :post (fn [ctx] (access/allow-authors conn org-slug (:user ctx)))
    :delete (fn [ctx] (access/allow-authors conn org-slug (:user ctx)))})

  ;; Existentialism
  :exists? (by-method {
    :post (fn [ctx] (if-let [org (and (slugify/valid-slug? org-slug) (org-res/get-org conn org-slug))]
                        {:existing-org org :existing-author? ((set (:authors org)) (:data ctx))}
                        false))
    :delete (fn [ctx] (if-let* [org (and (slugify/valid-slug? org-slug) (org-res/get-org conn org-slug))
                                exists? ((set (:authors org)) user-id)] ; short circuits the delete w/ a 404
                        {:existing-org org :existing-author? true}
                        false))}) ; org or author doesn't exist

  ;; Actions
  :post! (fn [ctx] (when-not (:existing-author? ctx) (add-author conn ctx org-slug (:data ctx))))
  :delete! (fn [ctx] (when (:existing-author? ctx) (remove-author conn ctx org-slug user-id)))
  
  ;; Responses
  :respond-with-entity? false
  :handle-created (fn [ctx] (if (:existing-org ctx)
                              (api-common/blank-response)
                              (api-common/missing-response)))
  :handle-no-content (fn [ctx] (when-not (:updated-org ctx) (api-common/missing-response)))
  :handle-options (if user-id
                    (api-common/options-response [:options :delete])
                    (api-common/options-response [:options :post])))


;; A resource for operations on a list of orgs
(defresource org-list [conn]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :post]

  ;; Media type client accepts
  :available-media-types [mt/org-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/org-media-type)
  
  ;; Media type client sends
  :known-content-type? (by-method {
                          :options true
                          :post (fn [ctx] (api-common/known-content-type? ctx mt/org-media-type))})

  ;; Authorization
  :allowed? (by-method {
    :options true
    :post (fn [ctx] (access/allow-team-admins-or-no-org
                      conn (:user ctx)))}) ; don't allow non-team-admins to get stuck w/ no org

  ;; Validations
  :processable? (by-method {
    :options true
    :post (fn [ctx] (valid-new-org? conn ctx))})

  ;; Actions
  :post! (fn [ctx] (create-org conn ctx))

  ;; Responses
  :handle-created (fn [ctx] (let [new-org (:new-org ctx)
                                  slug (:slug new-org)]
                              (api-common/location-response
                                (org-rep/url slug)
                                (org-rep/render-org (assoc new-org :update-count 0) :author)
                                mt/org-media-type)))
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (:reason ctx))))
;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Org creation
      (ANY "/orgs" [] (pool/with-pool [conn db-pool] (org-list conn)))
      (ANY "/orgs/" [] (pool/with-pool [conn db-pool] (org-list conn)))
      ;; Org operations
      (ANY "/orgs/:slug" [slug] (pool/with-pool [conn db-pool] (org conn slug)))
      (ANY "/orgs/:slug/" [slug] (pool/with-pool [conn db-pool] (org conn slug)))
      ;; Org author operations
      (OPTIONS "/orgs/:slug/authors" [slug] (pool/with-pool [conn db-pool] (author conn slug nil)))
      (OPTIONS "/orgs/:slug/authors/" [slug] (pool/with-pool [conn db-pool] (author conn slug nil)))
      (POST "/orgs/:slug/authors" [slug] (pool/with-pool [conn db-pool] (author conn slug nil)))
      (POST "/orgs/:slug/authors/" [slug] (pool/with-pool [conn db-pool] (author conn slug nil)))
      (OPTIONS "/orgs/:slug/authors/:user-id" [slug user-id] (pool/with-pool [conn db-pool]
        (author conn slug user-id)))
      (OPTIONS "/orgs/:slug/authors/:user-id/" [slug user-id]
        (pool/with-pool [conn db-pool] (author conn slug user-id)))
      (DELETE "/orgs/:slug/authors/:user-id" [slug user-id]
        (pool/with-pool [conn db-pool] (author conn slug user-id)))
      (DELETE "/orgs/:slug/authors/:user-id/" [slug user-id] (pool/with-pool [conn db-pool]
        (author conn slug user-id))))))