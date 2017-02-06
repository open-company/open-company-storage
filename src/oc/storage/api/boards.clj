(ns oc.storage.api.boards
  "Liberator API for board resources."
  (:require [if-let.core :refer (if-let*)]
            [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (defroutes OPTIONS GET POST PUT PATCH DELETE)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.resources.org :as org-res]
            [oc.storage.representations.board :as board-rep]
            [oc.storage.resources.board :as board-res]))

;; ----- Utility Functions -----

;; ----- Actions -----

;; ----- Validations -----

(defn allow-team-members
  "Return true if the JWToken user is a member of the org's team."
  [conn {teams :teams} org-slug]
  (if-let [org (org-res/get-org conn org-slug)]
    ((set teams) (:team-id org))
    false))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for operations on a particular Board
(defresource board [conn org-slug slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get]

  ;; Media type client accepts
  :available-media-types [mt/board-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/board-media-type)
  
  ;; Authorization
  :allowed? (fn [ctx] (allow-team-members conn (:user ctx) org-slug)) ; TODO filter out private boards

  :exists? (fn [ctx] (if-let* [org (org-res/get-org conn org-slug)
                               board (board-res/get-board conn (:uuid org) slug)]
                        {:existing-org org :existing-board board}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (board-rep/render-board org-slug (:existing-board ctx))))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Board operations
      (OPTIONS "/orgs/:org-slug/boards/:slug" [org-slug slug] (pool/with-pool [conn db-pool] (board conn org-slug slug)))
      (GET "/orgs/:org-slug/boards/:slug" [org-slug slug] (pool/with-pool [conn db-pool] (board conn org-slug slug))))))