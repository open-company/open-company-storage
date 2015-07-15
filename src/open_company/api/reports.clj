(ns open-company.api.reports
  (:require [compojure.core :refer (defroutes ANY)]
            [liberator.core :refer (defresource by-method)]
            [open-company.api.common :as common]
            [open-company.resources.report :as report]
            [open-company.representations.report :as render]))

;; ----- Responses -----

(defn- report-location-response [report]
  (common/location-response ["v1" "companies" (:symbol report) (:year report) (:period report)]
    (render/render-report report) report/media-type))

(defn- unprocessable-reason [reason]
  (case reason
    :bad-company common/missing-response
    (common/unprocessable-entity-response "Not processable.")))

;; ----- Actions -----

(defn- get-report [ticker year period]
  (if-let [report (report/get-report ticker year period)]
    {:report report}))

(defn- put-report [ticker year period report]
  (let [full-report (merge report {:symbol ticker :year year :period period})]
    (when (report/put-report full-report)
      {:report full-report})))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource report [ticker year period]
  common/open-company-resource

  :available-media-types [report/media-type]
  :exists? (fn [_] (get-report ticker year period))
  :known-content-type? (fn [ctx] (common/known-content-type? ctx report/media-type))

  :processable? (by-method {
    :get true
    :put (fn [ctx] (common/check-input (report/valid-report ticker year period (:data ctx))))})

  ;; Handlers
  :handle-ok (by-method {
    :get (fn [ctx] (render/render-report (:report ctx)))
    :put (fn [ctx] (render/render-report (:report ctx)))})
  :handle-not-acceptable (fn [_] (common/only-accept 406 report/media-type))
  :handle-unsupported-media-type (fn [_] (common/only-accept 415 report/media-type))
  :handle-unprocessable-entity (fn [ctx] (unprocessable-reason (:reason ctx)))

  ;; Delete a report
  :delete! (fn [_] (report/delete-report ticker year period))

  ;; Create or update a report
  :new? (by-method {:put (not (report/get-report ticker year period))})
  :put! (fn [ctx] (put-report ticker year period (:data ctx)))
  :handle-created (fn [ctx] (report-location-response (:report ctx))))

;; ----- Routes -----

(defroutes report-routes
  (ANY "/v1/companies/:ticker/:year/:period" [ticker year period] (report ticker year period)))