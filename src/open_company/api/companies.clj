(ns open-company.api.companies
  (:require [compojure.core :refer (defroutes ANY GET POST)]
            [liberator.core :refer (defresource by-method)]
            [open-company.api.common :as common]
            [open-company.resources.company :as company]
            [open-company.representations.company :refer (render-company)]))

;; ----- Get companies -----

(defn- get-company [ticker]
  (if-let [company (company/get-company ticker)]
    {:company company}))

;; ----- Resources -----
;; see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(def company-resource-config {
  :available-charsets [common/UTF8]
  :handle-not-found (fn [_] common/missing-response)
  ;:handle-unprocessable-entity (fn [ctx] (unprocessable-reason (:reason ctx)))
})

(defresource company [ticker]
  company-resource-config
  :available-media-types [company/company-media-type]
  :handle-not-acceptable (fn [_] (common/only-accept 406 company/company-media-type))
  :allowed-methods [:get :put :delete]
  :exists? (fn [_] (get-company ticker))
  :known-content-type? (fn [ctx] (common/known-content-type? ctx company/company-media-type))
  :handle-unsupported-media-type (fn [_] (common/only-accept 415 company/company-media-type))
  :respond-with-entity? (by-method {:put true :delete false})

  :processable? (by-method {
    :get true})

  :handle-ok (by-method {
    :get (fn [ctx] (render-company (:company ctx)))}))

;; ----- Routes -----

(defroutes company-routes
  (ANY "/v1/companies/:ticker" [ticker] (company ticker)))