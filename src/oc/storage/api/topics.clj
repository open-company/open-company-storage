(ns oc.storage.api.topics
  "Liberator API for topic resources."
  (:require [if-let.core :refer (if-let*)]
            [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (defroutes OPTIONS GET DELETE)]
            [liberator.core :refer (defresource by-method)]
            [cheshire.core :as json]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.lib.slugify :as slugify]
            [oc.storage.config :as config]
            [oc.storage.api.access :as access]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.topic :as topic-rep]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]))

;; ----- Utility Functions -----

(defn topic-list-for [org-slug board-slug]
  (let [topics config/topics
        templates (:templates topics)
        topic-slugs (keys templates)
        with-links (map #(topic-rep/topic-template-for-rendering org-slug board-slug (% templates)) topic-slugs)]
    (json/generate-string (assoc topics :templates (zipmap topic-slugs with-links)) {:pretty config/pretty?})))

;; ----- Actions -----

(defn- archive-topic [conn {board-uuid :uuid topics :topics} slug]
  (timbre/info "Archiving topic:" slug "for board:" board-uuid)
  (if-let [board-result (board-res/update-board! conn board-uuid {:topics (filter #(not= % slug) topics)})]
    (do 
      (timbre/info "Archived topic:" slug "for board:" board-uuid)
      {:update-board board-result})
    
    (do
      (timbre/error "Failed to archive topic: " slug " for board:" board-uuid)
      false)))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for operations on a particular board
(defresource topic [conn org-slug board-slug slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :delete]

  ;; Authorization
  :allowed? (by-method {
    :options true
    :delete (fn [ctx] (access/allow-authors conn org-slug board-slug (:user ctx)))})

  ;; Media type client accepts
  :media-type-available? true ; client browser sends */* as Accept media type

  ;; Media type client sends
  :known-content-type? true ; bug in cljs-http where it always passes a content-type for a delete

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slugs? (and (slugify/valid-slug? org-slug) (slugify/valid-slug? board-slug))
                               org (org-res/get-org conn org-slug)
                               board (board-res/get-board conn (:uuid org) board-slug)
                               topic ((set (:topics board)) slug)]
                        {:existing-org org :existing-board board}
                        false))
  :delete! (fn [ctx] (archive-topic conn (:existing-board ctx) slug)))

;; A resource for the available topics for a specific board.
(defresource topic-list [conn org-slug board-slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get]

  ;; Media type client accepts
  :available-media-types [mt/topic-list-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/topic-list-media-type)

  ;; Authorization
  :allowed? (by-method {
    :options true
    :get (fn [ctx] (access/allow-authors conn org-slug board-slug (:user ctx)))})

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slugs? (and (slugify/valid-slug? org-slug) (slugify/valid-slug? board-slug))
                               org (org-res/get-org conn org-slug)]
                        (board-res/get-board conn (:uuid org) board-slug)
                        false))

  ;; Responses
  :handle-ok (fn [_] (topic-list-for org-slug board-slug)))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    ;; Requests are also routed here from entries-api's routes (see: dispatch fn)
    (compojure/routes
      ;; Topic list operations
      (OPTIONS "/orgs/:org-slug/boards/:board-slug/topics/new" [org-slug board-slug]
        (pool/with-pool [conn db-pool](topic-list conn org-slug board-slug)))
      (GET "/orgs/:org-slug/boards/:board-slug/topics/new" [org-slug board-slug]
        (pool/with-pool [conn db-pool] (topic-list conn org-slug board-slug))))))