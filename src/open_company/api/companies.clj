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
    :get true
    :put (fn [ctx] (common/check-input (company/valid-company ticker (:data ctx))))})

  :handle-ok (by-method {
    :get (fn [ctx] (render-company (:company ctx)))
    :put (fn [ctx] (render-company (:company ctx)))})

  ;; Delete a company
  :delete! (fn [_] (company/delete-company ticker))

  ;; Create or update a company
  :new? (by-method {:put (not (company/get-company ticker))})
  :malformed? (by-method {
    :get false
    :delete false
    :put (fn [ctx] (common/malformed-json? ctx))})
  :can-put-to-missing? (fn [_] true)
  :conflict? (fn [_] false)
  :put! (fn [ctx] (company/put-company ticker (:data ctx))))


;; ----- Routes -----

(defroutes company-routes
  (ANY "/v1/companies/:ticker" [ticker] (company ticker)))