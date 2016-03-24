(ns open-company.api.companies
  (:require [compojure.core :as compojure :refer (defroutes ANY OPTIONS GET POST)]
            [liberator.core :refer (defresource by-method)]
            [schema.core :as schema]
            [open-company.config :as config]
            [open-company.db.pool :as pool]
            [open-company.lib.slugify :as slug]
            [open-company.api.common :as common]
            [open-company.resources.common :as common-res]
            [open-company.resources.company :as company]
            [open-company.resources.section :as section]
            [open-company.representations.company :as company-rep]
            [cheshire.core :as json]))

;; Round-trip it through Cheshire to ensure the embedded HTML gets encodedod or the client has issues parsing it
(defonce sections (json/generate-string config/sections {:pretty true}))

(defn add-slug
  "Add the slug to the company properties if it's missing."
  [slug company]
  (update company :slug (fnil identity slug)))

(defn find-slug [conn company-props]
  (or (:slug company-props) (slug/find-available-slug (:name company-props) (company/taken-slugs conn))))

;; ----- Responses -----

(defn- company-location-response [conn company]
  (common/location-response ["companies" (:symbol company)]
    (company-rep/render-company conn company) company-rep/media-type))

(defn- unprocessable-reason [reason]
  (case reason
    :invalid-slug-format (common/unprocessable-entity-response "Invalid slug format.")
    :slug-taken (common/unprocessable-entity-response "Slug already taken.")
    :name (common/unprocessable-entity-response "Company name is required.")
    :slug (common/unprocessable-entity-response "Invalid or missing slug.")
    (common/unprocessable-entity-response (str "Not processable: " (pr-str reason)))))

(defn- options-for-company [conn slug ctx]
  (if-let [company (company/get-company conn slug)]
    (if (common/authorized-to-company? (assoc ctx :company company))
      (common/options-response [:options :get :patch :delete])
      (common/options-response [:options :get]))
    (common/missing-response)))

;; ----- Actions -----

(defn- get-company [conn slug ctx]
  (if-let [company (or (:company ctx) (company/get-company conn slug))]
    {:company company}))

