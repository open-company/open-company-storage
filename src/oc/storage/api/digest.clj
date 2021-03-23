(ns oc.storage.api.digest
  "Liberator API for digest resource."
  (:require [if-let.core :refer (if-let*)]
            [compojure.core :as compojure :refer (OPTIONS GET)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.slugify :as slugify]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.api.activity :as activity-api]
            [oc.storage.api.access :as access]
            [oc.storage.resources.activity :as activity-res]
            [oc.storage.resources.common :as common]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.digest :as digest-rep]
            [oc.storage.resources.org :as org-res]
            [oc.storage.urls.org :as org-urls]
            [oc.lib.change.resources.read :as read]))

;; ----- Helpers -----

(defn assemble-digest
  "Assemble the requested (by the params) entries for the provided org to populate the digest response."
  [conn {start :start direction :direction limit :limit} org boards-by-uuid user-id]
  (let [follow-data (activity-api/follow-parameters-map user-id (:uuid org))

        follow-following-data (assoc follow-data :following true)
        following-data (activity-res/paginated-entries-for-digest conn (:uuid org) :desc start direction limit (vals boards-by-uuid) follow-following-data {})
        following-count (activity-res/paginated-entries-for-digest conn (:uuid org) :desc start :after 0 (vals boards-by-uuid) follow-following-data {:count true})

        follow-unfollowing-data (assoc follow-data :unfollowing true)
        has-unfollows? (seq (:unfollow-board-uuids follow-data)) ;; Do not query DB if there are no unfollowed boards
        unfollowing-data (if has-unfollows?
                           (activity-res/paginated-entries-for-digest conn (:uuid org) :desc start direction limit (vals boards-by-uuid) follow-unfollowing-data {})
                           [])
        unfollowing-count (if has-unfollows?
                            (activity-res/paginated-entries-for-digest conn (:uuid org) :desc start :after 0 (vals boards-by-uuid) follow-unfollowing-data {:count true})
                            0)

        user-reads (read/retrieve-by-user-org config/dynamodb-opts user-id (:uuid org))
        user-reads-map (zipmap (map :item-uuid user-reads) user-reads)]
    ;; Give each activity its board name
    (-> {:start start
         :direction direction
         :total-following-count following-count
         :total-unfollowing-count unfollowing-count}
        (assoc :following (map (fn [entry]
                                 (let [board (boards-by-uuid (:board-uuid entry))]
                                   (merge entry {:board-slug (:slug board)
                                                 :board-access (:access board)
                                                 :board-name (:name board)
                                                 :last-read-at (get-in user-reads-map [(:uuid entry) :read-at])})))
                               following-data))
        (assoc :unfollowing (map (fn [entry]
                                   (let [board (boards-by-uuid (:board-uuid entry))]
                                     (merge entry {:board-slug (:slug board)
                                                   :board-access (:access board)
                                                   :board-name (:name board)
                                                  :last-read-at (get-in user-reads-map [(:uuid entry) :read-at])})))
                                 unfollowing-data)))))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource to retrieve the digest data of a particular Org
(defresource digest [conn slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get]

  ;; Media type client accepts
  :available-media-types [mt/entry-collection-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/entry-collection-media-type)

  ;; Authorization
  :allowed? (by-method {
    :options true
    :get (fn [ctx] (access/allow-members conn slug (:user ctx)))})

  ;; Check the request
  :malformed? (fn [ctx]
                (let [ctx-params (-> ctx :request :params)
                      start (:start ctx-params)
                      ;; Start is always set for digest
                      valid-start? (and (seq start) (common/sort-value? start))
                      direction (keyword (:direction ctx-params))
                      ;; direction is always set to after for digest
                      valid-direction? (= direction :after)]
                  (not (and valid-start? valid-direction?))))

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slug? (slugify/valid-slug? slug)
                               user (:user ctx)
                               org (or (:existing-org ctx) (org-res/get-org conn slug))
                               boards-by-uuid (activity-api/user-boards-by-uuid conn user org)]
                        {:existing-org (api-common/rep org)
                         :boards-by-uuid (api-common/rep boards-by-uuid)}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (let [user (:user ctx)
                             user-id (:user-id user)
                             org (:existing-org ctx)
                             boards-by-uuid (:boards-by-uuid ctx)
                             ctx-params (-> ctx :request :params)
                             params (-> ctx-params
                                     (dissoc :slug)
                                     (assoc :limit 10) ;; use a limit of 10 posts since digest can't be too long anyway
                                     (update :direction keyword)) ; always set to after)
                             results (assemble-digest conn params org boards-by-uuid user-id)]
                          (digest-rep/render-digest params org "digest" results boards-by-uuid user))))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Digest endpoint
      (OPTIONS (org-urls/digest ":slug") [slug] (pool/with-pool [conn db-pool] (digest conn slug)))
      (OPTIONS (str (org-urls/digest ":slug") "/") [slug] (pool/with-pool [conn db-pool] (digest conn slug)))
      (GET (org-urls/digest ":slug") [slug] (pool/with-pool [conn db-pool] (digest conn slug)))
      (GET (str (org-urls/digest ":slug") "/") [slug] (pool/with-pool [conn db-pool] (digest conn slug))))))