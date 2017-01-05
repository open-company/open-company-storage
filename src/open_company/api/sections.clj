(ns open-company.api.sections
  (:require [if-let.core :refer (if-let*)]
            [compojure.core :refer (routes ANY)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.rethinkdb.pool :as pool]
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

(defn- patch-revision [conn company-slug section-name as-of revision author]
  {:updated-section (section-res/patch-revision conn company-slug section-name as-of revision author)})

(defn- delete-revision [conn company-slug section-name as-of author]
  {:updated-section (section-res/delete-revision conn company-slug section-name as-of author)})

(defn- get-revision-list [conn company-slug section-name]
  (if-let [section (section-res/get-section conn company-slug section-name)]
    {:revisions (section-res/get-revisions conn company-slug section-name)}))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource section [conn company-slug section-name as-of]
  common/open-company-anonymous-resource  ; verify validity of JWToken if it's provided, but it's not required

  :allowed-methods [:options :get :put :patch :delete]
  :available-media-types [section-rep/media-type]
  :exists? (by-method {
                       :get (fn [_] (get-section conn company-slug section-name as-of))
                       :put (fn [_] (and (nil? as-of) (get-section conn company-slug section-name as-of)))
                       :patch (fn [_] (and (not (nil? as-of)) (get-section conn company-slug section-name as-of)))
                       :delete (fn [_] (and (not (nil? as-of)) (get-section conn company-slug section-name as-of)))})

  :known-content-type? (fn [ctx] (common/known-content-type? ctx section-rep/media-type))

  :allowed? (by-method {
                        :options (fn [ctx] (common/allow-anonymous ctx))
                        :get (fn [ctx] (or (common/allow-public conn company-slug ctx)
                                           (common/allow-org-members conn company-slug ctx)))
                        :put (fn [ctx] (common/allow-org-members conn company-slug ctx))
                        :patch (fn [ctx] (common/allow-org-members conn company-slug ctx))
                        :post false
                        :delete (fn [ctx] (common/allow-org-members conn company-slug ctx))})

  :can-put-to-missing? true
  
  ;; TODO: handle with prismatic schema check
  :processable? (by-method {
                            :options true
                            :get true
                            :put true
                            :patch true
                            :delete true})

  ;; Handlers
  :handle-ok
    (by-method {
                :get (fn [ctx] (section-rep/render-section conn (:section ctx) (common/allow-org-members conn company-slug ctx) (not (nil? as-of))))
                :put (fn [ctx] (section-rep/render-section conn (:updated-section ctx)))
                :patch (fn [ctx] (section-rep/render-section conn (:updated-section ctx)))
                :delete (fn [ctx] (section-rep/render-section conn (:updated-section ctx)))})
  :handle-not-acceptable (fn [_] (common/only-accept 406 section-rep/media-type))
  :handle-unsupported-media-type (fn [_] (common/only-accept 415 section-rep/media-type))
  :handle-unprocessable-entity (fn [ctx] (unprocessable-reason (:reason ctx)))
  :handle-options (fn [ctx] (options-for-section conn company-slug section-name ctx))

  ;; Create or update a section
  :new? (by-method {:put (fn [ctx] (not (:section ctx)))})
  :put! (fn [ctx] (put-section conn company-slug section-name (:data ctx) (:user ctx)))
  :patch! (fn [ctx] (patch-revision conn company-slug section-name as-of (merge (:section ctx) (:data ctx)) (:user ctx)))
  :delete! (fn [ctx] (delete-revision conn company-slug section-name as-of (:user ctx)))
  :handle-created (fn [ctx] (section-location-response conn (:updated-section ctx))))

;; A resource for a list of all the revisions of the section the user has access to.
(defresource section-revision-list [conn company-slug section-name]
  common/open-company-anonymous-resource ; verify validity of JWToken if it's provided, but it's not required

  :available-charsets [common/UTF8]
  :available-media-types [section-rep/collection-media-type]
  :allowed-methods [:options :get]
  :allowed? (by-method {
                        :options (fn [ctx] (common/allow-anonymous ctx))
                        :get (fn [ctx] (or (common/allow-public conn company-slug ctx)
                                           (common/allow-org-members conn company-slug ctx)))})

  :handle-not-acceptable (common/only-accept 406 section-rep/collection-media-type)

  ;; Get a list of section revisions
  :exists? (fn [_] (get-revision-list conn company-slug section-name))

  :processable? true

  :handle-ok (fn [ctx] (section-rep/render-revision-list
                          company-slug
                          section-name
                          (:revisions ctx)
                          (common/allow-org-members conn company-slug ctx)))
  :handle-options (fn [ctx] (common/options-response [:options :get])))

;; ----- Routes -----

(defn- section-route [sys company-slug section-slug uuid as-of]
  (let [db-pool (-> sys :db-pool :pool)
        section-name (if (= section-slug "custom-:uuid") (str "custom-" uuid) section-slug)] 
    (pool/with-pool [conn db-pool] (section conn company-slug section-name as-of))))

(defn- revisions-route [sys company-slug section-slug uuid]
  (let [db-pool (-> sys :db-pool :pool)
        section-name (if (= section-slug "custom-:uuid") (str "custom-" uuid) section-slug)] 
    (pool/with-pool [conn db-pool] (section-revision-list conn company-slug section-name))))

(defn section-routes [sys]
    (apply routes (concat

      ;; Section routes
                   (map #(ANY (str "/companies/:company-slug/" %)
                              [company-slug uuid as-of]
                              (section-route sys company-slug % uuid as-of))
                     (conj (map name common-res/section-names) "custom-:uuid"))

      ;; Section revision list routes
                   (map #(ANY (str "/companies/:company-slug/" % "/revisions")
                              [company-slug uuid]
                              (revisions-route sys company-slug % uuid))
                       (conj (map name common-res/section-names) "custom-:uuid")))))


