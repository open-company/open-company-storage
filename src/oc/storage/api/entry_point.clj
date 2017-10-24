(ns oc.storage.api.entry-point
  "Liberator API for HATEOAS entry point to storage service."
  (:require [if-let.core :refer (when-let*)]
            [compojure.core :as compojure :refer (defroutes GET OPTIONS)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.lib.slugify :as slugify]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]))

;; ----- Utility Functions -----

(defn org-if-public-boards
  "Return an org if and only if it has any public boards."
  [conn org-slug]
  (when-let* [org (org-res/get-org conn org-slug)
              org-uuid (:uuid org)
              boards (board-res/list-boards-by-index conn "org-uuid-access" [[org-uuid "public"]])]
    (if (empty? boards) false org)))

;; ----- Responses -----

(defn- render-entry-point [conn {:keys [user request] :as _ctx}]

  ;; TODO: promoted orgs w/ public boards
  (let [authed-orgs (if user
                ;; Auth'd user
                (org-res/list-orgs-by-teams conn (:teams user) [:team-id :logo-url :logo-width :logo-height :created-at :updated-at])
                ;; Not auth'd
                [])
        org-slugs (set (map :slug authed-orgs)) ; set of the org slugs for this user
        requested-org-slug (get-in request [:params "requested"]) ; a public org may be requested specifically
        check-for-public? (and (slugify/valid-slug? requested-org-slug) (not (org-slugs requested-org-slug))) ; requested and not in the list
        public-org (when check-for-public? (org-if-public-boards conn requested-org-slug)) ; requested org if public
        orgs (if public-org (conj authed-orgs public-org) authed-orgs)] ; final set of orgs
    (org-rep/render-org-list orgs user)))
    
;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource entry-point [conn]
  (api-common/anonymous-resource config/passphrase) ; verify validity of optional JWToken

  :allowed-methods [:options :get]
  
  ;; Media type client accepts
  :allowed? (fn [ctx] (api-common/allow-anonymous ctx))

  :available-media-types ["application/json" mt/org-collection-media-type]
  :handle-not-acceptable (fn [_] (api-common/only-accept 406 ["application/json" mt/org-collection-media-type]))

  ;; Responses
  :handle-ok (fn [ctx] (api-common/json-response (render-entry-point conn ctx) 200 mt/org-collection-media-type)))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
     (OPTIONS "/" [] (pool/with-pool [conn db-pool] (entry-point conn)))
     (GET "/" [] (pool/with-pool [conn db-pool] (entry-point conn))))))