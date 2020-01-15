(ns oc.storage.api.activity
  "Liberator API for org resources."
  (:require [clojure.walk :refer (keywordize-keys)]
            [if-let.core :refer (if-let*)]
            [compojure.core :as compojure :refer (OPTIONS GET)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.slugify :as slugify]
            [oc.lib.db.pool :as pool]
            [oc.lib.db.common :as db-common]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.api.access :as access]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.activity :as activity-rep]
            [oc.storage.representations.entry :as entry-rep]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.entry :as entry-res]
            [oc.storage.lib.timestamp :as ts]
            [oc.lib.change.resources.read :as read]))

(defn- assemble-activity
  "Assemble the requested (by the params) activity for the provided org."
  [conn {start :start direction :direction must-see :must-see digest-request :digest-request}
   org sort-type board-by-uuids allowed-boards user-id]
  (let [user-reads (read/retrieve-by-user config/dynamodb-opts user-id)
        order (if (= direction :before) :desc :asc)
        limit (if digest-request 0 config/default-activity-limit)
        entries (entry-res/paginated-entries-by-org conn (:uuid org) order start direction limit sort-type allowed-boards
                 {:must-see must-see})
        activities {:next-count (count entries)
                    :direction direction
                    :user-reads user-reads}]
    ;; Give each activity its board name
    (assoc activities :activity (map (fn [activity] (let [board (board-by-uuids (:board-uuid activity))
                                                          activity-read (some #(when (= (:item-id %) (:uuid activity)) %) user-reads)]
                                                      (merge activity {
                                                       :board-slug (:slug board)
                                                       :board-access (:access board)
                                                       :board-name (:name board)
                                                       :new-comments-count (entry-rep/new-comments-count activity user-id activity-read)})))
                                    entries))))

(defn- assemble-follow-ups
  "Assemble the requested (by the params) follow-up entries for the provided user."
  [conn {start :start direction :direction must-see :must-see} org sort-type board-by-uuids
   allowed-boards user-id]
  (let [user-reads (read/retrieve-by-user config/dynamodb-opts user-id)
        order (if (= direction :before) :desc :asc)
        entries (entry-res/list-all-entries-by-follow-ups conn (:uuid org) user-id order start direction
                 config/default-activity-limit sort-type allowed-boards {:must-see must-see})
        activities {:next-count (count entries)
                    :direction direction
                    :user-reads user-reads}]
    ;; Give each activity its board name
    (assoc activities :activity (map (fn [activity] (let [board (board-by-uuids (:board-uuid activity))
                                                          activity-read (some #(when (= (:item-id %) (:uuid activity)) %) user-reads)]
                                                      (merge activity {
                                                       :board-slug (:slug board)
                                                       :board-access (:access board)
                                                       :board-name (:name board)
                                                       :new-comments-count (entry-rep/new-comments-count activity user-id activity-read)})))
                                  entries))))

(defn- assemble-inbox
  "Assemble the requested activity (params) for the provided org."
  [conn {start :start direction :direction must-see :must-see} org board-by-uuids allowed-boards user-id]
  (let [user-reads (read/retrieve-by-user config/dynamodb-opts user-id)
        order (if (= :after direction) :asc :desc)
        total-inbox-count (entry-res/list-all-entries-for-inbox conn (:uuid org) user-id :asc (db-common/current-timestamp)
                           :before allowed-boards {:count true})
        entries (entry-res/list-all-entries-for-inbox conn (:uuid org) user-id order start direction allowed-boards)
        activities {:direction :next
                    :next-count (count entries)
                    :total-count total-inbox-count
                    :user-reads user-reads}]
    ;; Give each activity its board name
    (assoc activities :activity (map (fn [activity] (let [board (board-by-uuids (:board-uuid activity))
                                                          activity-read (some #(when (= (:item-id %) (:uuid activity)) %) user-reads)]
                                                      (merge activity {
                                                       :board-slug (:slug board)
                                                       :board-access (:access board)
                                                       :board-name (:name board)
                                                       :new-comments-count (entry-rep/new-comments-count activity user-id activity-read)})))
                                 entries))))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for operations on the activity of a particular Org
(defresource activity [conn slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get]

  ;; Media type client accepts
  :available-media-types [mt/activity-collection-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/activity-collection-media-type)

  ;; Authorization
  :allowed? (by-method {
    :options true
    :get (fn [ctx] (access/allow-members conn slug (:user ctx)))})

  ;; Check the request
  :malformed? (fn [ctx] (let [ctx-params (keywordize-keys (-> ctx :request :params))
                              start (:start ctx-params)

                              valid-start? (if start (ts/valid-timestamp? start) true)
                              direction (keyword (:direction ctx-params))
                              ;; no direction is OK, but if specified it's from the allowed enumeration of options
                              valid-direction? (if direction (#{:before :after} direction) true)
                              ;; a specified start/direction must be together or ommitted
                              pairing-allowed? (or (and start direction)
                                                    (and (not start) (not direction)))]
                           (not (and valid-start? valid-direction? pairing-allowed?))))

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slug? (slugify/valid-slug? slug)
                               org (or (:existing-org ctx) (org-res/get-org conn slug))]
                        {:existing-org (api-common/rep org)}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (let [user (:user ctx)
                             user-id (:user-id user)
                             org (:existing-org ctx)
                             org-id (:uuid org)
                             ctx-params (keywordize-keys (-> ctx :request :params))
                             sort (:sort ctx-params)
                             sort-type (if (= sort "activity") :recent-activity :recently-posted)
                             start-params (update ctx-params :start #(or % (db-common/current-timestamp))) ; default is now
                             direction (or (#{:after} (keyword (:direction ctx-params))) :before) ; default is before
                             params (merge start-params {:direction direction})
                             boards (board-res/list-boards-by-org conn org-id [:created-at :updated-at :authors :viewers :access])
                             allowed-boards (map :uuid (filter #(access/access-level-for org % user) boards))
                             board-uuids (map :uuid boards)
                             board-slugs-and-names (map #(array-map :slug (:slug %) :access (:access %) :name (:name %)) boards)
                             board-by-uuids (zipmap board-uuids board-slugs-and-names)
                             fixed-params (if (= (:auth-source user) "digest")
                                            (assoc params :digest-request true)
                                            params)
                             activity (assemble-activity conn fixed-params org sort-type board-by-uuids allowed-boards user-id)]
                          (activity-rep/render-activity-list params org "entries" sort-type activity boards user))))

;; A resource for operations on the activity of a particular Org
(defresource follow-ups [conn slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get]

  ;; Media type client accepts
  :available-media-types [mt/activity-collection-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/activity-collection-media-type)

  ;; Authorization
  :allowed? (by-method {
    :options true
    :get (fn [ctx] (access/allow-members conn slug (:user ctx)))})

  ;; Check the request
  :malformed? (fn [ctx] (let [ctx-params (keywordize-keys (-> ctx :request :params))
                              start (:start ctx-params)

                              valid-start? (if start (ts/valid-timestamp? start) true)
                              direction (keyword (:direction ctx-params))
                              ;; no direction is OK, but if specified it's from the allowed enumeration of options
                              valid-direction? (if direction (#{:before :after} direction) true)
                              ;; a specified start/direction must be together or ommitted
                              pairing-allowed? (or (and start direction)
                                                    (and (not start) (not direction)))]
                           (not (and valid-start? valid-direction? pairing-allowed?))))

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slug? (slugify/valid-slug? slug)
                               org (or (:existing-org ctx) (org-res/get-org conn slug))]
                        {:existing-org (api-common/rep org)}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (let [user (:user ctx)
                             user-id (:user-id user)
                             org (:existing-org ctx)
                             org-id (:uuid org)
                             ctx-params (-> ctx :request :params keywordize-keys)
                             sort (:sort ctx-params)
                             sort-type (if (= sort "activity") :recent-activity :recently-posted)
                             start-params (update ctx-params :start #(or % (db-common/current-timestamp))) ; default is now
                             direction (or (-> ctx-params :direction keyword #{:after}) :before) ; default is before
                             params (merge start-params {:direction direction})
                             boards (board-res/list-boards-by-org conn org-id [:created-at :updated-at :authors :viewers :access])
                             allowed-boards (map :uuid (filter #(access/access-level-for org % user) boards))
                             board-uuids (map :uuid boards)
                             board-slugs-and-names (map #(array-map :slug (:slug %) :access (:access %) :name (:name %)) boards)
                             board-by-uuids (zipmap board-uuids board-slugs-and-names)
                             activity (assemble-follow-ups conn params org sort-type board-by-uuids allowed-boards user-id)]
                          (activity-rep/render-activity-list params org "follow-ups" sort-type activity boards user))))

;; A resource to retrieve Inbox posts
(defresource inbox [conn slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get]

  ;; Media type client accepts
  :available-media-types [mt/activity-collection-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/activity-collection-media-type)

  ;; Authorization
  :allowed? (by-method {
    :options true
    :get (fn [ctx] (access/allow-members conn slug (:user ctx)))})

  ;; Check the request
  :malformed? (fn [ctx] (let [ctx-params (keywordize-keys (-> ctx :request :params))
                              start (:start ctx-params)
                              valid-start? (if start (ts/valid-timestamp? start) true)
                              direction (keyword (:direction ctx-params))
                              ;; no direction is OK, but if specified it's from the allowed enumeration of options
                              valid-direction? (if direction (#{:before :after :around} direction) true)
                              ;; a specified start/direction must be together or ommitted
                              pairing-allowed? (or (and start direction)
                                                   (and (not start) (not direction)))]
                          (not (and valid-start? valid-direction? pairing-allowed?))))

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slug? (slugify/valid-slug? slug)
                               org (or (:existing-org ctx) (org-res/get-org conn slug))]
                        {:existing-org (api-common/rep org)}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (let [user (:user ctx)
                             user-id (:user-id user)
                             org (:existing-org ctx)
                             org-id (:uuid org)
                             ctx-params (keywordize-keys (-> ctx :request :params))
                             start? (if (:start ctx-params) true false) ; flag if a start was specified
                             start-params (update ctx-params :start #(or % (db-common/current-timestamp))) ; default is now
                             direction (or (#{:after :around} (keyword (:direction ctx-params))) :before) ; default is before
                             params (merge start-params {:direction direction :start? start?})
                             boards (board-res/list-boards-by-org conn org-id [:created-at :updated-at :authors :viewers :access])
                             board-uuids (map :uuid boards)
                             allowed-boards (map :uuid (filter #(access/access-level-for org % user) boards))
                             board-slugs-and-names (map #(array-map :slug (:slug %) :access (:access %) :name (:name %)) boards)
                             board-by-uuids (zipmap board-uuids board-slugs-and-names)
                             activity (assemble-inbox conn params org board-by-uuids allowed-boards user-id)]
                          (activity-rep/render-activity-list params org "inbox" nil activity boards user))))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; All activity operations
      (OPTIONS "/orgs/:slug/entries" [slug] (pool/with-pool [conn db-pool] (activity conn slug)))
      (OPTIONS "/orgs/:slug/entries/" [slug] (pool/with-pool [conn db-pool] (activity conn slug)))
      (GET "/orgs/:slug/entries" [slug] (pool/with-pool [conn db-pool] (activity conn slug)))
      (GET "/orgs/:slug/entries/" [slug] (pool/with-pool [conn db-pool] (activity conn slug)))

      (OPTIONS "/orgs/:slug/follow-ups" [slug] (pool/with-pool [conn db-pool] (follow-ups conn slug)))
      (OPTIONS "/orgs/:slug/follow-ups/" [slug] (pool/with-pool [conn db-pool] (follow-ups conn slug)))
      (GET "/orgs/:slug/follow-ups" [slug] (pool/with-pool [conn db-pool] (follow-ups conn slug)))
      (GET "/orgs/:slug/follow-ups/" [slug] (pool/with-pool [conn db-pool] (follow-ups conn slug)))

      (OPTIONS "/orgs/:slug/inbox" [slug] (pool/with-pool [conn db-pool] (inbox conn slug)))
      (OPTIONS "/orgs/:slug/inbox/" [slug] (pool/with-pool [conn db-pool] (inbox conn slug)))
      (GET "/orgs/:slug/inbox" [slug] (pool/with-pool [conn db-pool] (inbox conn slug)))
      (GET "/orgs/:slug/inbox/" [slug] (pool/with-pool [conn db-pool] (inbox conn slug))))))