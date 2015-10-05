(ns open-company.api.sections
  (:require [compojure.core :refer (routes ANY)]
            [liberator.core :refer (defresource by-method)]
            [open-company.api.common :as common]
            [open-company.resources.common :as common-res]
            [open-company.resources.section :as section-res]
            [open-company.representations.section :as section-rep]))


;; ----- Responses -----

(defn- unprocessable-reason [reason]
  (case reason
    :bad-company (common/missing-response)
    :bad-section-name (common/missing-response)
    (common/unprocessable-entity-response "Not processable.")))

;; ----- Actions -----

;; TODO more efficient get latest of section by slug and name
(defn- get-section [company-slug section-name]
  (if-let [section (first (section-res/list-sections company-slug section-name))]
    {:section section}))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource section [company-slug section-name]
  common/open-company-resource

  :available-media-types [section-rep/media-type]
  :exists? (fn [_] (get-section company-slug section-name))
  :known-content-type? (fn [ctx] (common/known-content-type? ctx section-rep/media-type))

  ;; TODO: pass company slug and section name separately and validate they match
  :processable? (by-method {
    :get true
    :put (fn [ctx] (common/check-input (section-res/valid-section (-> (:data ctx)
                                                                  (assoc :company-slug company-slug)
                                                                  (assoc :section-name section-name)))))})

  ;; Handlers
  :handle-ok (by-method {
    :get (fn [ctx] (section-rep/render-section (:section ctx)))
    :put (fn [ctx] (section-rep/render-section (:updated-section ctx)))})
  :handle-not-acceptable (fn [_] (common/only-accept 406 section-rep/media-type))
  :handle-unsupported-media-type (fn [_] (common/only-accept 415 section-rep/media-type))
  :handle-unprocessable-entity (fn [ctx] (unprocessable-reason (:reason ctx)))

  )

;; ----- Routes -----

(def section-routes
  (apply routes 
    (map #(ANY (str "/companies/:company-slug/" %) [company-slug] (section company-slug %)) common-res/sections)))