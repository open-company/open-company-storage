(ns oc.storage.api.pins
  "Liberator API for entry resources."
  (:require [if-let.core :refer (if-let*)]
            [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (ANY)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.api.access :as access]
            [oc.storage.api.entries :as entries-api]
            [oc.storage.api.activity :as activity-api]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.entry :as entry-rep]
            [oc.storage.resources.reaction :as reaction-res]
            [oc.storage.async.notification :as notification]
            [oc.storage.resources.entry :as entry-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.urls.pin :as pin-urls]))

;; Existentialism checks

(defn- get-entry-pin [entry pin-container-uuid]
  (or (get-in entry [:pins (keyword pin-container-uuid)]) true))

(defn- pin-exists? [conn ctx org-slug board-slug-or-uuid entry-uuid pin-container-uuid]
  (let [{:keys [existing-entry] :as next-ctx} (entries-api/published-entry-exists? conn ctx org-slug board-slug-or-uuid entry-uuid (:user ctx))
        entry-pin (get-entry-pin existing-entry pin-container-uuid)]
    (if-let* [existing-org (:existing-org next-ctx)
              boards-by-uuid (activity-api/user-boards-by-uuid conn (:user ctx) existing-org)
              _allowed-containers-set (set (conj (keys boards-by-uuid) pin-container-uuid))]
      (merge next-ctx {:existing-pin entry-pin})
      false)))

;; Actions

(defn- toggle-pin! [conn ctx entry-uuid pin-container-uuid]
  (timbre/debug "About to toggle pin for entry" entry-uuid "on container" pin-container-uuid "...")
  (let [org (:existing-org ctx)
        board (:existing-board ctx)
        entry (:existing-entry ctx)
        user (:user ctx)
        updated-entry (entry-res/toggle-pin! conn entry-uuid pin-container-uuid (:user ctx))]
    (timbre/info "Entry" entry-uuid (if (map? (get-in entry [:pins (keyword pin-container-uuid)])) "pinned" "unpinned") "to container" pin-container-uuid "by" (:user-id user))
    (notification/send-trigger! (notification/->trigger :pin-toggle org board {:pin-container-uuid pin-container-uuid :old entry :new updated-entry} user nil (api-common/get-change-client-id ctx)))
    {:updated-entry (api-common/rep updated-entry)}))

;; A resource for adding replies to a poll
(defresource pin [conn org-slug board-slug-or-uuid entry-uuid pin-container-uuid]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity of optional JWToken

  :allowed-methods [:options :post]

  ;; Media type client accepts
  :available-media-types (by-method {:post [mt/pin-media-type]})
  :handle-not-acceptable (by-method {:post (api-common/only-accept 406 mt/pin-media-type)})

  ;; Media type client sends
  :known-content-type? true

  ;; Authorization
  :allowed? (by-method {:options true
                        :post (fn [ctx] (access/allow-members conn org-slug (:user ctx)))})

  ;; Data handling
  :new? false
  :respond-with-entity? true

  ;; Validations
  :processable? true

  ;; Possibly no data to handle
  :malformed? false

  ;; Existentialism
  :can-post-to-mising? false
  :exists? (by-method {:options true
                       :post (fn [ctx] (pin-exists? conn ctx org-slug board-slug-or-uuid entry-uuid pin-container-uuid))})

  ;; Actions
  :post! (fn [ctx] (toggle-pin! conn ctx entry-uuid pin-container-uuid))

  ;; Responses
  :handle-ok (fn [ctx]
               (entry-rep/render-entry
                (:existing-org ctx)
                (:existing-board ctx)
                (:updated-entry ctx)
                (:existing-comments ctx)
                (reaction-res/aggregate-reactions (:existing-reactions ctx))
                (select-keys ctx [:access-level :role])
                (-> ctx :user :user-id)))
  :handle-exception (fn [ctx] (api-common/handle-exception ctx))
  :handle-unprocessable-entity (fn [ctx]
                                 (api-common/unprocessable-entity-response (:reason ctx))))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      (ANY (pin-urls/pin ":org-slug" ":board-slug-or-uuid" ":entry-uuid" ":pin-container-uuid")
        [org-slug board-slug-or-uuid entry-uuid pin-container-uuid]
        (pool/with-pool [conn db-pool]
          (pin conn org-slug board-slug-or-uuid entry-uuid pin-container-uuid)))
      (ANY (str (pin-urls/pin ":org-slug" ":board-slug-or-uuid" ":entry-uuid" ":pin-container-uuid") "/")
        [org-slug board-slug-or-uuid entry-uuid pin-container-uuid]
        (pool/with-pool [conn db-pool]
          (pin conn org-slug board-slug-or-uuid entry-uuid pin-container-uuid))))))