(defn- patch-company [conn slug company-updates user]
  (let [original-company (company/get-company conn slug)
        section-names (clojure.set/intersection (set (keys company-updates)) common-res/section-names)
        ; store any new or updated sections that were provided in the company as sections
        updated-sections (->> section-names
          (map #(section/put-section conn slug % (company-updates %) user)) ; put each section that's included in the patch
          (map #(dissoc % :id :section-name))) ; not needed for sections in company
        ; merge the original company with the updated sections & anything other properties they provided 
        with-section-updates (merge original-company (merge company-updates (zipmap section-names updated-sections)))
        ; get any sections that we used to have, that have been added back in (sections back from the dead)
        with-prior-sections (company/add-prior-sections conn with-section-updates)
        ; add in the placeholder sections for any brand new added sections
        with-placeholders (company/add-placeholder-sections with-prior-sections)]
    ;; update the company
    {:updated-company (company/put-company conn slug with-placeholders user)}))

;; ----- Validations -----

(defn processable-patch-req? [conn slug ctx]
  (if-let [existing-company (company/get-company conn slug)] ; can only PATCH a company that already exists
    (let [updated-company (merge existing-company (:data ctx)) ; apply the PATCH to the existing company
          invalid? (schema/check common-res/Company updated-company)] ; check that it's still valid
      (cond
        invalid? [false {:reason invalid?}] ; invalid
        :else [true {:company existing-company}])) ; it's valid, keep the existing company around for efficiency
    [true {}])) ; it will fail later on :exists?

(defn processable-post-req? [conn {:keys [user data]}]
  (let [company (company/->company data user (find-slug conn data))
        invalid? (schema/check common-res/Company company)
        slug-taken? (not (company/slug-available? conn (:slug company)))]
    (cond
      invalid? [false {:reason invalid?}]
      slug-taken? [false {:reason :slug-taken}]
      :else [true {}])))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for a specific company.
(defresource company
  [db-pool slug]
  common/open-company-anonymous-resource ; verify validity of JWToken if it's provided, but it's not required

  :available-media-types [company-rep/media-type]
  :exists? (fn [ctx] (pool/with-pool [conn db-pool] (get-company conn slug ctx)))
  :known-content-type? (fn [ctx] (common/known-content-type? ctx company-rep/media-type))

  :allowed-methods [:options :get :patch :delete]
  :allowed? (by-method {
    :options (fn [ctx] (common/allow-anonymous ctx))
    :get (fn [ctx] (common/allow-anonymous ctx))
    :patch (fn [ctx] (pool/with-pool [conn db-pool] (common/allow-org-members conn slug ctx)))
    :delete (fn [ctx] (pool/with-pool [conn db-pool] (common/allow-org-members conn slug ctx)))})

  :processable? (by-method {
    :options true
    :get true
    :patch (fn [ctx] (pool/with-pool [conn db-pool] (processable-patch-req? conn slug ctx)))})

  ;; Handlers
  :handle-ok (by-method {
    :get (fn [ctx] (pool/with-pool [conn db-pool] (company-rep/render-company conn (:company ctx) (common/authorized-to-company? ctx))))
    :patch (fn [ctx] (pool/with-pool [conn db-pool] (company-rep/render-company conn (:updated-company ctx))))})
  :handle-not-acceptable (fn [_] (common/only-accept 406 company-rep/media-type))
  :handle-unsupported-media-type (fn [_] (common/only-accept 415 company-rep/media-type))
  :handle-unprocessable-entity (fn [ctx] (unprocessable-reason (:reason ctx)))
  :handle-options (fn [ctx] (pool/with-pool [conn db-pool] (options-for-company conn slug ctx)))

  ;; Delete a company
  :delete! (fn [_] (pool/with-pool [conn db-pool] (company/delete-company conn slug)))

  ;; Update a company
  :patch! (fn [ctx] (pool/with-pool [conn db-pool] (patch-company conn slug (add-slug slug (:data ctx)) (:user ctx)))))

;; A resource for a list of all the companies the user has access to.
(defresource company-list [db-pool]
  common/open-company-anonymous-resource ; verify validity of JWToken if it's provided, but it's not required

  :available-charsets [common/UTF8]
  :available-media-types (by-method {:get [company-rep/collection-media-type]
                                     :post [company-rep/media-type]})
  :allowed-methods [:options :get :post]
  :allowed? (by-method {
    :options (fn [ctx] (common/allow-anonymous ctx))
    :get (fn [ctx] (common/allow-anonymous ctx))
    :post (fn [ctx] (common/allow-authenticated ctx))})

  :handle-not-acceptable (common/only-accept 406 company-rep/collection-media-type)

  ;; Get a list of companies
  :exists? (fn [_] {:companies (pool/with-pool [conn db-pool] (company/list-companies conn))})

  :processable? (by-method {
    :get true
    :options true
    :post (fn [ctx] (pool/with-pool [conn db-pool] (processable-post-req? conn ctx)))})

  :post! (fn [ctx] {:company (pool/with-pool [conn db-pool]
                               (->> (company/->company (:data ctx) (:user ctx) (find-slug conn (:data ctx)))
                                    (company/add-placeholder-sections)
                                    (company/create-company! conn)))})

  :handle-ok (fn [ctx] (company-rep/render-company-list (:companies ctx)))
  :handle-created (fn [ctx] (pool/with-pool [conn db-pool] (company-location-response conn (:company ctx))))
  :handle-options (fn [ctx] (if (common/authenticated? ctx)
                              (common/options-response [:options :get :post])
                              (common/options-response [:options :get])))

  :handle-unprocessable-entity (fn [ctx] (unprocessable-reason (:reason ctx))))

;; A resource for the available sections for a specific company.
(defresource section-list
  [db-pool slug]
  common/authenticated-resource ; verify validity and presence of required JWToken

  :available-charsets [common/UTF8]
  :available-media-types [company-rep/section-list-media-type]
  :allowed-methods [:options :get]
  :allowed? (fn [ctx] (pool/with-pool [conn db-pool] (common/allow-org-members conn slug ctx)))

  :handle-not-acceptable (common/only-accept 406 company-rep/section-list-media-type)
  :handle-options (pool/with-pool [conn db-pool]
                    (if (company/get-company conn slug)
                      (common/options-response [:options :get])
                      (common/missing-response)))

  ;; Get a list of sections
  :exists? (fn [ctx] (pool/with-pool [conn db-pool] (get-company conn slug ctx)))
  :handle-ok (fn [_] sections))

;; ----- Routes -----

(defn company-routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
     (OPTIONS "/companies/:slug/section/new" [slug] (section-list db-pool slug))
     (OPTIONS "/companies/:slug/section/new/" [slug] (section-list db-pool slug))
     (GET "/companies/:slug/section/new" [slug] (section-list db-pool slug))
     (GET "/companies/:slug/section/new/" [slug] (section-list db-pool slug))
     (ANY "/companies/:slug" [slug] (company db-pool slug))
     (ANY "/companies/:slug/" [slug] (company db-pool slug))
     (OPTIONS "/companies/" [] (company-list db-pool))
     (OPTIONS "/companies" [] (company-list db-pool))
     (GET "/companies/" [] (company-list db-pool))
     (GET "/companies" [] (company-list db-pool))
     (POST "/companies/" [] (company-list db-pool))
     (POST "/companies" [] (company-list db-pool)))))