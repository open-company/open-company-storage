(ns open-company.api.stakeholder-updates
  (:require [if-let.core :refer (if-let* when-let*)]
            [compojure.core :refer (routes OPTIONS GET POST DELETE)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.rethinkdb.pool :as pool]
            [open-company.lib.bot :as bot]
            [open-company.lib.email :as email]
            [open-company.api.common :as common]
            [open-company.resources.company :as company-res]
            [open-company.resources.stakeholder-update :as su-res]
            [open-company.representations.company :as company-rep]
            [open-company.representations.stakeholder-update :as su-rep]))

;; ----- Responses -----

(defn- options-for-stakeholder-update
  "
  List of HTTP methods for the stakeholder update resource.

  Only return DELETE if the user is authorized for this company.
  "
  [conn company-slug slug ctx]
  (if-let* [company (company-res/get-company conn company-slug)
            _stakeholder-update (su-res/get-stakeholder-update conn company-slug slug)]
    (if (common/authorized-to-company? (assoc ctx :company company))
      (common/options-response [:options :get :delete])
      (common/options-response [:options :get]))
    (common/missing-response)))

(defn- stakeholder-update-location-response [update]
  (let [company-slug (:company-slug update)
        company-url (company-rep/url company-slug)]
    (common/location-response ["companies" company-slug "updates" (:slug update)]
      (su-rep/render-stakeholder-update company-url update true) su-rep/media-type)))

;; ----- Actions -----

(defn- get-stakeholder-update [conn company-slug slug]
  (when-let* [company (company-res/get-company conn company-slug)
              su (su-res/get-stakeholder-update conn company-slug slug)]
    {:company company :stakeholder-update su}))

(defn- list-stakeholder-updates [conn company-slug]
  (if-let* [company (company-res/get-company conn company-slug)
            su-list (su-res/list-stakeholder-updates conn company-slug [:slug :title :medium :author])
            su-filtered-list (su-res/distinct-updates su-list)]
    {:company company :stakeholder-updates (reverse su-filtered-list)}
    false))

(defn- stakeholder-update-for
  "Add the appropriate share details to the stakeholder-update for the share medium."
  [company share]
  (let [su (:stakeholder-update company)
        note (or (:note share) "")
        to (or (:to share) "")]
    (cond
      (:email share) (-> su
                        (assoc :medium :email)
                        (assoc :to to)
                        (assoc :note note))
      (:slack share) (-> su
                        (assoc :medium :slack)
                        (assoc :note note))
      :else (assoc su :medium :link))))

(defn- create-stakeholder-update [conn {:keys [company user] :as ctx}]
  (su-res/create-stakeholder-update! conn
    (su-res/->stakeholder-update conn
      company
      (stakeholder-update-for company (:data ctx))
      user)))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for a specific stakeholder update for a company.
(defresource stakeholder-update [conn company-slug slug]
  common/open-company-anonymous-resource ; verify validity of JWToken if it's provided, but it's not required

  :allowed-methods [:options :get :delete]
  :available-media-types [su-rep/media-type]
  :exists? (fn [_] (get-stakeholder-update conn company-slug slug))
  :known-content-type? (fn [ctx] (common/known-content-type? ctx su-rep/media-type))
  :malformed? false
  :processable? true

  :allowed? (by-method {
    :options (fn [ctx] (common/allow-anonymous ctx))
    :get (fn [ctx] (common/allow-anonymous ctx)) ; these are ObscURLs, so if you know what you're getting it's OK
    :delete (fn [ctx] (common/allow-org-members conn company-slug ctx))})

  ;; Delete a stakeholder update
  :delete! (fn [_] (su-res/delete-stakeholder-update conn company-slug slug))

  ;; Handlers
  :handle-ok
    (by-method {
      :get (fn [ctx] (su-rep/render-stakeholder-update
                        (company-rep/url company-slug)
                        (:stakeholder-update ctx)
                        (common/allow-org-members conn company-slug ctx)))})
  :handle-not-acceptable (fn [_] (common/only-accept 406 su-rep/media-type))
  :handle-unsupported-media-type (fn [_] (common/only-accept 415 su-rep/media-type))
  :handle-options (fn [ctx] (options-for-stakeholder-update conn company-slug slug ctx)))

