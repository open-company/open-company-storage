(ns open-company.api.companies
  (:require [defun :refer (defun)]
            [compojure.core :refer (defroutes ANY GET)]
            [liberator.core :refer (defresource by-method)]
            [open-company.api.common :as common]
            [open-company.resources.common :as common-res]
            [open-company.resources.company :as company]
            [open-company.resources.section :as section]
            [open-company.representations.company :as company-rep]
            [cheshire.core :as json]))

;; Round-trip it through Cheshire to ensure the embedded HTML gets encodedod or the client has issues parsing it
(defonce sections (json/generate-string
                    (json/decode
                      (slurp (clojure.java.io/resource "open_company/assets/sections.json"))) {:pretty true}))

(defun add-slug
  "Add the slug to the company properties if it's missing."
  ([_ company :guard :slug] company)
  ([slug company] (assoc company :slug slug)))

;; ----- Responses -----

(defn- company-location-response [company]
  (common/location-response ["companies" (:symbol company)]
    (company-rep/render-company company) company-rep/media-type))

(defn- unprocessable-reason [reason]
  (case reason
    :bad-company (common/missing-response)
    :invalid-name (common/unprocessable-entity-response "Company name is required.")
    :invalid-slug (common/unprocessable-entity-response "Invalid slug.")
    (common/unprocessable-entity-response "Not processable.")))

;; ----- Actions -----

(defn- get-company [slug]
  (if-let [company (company/get-company slug)]
    {:company company}))

(defn- put-company [slug company author]
  (let [full-company (assoc company :slug slug)]
    {:updated-company (company/put-company slug full-company author)}))

(defn- patch-company [slug company-updates author]
  (let [original-company (company/get-company slug)
        section-names (clojure.set/intersection (set (keys company-updates)) common-res/sections)
        updated-sections (->> section-names
          (map #(section/put-section slug % (company-updates %) author)) ; put each section that's in the patch
          (map #(dissoc % :id :section-name))) ; not needed for sections in company
        patch-updates (merge company-updates (zipmap section-names updated-sections))] ; updated sections & anythig else
    ;; update the company
    {:updated-company (company/put-company slug (merge original-company patch-updates) author)}))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for a specific company.
(defresource company
  [slug]
  common/open-company-resource

  :available-media-types [company-rep/media-type]
  :exists? (fn [_] (get-company slug))
  :known-content-type? (fn [ctx] (common/known-content-type? ctx company-rep/media-type))

  :allowed? (fn [ctx] (common/authorize slug (:jwtoken ctx)))

  :processable? (by-method {
    :get true
    :put (fn [ctx] (common/check-input (company/valid-company slug (add-slug slug (:data ctx)))))
    :patch (fn [ctx] true)}) ;; TODO validate for subset of company properties

  ;; Handlers
  :handle-ok (by-method {
    :get (fn [ctx] (company-rep/render-company (:company ctx)))
    :put (fn [ctx] (company-rep/render-company (:updated-company ctx)))
    :patch (fn [ctx] (company-rep/render-company (:updated-company ctx)))})
  :handle-not-acceptable (fn [_] (common/only-accept 406 company-rep/media-type))
  :handle-unsupported-media-type (fn [_] (common/only-accept 415 company-rep/media-type))
  :handle-unprocessable-entity (fn [ctx] (unprocessable-reason (:reason ctx)))

  ;; Delete a company
  :delete! (fn [_] (company/delete-company slug))

  ;; Create or update a company
  :new? (by-method {:put (not (company/get-company slug))})
  :put! (fn [ctx] (put-company slug (add-slug slug (:data ctx)) (:author ctx)))
  :patch! (fn [ctx] (patch-company slug (add-slug slug (:data ctx)) (:author ctx)))
  :handle-created (fn [ctx] (company-location-response (:updated-company ctx))))

;; A resource for a list of all the companies the user has access to.
(defresource company-list
  []
  common/authenticated-resource

  :available-charsets [common/UTF8]
  :available-media-types [company-rep/collection-media-type]
  :handle-not-acceptable (common/only-accept 406 company-rep/collection-media-type)
  
  :allowed-methods [:get]

  ;; Get a list of companies
  :exists? (fn [_] {:companies (company/list-companies)})
  :handle-ok (fn [ctx] (company-rep/render-company-list (:companies ctx))))

;; A resource for the available sections for a specific company.
(defresource section-list
  [slug]
  common/authenticated-resource

  :available-charsets [common/UTF8]
  :available-media-types [company-rep/section-list-media-type]
  :handle-not-acceptable (common/only-accept 406 company-rep/section-list-media-type)

  :allowed? (fn [ctx] (common/authorize slug (:jwtoken ctx)))

  :allowed-methods [:get]
  :exists? (fn [_] (get-company slug))

  ;; Get a list of sections
  :handle-ok (fn [_] sections))

;; ----- Routes -----

(defroutes company-routes
  (GET "/companies/:slug/section/new" [slug] (section-list slug))
  (GET "/companies/:slug/section/new/" [slug] (section-list slug))
  (ANY "/companies/:slug" [slug] (company slug))
  (ANY "/companies/:slug/" [slug] (company slug))
  (GET "/companies/" [] (company-list))
  (GET "/companies" [] (company-list)))