(ns oc.storage.api.updates
  "Liberator API for update resources."
  (:require [defun.core :refer (defun-)]
            [if-let.core :refer (if-let*)]
            [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (ANY)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.api.access :as access]
            [oc.storage.lib.email :as email]
            [oc.storage.lib.bot :as bot]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.update :as update-rep]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.update :as update-res]))

;; ----- Validations -----

(defun- slack-bot-for-share
  "If a share request is for Slack, make sure we have a Slack bot for the Slack org."
  
  ;; Slack share requests need a bot
  ([share-request :guard #(= (:medium %) "slack") author]
  (let [slack-org (:slack-org share-request)
        slack-bots (flatten (vals (:slack-bots author)))]
    (first (filter #(= slack-org (:slack-org %)) slack-bots))))

  ;; Other share requests don't need a Slack bot
  ([_share-request _author] :not-applicable))

(defn- valid-share-request? [conn org-slug {share-request :data author :user}]
  (if-let [org (org-res/get-org conn org-slug)]
    (try
      (if-let* [new-update (update-res/->update conn org-slug share-request author)
                slack-bot (slack-bot-for-share share-request author)]
        {:new-update new-update :existing-org org :slack-bot (dissoc slack-bot :slack-org)}
        [false, {:existing-org org :reason "Slack bot not configured."}])
      (catch clojure.lang.ExceptionInfo e
        [false, {:existing-org org :reason (.getMessage e)}])) ; Not a valid share request
    false)) ; couldn't find the specified org

;; ----- Actions -----

(defn- create-update [conn org-slug {{title :title :as new-update} :new-update user :user :as ctx}]
  (timbre/info "Creating update '" title "' for org:" org-slug)
  (if-let* [update-result (update-res/create-update! conn (update new-update :to distinct)) ; Add the update
            origin-url (get-in ctx [:request :headers "origin"])]
    
    (do
      (timbre/info "Created update '" title "' for org:" org-slug)
      (case (keyword (:medium update-result))
        :email (email/send-trigger! (email/->trigger org-slug update-result origin-url user))
        :slack (bot/send-trigger! (bot/->trigger org-slug update-result origin-url ctx))
        :link nil) ; no-op
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
    :get (fn [ctx] (access/allow-authors conn org-slug (:user ctx)))
    :post (fn [ctx] (access/allow-authors conn org-slug (:user ctx)))})

  ;; Validations
  :processable? (by-method {
    :options true
    :get true
    :post (fn [ctx] (valid-share-request? conn org-slug ctx))})

  ;; Existentialism
  :exists? (by-method {
    :get (fn [ctx] (if-let* [org (org-res/get-org conn org-slug)]
                        {:existing-org org}
                        false))})

  ;; Actions
  :post! (fn [ctx] (create-update conn org-slug ctx))

  ;; Responses
  :handle-ok (fn [ctx] (let [org (:existing-org ctx)
                             user (:user ctx)
                             updates (update-res/list-updates-by-author conn (:uuid org) (:user-id user))]
                          (update-rep/render-update-list org-slug updates)))
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