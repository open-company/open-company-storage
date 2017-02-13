(ns oc.storage.api.boards
  "Liberator API for board resources."
  (:require [if-let.core :refer (if-let*)]
            [compojure.core :as compojure :refer (defroutes OPTIONS GET POST PUT PATCH DELETE)]
            [liberator.core :refer (defresource by-method)]
            [cheshire.core :as json]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.api.common :as storage-common]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.board :as board-rep]
            [oc.storage.representations.topic :as topic-rep]
            [oc.storage.representations.entry :as entry-rep]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.entry :as entry-res]))

;; ----- Utility Functions -----

(defn topic-list-for [org-slug slug]
  (let [topics config/topics
        templates (:templates topics)
        topic-slugs (keys templates)
        with-links (map #(topic-rep/topic-template-for-rendering org-slug slug (% templates)) topic-slugs)]
    (json/generate-string (assoc topics :templates (zipmap topic-slugs with-links)) {:pretty true})))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for operations on a particular Board
(defresource board [conn org-slug slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get]

  ;; Media type client accepts
  :available-media-types [mt/board-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/board-media-type)
  
  ;; Authorization
  :allowed? (fn [ctx] (storage-common/access-level-for conn org-slug slug (:user ctx)))

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [org (org-res/get-org conn org-slug)
                               board (board-res/get-board conn (:uuid org) slug)
                               topic-slugs (map name (:topics board)) ; slug for each active topic
                               entries (entry-res/get-entries-by-board conn (:uuid board)) ; latest entry for each topic
                               selected-entries (select-keys entries topic-slugs) ; active entries
                               selected-entry-reps (zipmap topic-slugs
                                                    (map #(entry-rep/render-entry-for-collection org-slug slug (get selected-entries %) (:access-level ctx))
                                                      topic-slugs))
                               archived (clojure.set/difference (set (keys entries)) (set topic-slugs))] ; archived entries
                        {:existing-org org :existing-board (merge (assoc board :archived archived) selected-entry-reps)}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (board-rep/render-board org-slug (:existing-board ctx) (:access-level ctx))))

;; A resource for the available topics for a specific company.
(defresource topic-list [conn org-slug slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get]

  ;; Media type client accepts
  :available-media-types [mt/topic-list-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/topic-list-media-type)

  ;; Authorization
  :allowed? (fn [ctx] (storage-common/allow-authors conn org-slug slug (:user ctx)))

  ;; Existentialism
  :exists? (fn [ctx] (if-let [org (org-res/get-org conn org-slug)]
                        (board-res/get-board conn (:uuid org) slug)
                        false))

  ;; Responses
  :handle-ok (fn [_] (topic-list-for org-slug slug)))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Board operations
      (OPTIONS "/orgs/:org-slug/boards/:slug" [org-slug slug] (pool/with-pool [conn db-pool] (board conn org-slug slug)))
      (GET "/orgs/:org-slug/boards/:slug" [org-slug slug] (pool/with-pool [conn db-pool] (board conn org-slug slug)))
      ;; Topic list operations
      (OPTIONS "/orgs/:org-slug/boards/:slug/topics/new" [org-slug slug] (pool/with-pool [conn db-pool] (topic-list conn org-slug slug)))
      (GET "/orgs/:org-slug/boards/:slug/topics/new" [org-slug slug] (pool/with-pool [conn db-pool] (topic-list conn org-slug slug))))))