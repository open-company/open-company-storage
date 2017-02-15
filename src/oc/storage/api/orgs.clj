(ns oc.storage.api.orgs
  "Liberator API for team resources."
  (:require [if-let.core :refer (if-let*)]
            [compojure.core :as compojure :refer (defroutes OPTIONS GET POST PUT PATCH DELETE)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.api.common :as storage-common]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.representations.board :as board-rep]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]))

;; ----- Actions -----

(defn create-org [conn ctx]
  (if-let* [new-org (:new-org ctx)
            org-result (org-res/create-org! conn new-org)] ; Add the org

    ;; Org creation succeeded, so create the default boards
    (let [org-uuid (:uuid org-result)
          author (:user ctx)]
      {:new-org (assoc org-result :boards
                  (map
                    #(board-res/create-board! conn
                      (board-res/->board org-uuid {:name %} author))
                    board-res/default-boards))})
    
    ;; org creation failed
    false))

;; ----- Validations -----

(defn- valid-new-org? [conn ctx]
  (try
    ;; Create the new org from the data provided
    (let [org-map (:data ctx)
          author (:user ctx)]
      {:new-org (org-res/->org org-map author)})

    (catch clojure.lang.ExceptionInfo e
      [false, {:reason (.getMessage e)}]))) ; Not a valid new org

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


;; A resource for operations on a list of orgs
(defresource org-list [conn]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :post]

  ;; Media type client accepts
  :available-media-types [mt/org-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/org-media-type)
  
  ;; Media type client sends
  :known-content-type? (by-method {
                          :options true
                          :post (fn [ctx] (api-common/known-content-type? ctx mt/org-media-type))})

  ;; Authorization
  :allowed? (by-method {
    :options true
    :post (fn [ctx] (storage-common/allow-team-admins-or-no-org
                      conn (:user ctx)))}) ; don't allow non-team-admins to get stuck w/ no org

  ;; Validations
  :processable? (by-method {
    :options true
    :post (fn [ctx] (valid-new-org? conn ctx))})

  ;; Actions
  :post! (fn [ctx] (create-org conn ctx))

  ;; Responses
  :handle-created (fn [ctx] (let [new-org (:new-org ctx)
                                  slug (:slug new-org)]
                              (api-common/location-response
                                (org-rep/url slug)
                                (org-rep/render-org new-org)
                                mt/org-media-type)))
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (:reason ctx))))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Org operations
      (OPTIONS "/orgs/:slug" [slug] (pool/with-pool [conn db-pool] (org conn slug)))
      (GET "/orgs/:slug" [slug] (pool/with-pool [conn db-pool] (org conn slug)))
      ;; Org creation
      (OPTIONS "/orgs/" [] (pool/with-pool [conn db-pool] (org-list conn)))
      (POST "/orgs/" [] (pool/with-pool [conn db-pool] (org-list conn))))))