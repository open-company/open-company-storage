(ns open-company.api.stakeholder-updates
  (:require [compojure.core :refer (routes OPTIONS GET POST)]
            [liberator.core :refer (defresource by-method)]
            [open-company.db.pool :as pool]
            [open-company.api.common :as common]
            [open-company.resources.common :as common-res]
            [open-company.resources.stakeholder-update :as su-res]
            [open-company.representations.company :as company-rep]
            [open-company.representations.stakeholder-update :as su-rep]))

;; ----- Actions -----

(defn- list-stakeholder-updates [conn company-slug]
  (su-res/list-stakeholder-updates conn company-slug [:slug :title :intro]))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

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

  :handle-ok (fn [ctx] (su-rep/render-stakeholder-update-list company-slug (:stakeholder-updates ctx)))
  :handle-options (fn [ctx] (if (common/authenticated? ctx)
                              (common/options-response [:options :get :post])
                              (common/options-response [:options :get]))))

  ;:handle-unprocessable-entity (fn [ctx] (unprocessable-reason (:reason ctx))))

;; ----- Routes -----

(defn stakeholder-update-routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (routes
     (OPTIONS "/companies/:slug/updates" [slug] (pool/with-pool [conn db-pool] (stakeholder-update-list conn slug)))
     (OPTIONS "/companies/:slug/updates/" [slug] (pool/with-pool [conn db-pool] (stakeholder-update-list conn slug)))
     (GET "/companies/:slug/updates" [slug] (pool/with-pool [conn db-pool] (stakeholder-update-list conn slug)))
     (GET "/companies/:slug/updates/" [slug] (pool/with-pool [conn db-pool] (stakeholder-update-list conn slug)))
     (POST "/companies/:slug/updates/" [] (pool/with-pool [conn db-pool] (stakeholder-update-list conn)))
     (POST "/companies/:slug/updates" [] (pool/with-pool [conn db-pool] (stakeholder-update-list conn))))))