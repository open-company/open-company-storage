(ns open-company.api.stakeholder-updates
  (:require [if-let.core :refer (if-let* when-let*)]
            [compojure.core :refer (routes OPTIONS GET POST DELETE)]
            [liberator.core :refer (defresource by-method)]
            [open-company.db.pool :as pool]
            [open-company.api.common :as common]
            [open-company.resources.common :as common-res]
            [open-company.resources.company :as company-res]
            [open-company.resources.stakeholder-update :as su-res]
            [open-company.representations.company :as company-rep]
            [open-company.representations.stakeholder-update :as su-rep]))

;; ----- Responses -----

(defn- options-for-stakeholder-update [conn company-slug slug ctx]
  (if-let* [company (company-res/get-company conn company-slug)
            _stakeholder-update (su-res/get-stakeholder-update conn company-slug slug)]
    (if (common/authorized-to-company? (assoc ctx :company company))
      (common/options-response [:options :get :delete])
      (common/options-response [:options :get]))
    (common/missing-response)))

;; ----- Actions -----

(defn- get-stakeholder-update [conn company-slug slug]
  (when-let* [company (company-res/get-company conn company-slug)
              su (su-res/get-stakeholder-update conn company-slug slug)]
    {:company company
     :stakeholder-update su}))

(defn- list-stakeholder-updates [conn company-slug]
  (su-res/list-stakeholder-updates conn company-slug [:slug :title :intro]))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource stakeholder-update [conn company-slug slug]
  common/open-company-anonymous-resource

  :allowed-methods [:options :get :delete]
  :available-media-types [su-rep/media-type]
  :exists? (fn [_] (get-stakeholder-update conn company-slug slug))
  :known-content-type? (fn [ctx] (common/known-content-type? ctx su-rep/media-type))

  :allowed? (by-method {
    :options (fn [ctx] (common/allow-anonymous ctx))
    :get (fn [ctx] (common/allow-anonymous ctx))
    :delete (fn [ctx] (common/allow-org-members conn company-slug ctx))})

  :processable? true

  ;; Handlers
  :handle-ok
    (by-method {
      :get (fn [ctx] (su-rep/render-stakeholder-update
                        (:company ctx)
                        (:stakeholder-update ctx)
                        (common/allow-org-members conn company-slug ctx)))})
  :handle-not-acceptable (fn [_] (common/only-accept 406 su-rep/media-type))
  :handle-unsupported-media-type (fn [_] (common/only-accept 415 su-rep/media-type))
  :handle-options (fn [ctx] (options-for-stakeholder-update conn company-slug slug ctx))

  ;; Delete a stakeholder update
  :delete! (fn [_] (su-res/delete-stakeholder-update conn company-slug slug)))

;; A resource for a list of all the stakeholder updates for a company.
(defresource stakeholder-update-list [conn company-slug]
  common/open-company-anonymous-resource ; verify validity of JWToken if it's provided, but it's not required

  :available-charsets [common/UTF8]
  :available-media-types (by-method {:get [su-rep/collection-media-type]
                                     :post nil})
  :allowed-methods [:options :get :post]
  :allowed? (by-method {
    :options (fn [ctx] (common/allow-anonymous ctx))
    :get (fn [ctx] (common/allow-anonymous ctx))
    :post (fn [ctx] (common/allow-authenticated ctx))})

  :handle-not-acceptable (common/only-accept 406 su-rep/collection-media-type)

  ;; Get a list of stakeholder updates
  :exists? (fn [_] {:stakeholder-updates (list-stakeholder-updates conn company-slug)})

  :processable? (by-method {
    :get true
    :options true
    :post false})

  :handle-ok (fn [ctx] (su-rep/render-stakeholder-update-list
                          (company-rep/url company-slug)
                          (:stakeholder-updates ctx)))
  :handle-options (fn [ctx] (if (common/authenticated? ctx)
                              (common/options-response [:options :get :post])
                              (common/options-response [:options :get]))))

  ;:handle-unprocessable-entity (fn [ctx] (unprocessable-reason (:reason ctx))))

;; ----- Routes -----

(defn stakeholder-update-routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (routes
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