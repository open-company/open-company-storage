(ns open-company.api.entry
  (:require [compojure.core :refer (defroutes GET OPTIONS)]
            [liberator.core :refer (defresource)]
            [open-company.api.common :as common]
            [open-company.resources.company :as company-res]
            [open-company.representations.common :as common-rep]
            [open-company.representations.company :as company-rep]
            [cheshire.core :as json]))

;; ----- Representations -----

(defn- company-link [{:keys [name] :as c}]
  (common-rep/link-map "company" "GET" (company-rep/url c) company-rep/media-type :name name))

(defn- links [user companies]
  (cond-> [(common-rep/link-map "company-list" "GET" "/companies/" company-rep/collection-media-type)]
    user      (conj (common-rep/link-map "company-create" "POST" "/companies/" company-rep/media-type))
    companies (into (mapv company-link companies))))

(defn- render-entry [{:keys [user] :as _ctx}]
  (let [companies (when user (company-res/get-companies-by-index "org-id" (:org-id user)))]
    (json/generate-string
      {:links (links user companies)}
      {:pretty true})))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource entry-point []
  common/anonymous-resource

  :allowed-methods [:options :get]
  :allowed? (fn [ctx] (common/allow-anonymous ctx))
  :available-media-types ["application/json"]

  :handle-not-acceptable (fn [_] (common/only-accept 406 "application/json"))
  :handle-unsupported-media-type (fn [_] (common/only-accept 415 "application/json"))

  :handle-ok render-entry

  :handle-options (common/options-response [:options :get]))

;; ----- Routes -----

(defroutes entry-routes
  ;; (routes ...)
  (OPTIONS "/" [] (entry-point))
  (GET "/" [] (entry-point)))

(defn entry-routes [{:keys [db-pool]}]
  (routes
   (OPTIONS "/" [] (entry-point))
   (GET "/" [] (entry-point))))