(ns open-company.api.companies
  (:require [defun :refer (defun)]
            [compojure.core :refer (defroutes ANY GET)]
            [liberator.core :refer (defresource by-method)]
            [open-company.api.common :as common]
            [open-company.resources.company :as company]
            [open-company.representations.company :as company-rep]
            [open-company.config :as c]
            [cheshire.core :as json]))

(defun add-ticker
  "Add the ticker symbol to the company properties if it's missing."
  ([_ company :guard :symbol] company)
  ([ticker company] (assoc company :symbol ticker)))

;; ----- Responses -----

(defn- company-location-response [company]
  (common/location-response ["v1" "companies" (:symbol company)]
    (company-rep/render-company company) company-rep/media-type))

(defn- unprocessable-reason [reason]
  (case reason
    :bad-company (common/missing-response)
    :invalid-name (common/unprocessable-entity-response "Company name is required.")
    :invalid-symbol (common/unprocessable-entity-response "Invalid ticker symbol.")
    (common/unprocessable-entity-response "Not processable.")))

;; ----- Actions -----

(defn- get-company [ticker]
  (if-let [company (company/get-company ticker)]
    {:company company}))

(defn- put-company [ticker company]
  (let [full-company (assoc company :symbol ticker)
        company-result (company/put-company ticker full-company)]
    {:updated-company company-result}))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource company [ticker]
  common/open-company-resource

  :available-media-types [company-rep/media-type]
  :exists? (fn [_] (get-company ticker))
  :known-content-type? (fn [ctx] (common/known-content-type? ctx company-rep/media-type))

  :processable? (by-method {
    :get true
    :put (fn [ctx] (common/check-input (company/valid-company ticker (add-ticker ticker (:data ctx)))))})

  ;; Handlers
  :handle-ok (by-method {
    :get (fn [ctx] (company-rep/render-company (:company ctx)))
    :put (fn [ctx] (company-rep/render-company (:updated-company ctx)))})
  :handle-not-acceptable (fn [_] (common/only-accept 406 company-rep/media-type))
  :handle-unsupported-media-type (fn [_] (common/only-accept 415 company-rep/media-type))
  :handle-unprocessable-entity (fn [ctx] (unprocessable-reason (:reason ctx)))

  ;; Delete a company
  :delete! (fn [_] (company/delete-company ticker))

  ;; Create or update a company
  :new? (by-method {:put (not (company/get-company ticker))})
  :put! (fn [ctx] (put-company ticker (add-ticker ticker (:data ctx))))
  :handle-created (fn [ctx] (company-location-response (:updated-company ctx))))

(defresource company-list []
  :available-charsets [common/UTF8]
  :available-media-types [company-rep/collection-media-type]
  :handle-not-acceptable (common/only-accept 406 company-rep/collection-media-type)
  :allowed-methods [:get]

  ;; Get a list of companies
  :exists? (fn [_] {:companies (company/list-companies)})
  :handle-ok (fn [ctx] (company-rep/render-company-list (:companies ctx))))

(defresource sentry []
  :available-charsets [common/UTF8]
  :available-media-types [company-rep/collection-media-type]
  :handle-not-acceptable (common/only-accept 406 company-rep/collection-media-type)
  :allowed-methods [:get]

  ;; Get a list of companies
  :exists? (fn [_] {:sentry c/dsn})
  :handle-ok (fn [ctx] (json/generate-string {:collection (array-map :sentry (:sentry ctx))} {:pretty true})))

;; ----- Routes -----

(defroutes company-routes
  (ANY "/v1/companies/:ticker" [ticker] (company ticker))
  (GET "/v1/companies/" [] (company-list))
  (GET "/v1/companies" [] (company-list))
  (GET "/v1/sentry" [] (sentry)))