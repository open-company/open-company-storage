(ns oc.storage.api.orgs
  "Liberator API for team resources."
  (:require [compojure.core :as compojure :refer (defroutes OPTIONS GET POST PUT PATCH DELETE)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.api.common :as storage-common]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.resources.org :as org-res]
            [oc.storage.representations.board :as board-rep]
            [oc.storage.resources.board :as board-res]))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for operations on a particular Org
(defresource org [conn slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get]

  ;; Media type client accepts
  :available-media-types [mt/org-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/org-media-type)
  
  ;; Authorization
  :allowed? (by-method {
    :options true
    :get (fn [ctx] (storage-common/access-level-for conn slug (:user ctx)))})

  :exists? (fn [ctx] (if-let [org (org-res/get-org conn slug)]
                        {:existing-org org}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (let [org (:existing-org ctx)
                             org-id (:uuid org)
                             boards (board-res/get-boards-by-org conn org-id [:created-at :updated-at]) ; TODO Filter out private boards
                             board-reps (map #(board-rep/render-board-for-collection slug %) boards)]
                          (org-rep/render-org (assoc org :boards board-reps)))))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Team operations
      (OPTIONS "/orgs/:slug" [slug] (pool/with-pool [conn db-pool] (org conn slug)))
      (GET "/orgs/:slug" [slug] (pool/with-pool [conn db-pool] (org conn slug))))))