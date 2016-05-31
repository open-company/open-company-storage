(ns open-company.api.sections
  (:require [if-let.core :refer (if-let*)]
            [compojure.core :refer (routes ANY)]
            [liberator.core :refer (defresource by-method)]
            [open-company.db.pool :as pool]
            [open-company.api.common :as common]
            [open-company.resources.common :as common-res]
            [open-company.resources.company :as company-res]
            [open-company.resources.section :as section-res]
            [open-company.representations.section :as section-rep]))


;; ----- Responses -----

(defn- section-location-response [conn section]
  (common/location-response ["companies" (:company-slug section) (:section-name section)]
    (section-rep/render-section conn section) section-rep/media-type))

(defn- unprocessable-reason [reason]
  (case reason
    :bad-company (common/missing-response)
    :bad-section-name (common/missing-response)
    (common/unprocessable-entity-response "Not processable.")))

(defn- options-for-section [conn company-slug section-name ctx]
  (if-let* [company (company-res/get-company conn company-slug)
            _section (section-res/get-section conn company-slug section-name)]
    (if (common/authorized-to-company? (assoc ctx :company company))
      (common/options-response [:options :get :put :patch])
      (common/options-response [:options :get]))
    (common/missing-response)))

;; ----- Actions -----

(defn- get-section [conn company-slug section-name as-of]
  (let [section     (section-res/get-section conn company-slug section-name as-of)
        placeholder (get (company-res/get-company conn company-slug) (keyword section-name))]
    (when-let [s (or section placeholder)]
      {:section s})))

(defn- put-section [conn company-slug section-name section author]
  {:updated-section (section-res/put-section conn company-slug section-name section author)})

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource section [conn company-slug section-name as-of]
  common/open-company-anonymous-resource

  :allowed-methods [:options :get :put :patch]
  :available-media-types [section-rep/media-type]
  :exists? (fn [_] (get-section conn company-slug section-name as-of))
  :known-content-type? (fn [ctx] (common/known-content-type? ctx section-rep/media-type))

  :allowed? (by-method {
    :options (fn [ctx] (common/allow-anonymous ctx))
    :get (fn [ctx] (or (common/allow-public conn company-slug ctx)
                       (common/allow-org-members conn company-slug ctx)))
    :put (fn [ctx] (common/allow-org-members conn company-slug ctx))
    :patch (fn [ctx] (common/allow-org-members conn company-slug ctx))
    :post false
    :delete false})

  ;; TODO: handle with prismatic schema check
  :processable? (by-method {
    :options true
    :get true
    :put true
    :patch true})
    ; (fn [ctx] (common/check-input
    ;   (section-res/valid-section company-slug section-name
    ;     (-> (:data ctx)
    ;       (assoc :company-slug company-slug)
    ;       (assoc :section-name section-name)))))

  ;; Handlers
  :handle-ok
    (by-method {
      :get (fn [ctx] (section-rep/render-section conn (:section ctx) (common/allow-org-members conn company-slug ctx) (not (nil? as-of))))
      :put (fn [ctx] (section-rep/render-section conn (:updated-section ctx)))
      :patch (fn [ctx] (section-rep/render-section conn (:updated-section ctx)))})
  :handle-not-acceptable (fn [_] (common/only-accept 406 section-rep/media-type))
  :handle-unsupported-media-type (fn [_] (common/only-accept 415 section-rep/media-type))
  :handle-unprocessable-entity (fn [ctx] (unprocessable-reason (:reason ctx)))
  :handle-options (fn [ctx] (options-for-section conn company-slug section-name ctx))

  ;; Create or update a section
  :new? (by-method {:put (fn [ctx] (not (:section ctx)))})
  :put! (fn [ctx] (put-section conn company-slug section-name (:data ctx) (:user ctx)))
  :patch! (fn [ctx] (put-section conn company-slug section-name (merge (:section ctx) (:data ctx)) (:user ctx)))
  :handle-created (fn [ctx] (section-location-response conn (:updated-section ctx))))

;; ----- Routes -----

(defn section-routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (apply routes
      (map #(ANY (str "/companies/:company-slug/" %)
                  [company-slug as-of]
                  (pool/with-pool [conn db-pool] (section conn company-slug % as-of)))
          (map name common-res/section-names)))))