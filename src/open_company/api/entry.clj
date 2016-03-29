(ns open-company.api.entry
  (:require [compojure.core :as compojure :refer (defroutes GET OPTIONS)]
            [liberator.core :refer (defresource)]
            [open-company.db.pool :as pool]
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

(defn- render-entry [conn {:keys [user] :as _ctx}]
  (let [companies (when user (company-res/get-companies-by-index conn "org-id" (:org-id user)))]
    (json/generate-string
     {:links (links user companies)}
     {:pretty true})))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource entry-point [conn]
  common/anonymous-resource

  :allowed-methods [:options :get]
  :allowed? (fn [ctx] (common/allow-anonymous ctx))
  :available-media-types ["application/json"]

  :handle-not-acceptable (fn [_] (common/only-accept 406 "application/json"))
  :handle-unsupported-media-type (fn [_] (common/only-accept 415 "application/json"))

  :handle-ok (fn [ctx]
               (prn conn)
               (render-entry conn ctx))

  :handle-options (common/options-response [:options :get]))

;; ----- Routes -----

(defn entry-routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
     (OPTIONS "/" [] (pool/with-pool [conn db-pool] (entry-point conn)))
     (GET "/" [] (pool/with-pool [conn db-pool] (entry-point conn))))))