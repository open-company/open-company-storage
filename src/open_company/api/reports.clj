(ns open-company.api.reports
  (:require [compojure.core :refer (defroutes ANY)]
            [liberator.core :refer (defresource by-method)]
            [open-company.api.common :as common]
            [open-company.resources.report :as report]
            [open-company.representations.report :as report-rep]))

;; ----- Responses -----

(defn- report-location-response [report]
  (common/location-response ["v1" "companies" (:symbol report) (:year report) (:period report)]
    (report-rep/render-report report) report-rep/media-type))

(defn- unprocessable-reason [reason]
  (case reason
    :bad-company (common/missing-response "Company not found.")
    :bad-year (common/missing-response "Invalid report year.")
    :bad-period (common/missing-response "Invalid report period.")
    (common/unprocessable-entity-response "Not processable.")))

;; ----- Actions -----

(defn- get-report [ticker year period]
  (if-let [report (report/get-report ticker year period)]
    {:report report}))

(defn- put-report [ticker year period report]
  (let [full-report (merge report {:symbol ticker :year (Integer. year) :period period})
        report-result (report/put-report full-report)]
      {:updated-report report-result}))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource report [ticker year period]
  common/open-company-resource

  :available-media-types [report-rep/media-type]
  :exists? (fn [_] (get-report ticker year period))
  :known-content-type? (fn [ctx] (common/known-content-type? ctx report-rep/media-type))

  :processable? (by-method {
    :get true
    :put (fn [ctx] (common/check-input (report/valid-report ticker year period (:data ctx))))})

  ;; Handlers
  :handle-ok (by-method {
    :get (fn [ctx] (report-rep/render-report (:report ctx)))
    :put (fn [ctx] (report-rep/render-report (:updated-report ctx)))})
  :handle-not-acceptable (fn [_] (common/only-accept 406 report-rep/media-type))
  :handle-unsupported-media-type (fn [_] (common/only-accept 415 report-rep/media-type))
  :handle-unprocessable-entity (fn [ctx] (unprocessable-reason (:reason ctx)))

  ;; Delete a report
  :delete! (fn [_] (report/delete-report ticker year period))

  ;; Create or update a report
  :new? (by-method {:put (not (report/get-report ticker year period))})
  :put! (fn [ctx] (put-report ticker year period (:data ctx)))
  :handle-created (fn [ctx] (report-location-response (:updated-report ctx))))

;; ----- Routes -----

(defroutes report-routes
  (ANY "/v1/companies/:ticker/:year/:period" [ticker year period] (report ticker year period)))