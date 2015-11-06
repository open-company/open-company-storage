(ns open-company.api.sections
  (:require [compojure.core :refer (routes ANY)]
            [liberator.core :refer (defresource by-method)]
            [open-company.api.common :as common]
            [open-company.resources.common :as common-res]
            [open-company.resources.section :as section-res]
            [open-company.representations.section :as section-rep]))


;; ----- Responses -----

(defn- section-location-response [section]
  (common/location-response ["companies" (:company-slug section) (:section-name section)]
    (section-rep/render-section section) section-rep/media-type))

(defn- unprocessable-reason [reason]
  (case reason
    :bad-company (common/missing-response)
    :bad-section-name (common/missing-response)
    (common/unprocessable-entity-response "Not processable.")))

;; ----- Actions -----

(defn- get-section [company-slug section-name as-of]
  (if-let [section (section-res/get-section company-slug section-name as-of)]
    {:section section}))

(defn- put-section [company-slug section-name section author]
  {:updated-section (section-res/put-section company-slug section-name section author)})

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource section [company-slug section-name as-of]
  common/open-company-resource

  :available-media-types [section-rep/media-type]
  :exists? (fn [_] (get-section company-slug section-name as-of))
  :known-content-type? (fn [ctx] (common/known-content-type? ctx section-rep/media-type))

  :allowed? (fn [ctx] (common/authorize company-slug (:jwtoken ctx)))

  ;; TODO: better handle company slug and section name from body not matching URL
  :processable? (by-method {
    :get true
    :put (fn [ctx] (common/check-input
                      (section-res/valid-section company-slug section-name
                        (-> (:data ctx)
                          (assoc :company-slug company-slug)
                          (assoc :section-name section-name)))))
    :patch (fn [ctx] (common/check-input
                        (section-res/valid-section company-slug section-name
                          (-> (merge (:section ctx) (:data ctx))
                            (assoc :company-slug company-slug)
                            (assoc :section-name section-name)))))})

  ;; Handlers
  :handle-ok (by-method {
    :get (fn [ctx] (section-rep/render-section (:section ctx)))
    :put (fn [ctx] (section-rep/render-section (:updated-section ctx)))
    :patch (fn [ctx] (section-rep/render-section (:updated-section ctx)))})
  :handle-not-acceptable (fn [_] (common/only-accept 406 section-rep/media-type))
  :handle-unsupported-media-type (fn [_] (common/only-accept 415 section-rep/media-type))
  :handle-unprocessable-entity (fn [ctx] (unprocessable-reason (:reason ctx)))

  ;; Create or update a section
  :new? (by-method {:put (not (seq (section-res/list-revisions company-slug section-name)))})
  :put! (fn [ctx] (put-section company-slug section-name (:data ctx) (:author ctx)))
  :patch! (fn [ctx] (put-section company-slug section-name (merge (:section ctx) (:data ctx)) (:author ctx)))
  :handle-created (fn [ctx] (section-location-response (:updated-section ctx))))

;; ----- Routes -----

(def section-routes
  (apply routes
    (map #(ANY (str "/companies/:company-slug/" %) [company-slug as-of]
      (section company-slug % as-of)) (map name common-res/sections))))