;; A resource for a list of all the stakeholder updates for a company.
(defresource stakeholder-update-list [conn company-slug]
  common/open-company-anonymous-resource ; verify validity of JWToken if it's provided, but it's not required

  :available-media-types (by-method {:get [su-rep/collection-media-type]
                                     :post nil})
  :allowed-methods [:options :get :post]
  :malformed? (fn [ctx] (common/malformed-json? ctx true)) ; true to allow JSON or nothing
  :exists? (fn [_] (list-stakeholder-updates conn company-slug))
  :processable? true
  
  :allowed? (by-method {
    :options (fn [ctx] (common/allow-anonymous ctx))
    :get (fn [ctx] (common/allow-authenticated ctx)) ; protect the ObscURLs
    :post (fn [ctx] (common/allow-authenticated ctx))})

  ;; Create a new stakeholder update
  :post-to-missing? false ; 404 if company doesn't exist
  :post! (fn [ctx] (let [su   (create-stakeholder-update conn ctx)
                         ctx' (assoc (common/clone ctx) :stakeholder-update su)]
                     (cond 
                        (-> ctx :data :slack) ; Send to bot
                          (bot/send-trigger! (bot/ctx->trigger :stakeholder-update ctx'))
                        (-> ctx :data :email) ; Send to email service
                          (email/send-trigger! (email/ctx->trigger (:data ctx) ctx')))
                     {:stakeholder-update su}))

  ;; Handlers
  :handle-not-acceptable (common/only-accept 406 su-rep/collection-media-type)
  :handle-ok (fn [ctx] (su-rep/render-stakeholder-update-list
                          (company-rep/url company-slug)
                          (:stakeholder-updates ctx)
                          (common/allow-org-members conn company-slug ctx)))
  :handle-created (fn [ctx] (stakeholder-update-location-response (:stakeholder-update ctx)))
  :handle-options (fn [ctx] (if (common/authenticated? ctx)
                              (common/options-response [:options :get :post])
                              (common/options-response [:options :get]))))

;; ----- Routes -----

(defn stakeholder-update-routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (routes
      ;; List of stakeholder updates
      (OPTIONS "/companies/:company-slug/updates" [company-slug]
        (pool/with-pool [conn db-pool] (stakeholder-update-list conn company-slug)))
      (OPTIONS "/companies/:company-slug/updates/" [company-slug]
        (pool/with-pool [conn db-pool] (stakeholder-update-list conn company-slug)))
      (GET "/companies/:company-slug/updates" [company-slug]
        (pool/with-pool [conn db-pool] (stakeholder-update-list conn company-slug)))
      (GET "/companies/:company-slug/updates/" [company-slug]
        (pool/with-pool [conn db-pool] (stakeholder-update-list conn company-slug)))
      (POST "/companies/:company-slug/updates" [company-slug]
        (pool/with-pool [conn db-pool] (stakeholder-update-list conn company-slug)))
      (POST "/companies/:company-slug/updates/" [company-slug]
        (pool/with-pool [conn db-pool] (stakeholder-update-list conn company-slug)))
      ;; Specific stakeholder update
      (OPTIONS "/companies/:company-slug/updates/:slug" [company-slug slug]
        (pool/with-pool [conn db-pool] (stakeholder-update conn company-slug slug)))
      (OPTIONS "/companies/:company-slug/updates/:slug/" [company-slug slug]
        (pool/with-pool [conn db-pool] (stakeholder-update conn company-slug slug)))
      (GET "/companies/:company-slug/updates/:slug" [company-slug slug]
        (pool/with-pool [conn db-pool] (stakeholder-update conn company-slug slug)))
      (GET "/companies/:company-slug/updates/:slug/" [company-slug slug]
        (pool/with-pool [conn db-pool] (stakeholder-update conn company-slug slug)))
      (DELETE "/companies/:company-slug/updates/:slug" [company-slug slug]
        (pool/with-pool [conn db-pool] (stakeholder-update conn company-slug slug)))
      (DELETE "/companies/:company-slug/updates/:slug/" [company-slug slug]
        (pool/with-pool [conn db-pool] (stakeholder-update conn company-slug slug))))))