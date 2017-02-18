(ns oc.storage.api.updates
  "Liberator API for update resources."
  (:require [if-let.core :refer (if-let*)]
            [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (ANY)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.api.common :as storage-common]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.update :as update-rep]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.update :as update-res]))

;; ----- Validations -----

(defn- valid-share-request? [conn org-slug {share-request :data author :user}]
  (if-let [org (org-res/get-org conn org-slug)]
    (try
        (println "SR:" share-request)
        {:new-update (update-res/->update conn org-slug share-request author) :existing-org org}

      (catch clojure.lang.ExceptionInfo e
        [false, {:existing-org org :reason (.getMessage e)}])) ; Not a valid share request
    false)) ; couldn't find the specified org

;; ----- Actions -----

(defn- create-update [conn {title :title :as new-update} org-slug]
  (timbre/info "Creating update '" title "' for org:" org-slug)
  (if-let [update-result (update-res/create-update! conn new-update)] ; Add the update
    
    (do
      (timbre/info "Created update '" title "' for org:" org-slug)
      {:new-update update-result})
    
    (do (timbre/error "Failed creating update for org:" org-slug) false)))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for operations on a particular update
(defresource update [conn org-slug slug]
  (api-common/anonymous-resource config/passphrase) ; verify validity of optional JWToken

  :allowed-methods [:options :get]

  ;; Media type client accepts
  :available-media-types [mt/update-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/update-media-type)
  
  ;; Authorization
  :allowed? (by-method {
    :options true
    :get  (fn [ctx] (api-common/allow-anonymous ctx))}) ; these are ObscURLs, so if you know what you're getting it's OK

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [org (org-res/get-org conn org-slug)
                               update (update-res/get-update conn (:uuid org) slug)]
                        {:existing-update update}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (update-rep/render-update org-slug (:existing-update ctx))))

;; A resource for operations on a list of updates
(defresource update-list [conn org-slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get :post]

  ;; Media type client accepts
  :available-media-types (by-method {
    :get [mt/update-collection-media-type]
    :post [mt/update-media-type]})
  :handle-not-acceptable (by-method {
    :get (api-common/only-accept 406 mt/update-collection-media-type)
    :post (api-common/only-accept 406 mt/update-media-type)})
  
  ;; Media type client sends
  :known-content-type? (by-method {
                          :options true
                          :get true
                          :post (fn [ctx] (api-common/known-content-type? ctx mt/share-request-media-type))})

  ;; Authorization
  :allowed? (by-method {
    :options true
    :get (fn [ctx] (storage-common/allow-authors conn org-slug (:user ctx)))
    :post (fn [ctx] (storage-common/allow-authors conn org-slug (:user ctx)))})

  ;; Validations
  :processable? (by-method {
    :options true
    :get true
    :post (fn [ctx] (valid-share-request? conn org-slug ctx))})

  ;; Actions
  :post! (fn [ctx] (create-update conn (:new-update ctx) org-slug))

  ;; Responses
  :handle-created (fn [ctx] (let [new-update (:new-update ctx)
                                  update-slug (:slug new-update)]
                              (api-common/location-response
                                (update-rep/url org-slug update-slug)
                                (update-rep/render-update org-slug new-update)
                                mt/update-media-type)))
  :handle-unprocessable-entity (fn [ctx] (if (:existing-org ctx)
                                            (api-common/unprocessable-entity-response (:reason ctx))
                                            (api-common/missing-response))))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Update operations
      (ANY "/orgs/:org-slug/updates/:slug" [org-slug slug] (pool/with-pool [conn db-pool] (update conn org-slug slug)))
      (ANY "/orgs/:org-slug/updates/:slug/" [org-slug slug] (pool/with-pool [conn db-pool] (update conn org-slug slug)))
      ;; Update list operations
      (ANY "/orgs/:org-slug/updates" [org-slug] (pool/with-pool [conn db-pool] (update-list conn org-slug)))
      (ANY "/orgs/:org-slug/updates/" [org-slug] (pool/with-pool [conn db-pool] (update-list conn org-slug))))))