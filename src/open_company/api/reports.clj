(ns open-company.api.reports
  (:require [compojure.core :refer (defroutes ANY GET POST)]
            [liberator.core :refer (defresource by-method)]
            [open-company.api.common :as common]
            [open-company.resources.report :as report]
            [open-company.representations.report :as render]))

;; ----- Responses -----

(defn- report-location-response [report]
  (common/location-response ["v1" "companies" (:symbol report) (:year report) (:period report)]
    (render/render-report) report/media-type))

(defn- unprocessable-reason [reason]
  (case reason
    :bad-company common/missing-response
    (common/unprocessable-entity-response "Not processable.")))

;; ----- Get reports -----

(defn- get-report [ticker year period]
  (if-let [report (report/get-report ticker year period)]
    {:report report}))

;; ----- Resources -----
;; see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource report [ticker year period]
  :available-charsets [common/UTF8]
  :handle-not-found (fn [_] common/missing-response)
  :handle-unprocessable-entity (fn [ctx] (unprocessable-reason (:reason ctx)))
  :available-media-types [report/media-type]
  :handle-not-acceptable (fn [_] (common/only-accept 406 report/media-type))
  :allowed-methods [:get :put :delete]
  :exists? (fn [_] (get-report ticker year period))
  :known-content-type? (fn [ctx] (common/known-content-type? ctx report/media-type))
  :handle-unsupported-media-type (fn [_] (common/only-accept 415 report/media-type))
  :respond-with-entity? (by-method {:put true :delete false})

  :processable? (by-method {
    :get true
    :put (fn [ctx] (common/check-input (report/valid-report ticker year period (:data ctx))))})

  :handle-ok (by-method {
    :get (fn [ctx] (render/render-report (:report ctx)))
    :put (fn [ctx] (render/render-report (:report ctx)))})

  ;; Delete a report
  :delete! (fn [_] (report/delete-report ticker year period))

  ;; Create or update a report
  :new? (by-method {:put (not (report/get-report ticker year period))})
  :malformed? (by-method {
    :get false
    :delete false
    :put (fn [ctx] (common/malformed-json? ctx))})
  :can-put-to-missing? (fn [_] true)
  :conflict? (fn [_] false)
  :put! (fn [ctx] (report/put-report ticker year period (:data ctx)))
  :handle-created (fn [ctx] (report-location-response (:report ctx))))

;; ----- Routes -----

(defroutes report-routes
  (ANY "/v1/companies/:ticker/:year/:period" [ticker year period] (report ticker year period)))