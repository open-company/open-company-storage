(ns oc.storage.api.activity
  "Liberator API for entry collection resources."
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
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.entry :as entry-res]
            [oc.storage.lib.timestamp :as ts]
            [oc.lib.time :as oc-time]
            [oc.lib.change.resources.follow :as follow]
            [oc.lib.change.resources.read :as read]
            [oc.lib.change.resources.seen :as seen]))

(def board-props [:created-at :updated-at :authors :viewers :access :publisher-board])

(defn follow-parameters-map
  ([user-id org-slug]
   (follow/retrieve config/dynamodb-opts user-id org-slug))
  ([user-id org-slug following?]
   (cond-> (follow/retrieve config/dynamodb-opts user-id org-slug)
     following? (merge {:following true})
     (not following?) (merge {:unfollowing true}))))

(defn- assemble-activity
  "Assemble the requested (by the params) activity for the provided org."
  [conn {start :start direction :direction must-see :must-see digest-request :digest-request
         sort-type :sort-type following :following unfollowing :unfollowing last-seen-at :last-seen-at
         limit :limit :as params}
   org board-by-uuids allowed-boards user-id]
  (let [follow? (or following unfollowing)
        follow-data (when follow?
                      (follow-parameters-map user-id (:slug org) following))
        entries (if follow?
                  (entry-res/paginated-entries-by-org conn (:uuid org) :desc start direction limit sort-type allowed-boards
                   follow-data last-seen-at {:must-see must-see})
                  (entry-res/paginated-entries-by-org conn (:uuid org) :desc start direction limit sort-type allowed-boards
                   {:must-see must-see}))
        total-count (entry-res/paginated-entries-by-org conn (:uuid org) :desc (oc-time/now-ts) :before 0 :recent-activity allowed-boards
                     follow-data nil {:count true :must-see must-see})
        activities {:next-count (count entries)
                    :direction direction
                    :total-count total-count}]
    ;; Give each activity its board name
    (assoc activities :activity (map (fn [activity] (let [board (board-by-uuids (:board-uuid activity))]
                                                      (merge activity {
                                                       :board-slug (:slug board)
                                                       :board-access (:access board)
                                                       :board-name (:name board)
                                                       :container-seen-at last-seen-at})))
                                    entries))))

(defn- assemble-bookmarks
  "Assemble the requested activity (params) for the provided org."
  [conn {start :start direction :direction must-see :must-see limit :limit} org board-by-uuids user-id]
  (let [total-bookmarks-count (entry-res/list-all-bookmarked-entries conn (:uuid org) user-id :desc
                               (oc-time/now-ts) :before 0 {:count true})
        entries (entry-res/list-all-bookmarked-entries conn (:uuid org) user-id :desc start direction
                 limit {:count false})
        activities {:direction direction
                    :next-count (count entries)
                    :total-count total-bookmarks-count}]
    ;; Give each activity its board name
    (assoc activities :activity (map (fn [activity] (let [board (board-by-uuids (:board-uuid activity))]
                                                      (merge activity {
                                                       :board-slug (:slug board)
                                                       :board-access (:access board)
                                                       :board-name (:name board)})))
                                  entries))))

(defn- assemble-inbox
  "Assemble the requested activity (params) for the provided org."
  [conn {start :start must-see :must-see} org board-by-uuids allowed-boards user-id]
  (let [follow-data (follow-parameters-map user-id (:slug org))
        total-inbox-count (entry-res/list-all-entries-for-inbox conn (:uuid org) user-id :desc (oc-time/now-ts)
                           0 allowed-boards follow-data {:count true})
        entries (entry-res/list-all-entries-for-inbox conn (:uuid org) user-id :desc start config/default-activity-limit
                 allowed-boards follow-data {})
        activities {:next-count (count entries)
                    :total-count total-inbox-count}]
    ;; Give each activity its board name
    (assoc activities :activity (map (fn [activity] (let [board (board-by-uuids (:board-uuid activity))]
                                                      (merge activity {
                                                       :board-slug (:slug board)
                                                       :board-access (:access board)
                                                       :board-name (:name board)})))
                                 entries))))

(defn assemble-replies
  "Assemble the requested (by the params) entries for the provided org to populate the replies view."
  [conn {start :start direction :direction last-seen-at :last-seen-at limit :limit :as params}
   org board-by-uuids allowed-boards user-id]
  (let [follow-data (follow-parameters-map user-id (:slug org))
        replies (entry-res/list-entries-for-user-replies conn (:uuid org) allowed-boards user-id :desc start direction limit follow-data last-seen-at {})
        total-count (entry-res/list-entries-for-user-replies conn (:uuid org) allowed-boards user-id :desc (oc-time/now-ts) :before 0 follow-data nil {:count true})
        result {:next-count (count replies)
                :direction direction
                :total-count total-count}]
    ;; Give each activity its board name
    (assoc result :activity (map (fn [entry] (let [board (board-by-uuids (:board-uuid entry))]
                                                          (merge entry {
                                                           :board-slug (:slug board)
                                                           :board-access (:access board)
                                                           :board-name (:name board)
                                                           :container-seen-at last-seen-at})))
                             replies))))

(defn- assemble-contributions
  "Assemble the requested activity (based on the params) for the provided org that's published by the given user."
  [conn {start :start direction :direction sort-type :sort-type last-seen-at :last-seen-at limit :limit} org board-by-uuids allowed-boards author-uuid]
  (let [total-contributions-count (entry-res/list-entries-by-org-author conn (:uuid org)
                                 author-uuid :desc (oc-time/now-ts) direction 0 sort-type allowed-boards nil {:count true})
        entries (entry-res/list-entries-by-org-author conn (:uuid org) author-uuid
                 :desc start direction limit sort-type allowed-boards last-seen-at)
        activities {:next-count (count entries)
                    :author-uuid author-uuid
                    :total-count total-contributions-count}]
    ;; Give each activity its board name
    (assoc activities :activity (map (fn [activity] (let [board (board-by-uuids (:board-uuid activity))]
                                                      (merge activity {
                                                       :board-slug (:slug board)
                                                       :board-access (:access board)
                                                       :board-name (:name board)})))
                                 entries))))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for operations on the activity of a particular Org
(defresource activity [conn slug]
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
  :malformed? (fn [ctx] (let [ctx-params (-> ctx :request :params keywordize-keys)
                              start (:start ctx-params)
                              valid-start? (if start (try (Long. start) (catch java.lang.NumberFormatException e false)) true)
                              direction (keyword (:direction ctx-params))
                              ;; no direction is OK, but if specified it's from the allowed enumeration of options
                              valid-direction? (if direction (#{:before :after} direction) true)
                              valid-sort? (or (not (contains? ctx-params :sort))
                                              (= (:sort ctx-params) "activity"))
                              ;; a specified start/direction must be together or ommitted
                              pairing-allowed? (or (and start direction)
                                                   (and (not start) (not direction)))
                              ;; can have :following or :unfollowing or none, but not both
                              following? (contains? ctx-params :following)
                              unfollowing? (contains? ctx-params :unfollowing)
                              valid-follow? (not (and following? unfollowing?))]
                           (not (and valid-start? valid-direction? valid-sort? pairing-allowed? valid-follow?))))

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
                             following? (:following ctx-params)
                             container-seen (when following?
                                              (seen/retrieve-by-user-container config/dynamodb-opts user-id config/seen-home-container-id))
                             params (-> ctx-params
                                     (dissoc :slug)
                                     (update :start #(if % (Long. %) (oc-time/now-ts)))  ; default is now
                                     (assoc :digest-request (= (:auth-source user) "digest"))
                                     (update :direction #(if % (keyword %) :before)) ; default is before
                                     (assoc :limit (if (or (= :after (keyword (:direction ctx-params)))
                                                           (= (:auth-source user) :digest-request))
                                                     0 ;; In case of a digest request or if a refresh request
                                                     config/default-activity-limit)) ;; fallback to the default pagination otherwise
                                     (assoc :sort-type (if (= (:sort ctx-params) "activity") :recent-activity :recently-posted))
                                     (assoc :container-id (when following? config/seen-home-container-id))
                                     (assoc :last-seen-at (:seen-at container-seen))
                                     (assoc :next-seen-at (db-common/current-timestamp)))
                             boards (board-res/list-boards-by-org conn org-id board-props)
                             allowed-boards (map :uuid (filter #(access/access-level-for org % user) boards))
                             board-uuids (map :uuid boards)
                             board-slugs-and-names (map #(array-map :slug (:slug %) :access (:access %) :name (:name %)) boards)
                             board-by-uuids (zipmap board-uuids board-slugs-and-names)
                             items (assemble-activity conn params org board-by-uuids allowed-boards user-id)]
                          (activity-rep/render-activity-list params org "entries" items boards user))))

;; A resource for operations on the activity of a particular Org
(defresource bookmarks [conn slug]
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
  :malformed? (fn [ctx] (let [ctx-params (-> ctx :request :params keywordize-keys)
                              start (:start ctx-params)
                              valid-start? (if start (try (Long. start) (catch java.lang.NumberFormatException e false)) true)
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
                             params (-> ctx-params
                                     (dissoc :slug)
                                     (update :start #(if % (Long. %) (oc-time/now-ts)))  ; default is now
                                     (update :direction #(if % (keyword %) :before)) ; default is before
                                     (assoc :limit (if (or (= :after (keyword (:direction ctx-params)))
                                                           (= (:auth-source user) :digest-request))
                                                     0 ;; In case of a digest request or if a refresh request
                                                     config/default-activity-limit))) ;; fallback to the default pagination otherwise
                             boards (board-res/list-boards-by-org conn org-id board-props)
                             allowed-boards (map :uuid (filter #(access/access-level-for org % user) boards))
                             board-uuids (map :uuid boards)
                             board-slugs-and-names (map #(array-map :slug (:slug %) :access (:access %) :name (:name %)) boards)
                             board-by-uuids (zipmap board-uuids board-slugs-and-names)
                             items (assemble-bookmarks conn params org board-by-uuids user-id)]
                          (activity-rep/render-activity-list params org "bookmarks" items boards user))))

;; A resource to retrieve entries with unread activity
(defresource inbox [conn slug]
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
  :malformed? (fn [ctx] (let [ctx-params (-> ctx :request :params keywordize-keys)
                              start (:start ctx-params)
                              valid-start? (if start (try (Long. start) (catch java.lang.NumberFormatException e false)) true)
                              ;; can have :following or :unfollowing or none, but not both
                              following? (contains? ctx-params :following)
                              unfollowing? (contains? ctx-params :unfollowing)
                              valid-follow? (not (and following? unfollowing?))]
                          (not (and valid-start? valid-follow?))))
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
                             params (update ctx-params :start #(if % (Long. %) (oc-time/now-ts))) ; default is now
                             boards (board-res/list-boards-by-org conn org-id board-props)
                             board-uuids (map :uuid boards)
                             allowed-boards (map :uuid (filter #(access/access-level-for org % user) boards))
                             board-slugs-and-names (map #(array-map :slug (:slug %) :access (:access %) :name (:name %)) boards)
                             board-by-uuids (zipmap board-uuids board-slugs-and-names)
                            items (assemble-inbox conn params org board-by-uuids allowed-boards user-id)]
                          (activity-rep/render-activity-list params org "inbox" items boards user))))

;; A resource to retrieve the replies of a particular Org
(defresource replies [conn slug]
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
  :malformed? (fn [ctx] (let [ctx-params (-> ctx :request :params keywordize-keys)
                              start (:start ctx-params)
                              valid-start? (if start (try (Long. start) (catch java.lang.NumberFormatException e false)) true)
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
                             container-seen (seen/retrieve-by-user-container config/dynamodb-opts user-id config/seen-replies-container-id)
                             ctx-params (-> ctx :request :params keywordize-keys)
                             params (-> ctx-params
                                     (dissoc :slug)
                                     (update :start #(if % (Long. %) (oc-time/now-ts)))  ; default is now
                                     (assoc :limit (if (or (= :after (keyword (:direction ctx-params)))
                                                           (= (:auth-source user) :digest-request))
                                                     0 ;; In case of a digest request or if a refresh request
                                                     config/default-activity-limit)) ;; fallback to the default pagination otherwise
                                     (update :direction #(if % (keyword %) :before)) ; default is before
                                     (assoc :container-id config/seen-replies-container-id)
                                     (assoc :last-seen-at (:seen-at container-seen))
                                     (assoc :next-seen-at (db-common/current-timestamp)))
                             boards (board-res/list-boards-by-org conn org-id board-props)
                             allowed-boards (map :uuid (filter #(access/access-level-for org % user) boards))
                             board-uuids (map :uuid boards)
                             board-slugs-and-names (map #(array-map :slug (:slug %) :access (:access %) :name (:name %)) boards)
                             board-by-uuids (zipmap board-uuids board-slugs-and-names)
                             items (assemble-replies conn params org board-by-uuids allowed-boards user-id)]
                          (activity-rep/render-activity-list params org "replies" items boards user))))

;; A resource to retrieve entries for a given user
(defresource contributions [conn slug author-uuid]
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
  :malformed? (fn [ctx] (let [ctx-params (-> ctx :request :params keywordize-keys)
                              start (:start ctx-params)
                              valid-start? (if start (try (Long. start) (catch java.lang.NumberFormatException e false)) true)
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
                             user-id (:user-id ctx)
                             org (:existing-org ctx)
                             org-id (:uuid org)
                             container-seen (seen/retrieve-by-user-container config/dynamodb-opts user-id author-uuid)
                             ctx-params (-> ctx :request :params keywordize-keys)
                             params (-> ctx-params
                                     (dissoc :slug)
                                     (update :start #(if % (Long. %) (oc-time/now-ts)))  ; default is now
                                     (assoc :limit (if (or (= :after (keyword (:direction ctx-params)))
                                                           (= (:auth-source user) :digest-request))
                                                     0 ;; In case of a digest request or if a refresh request
                                                     config/default-activity-limit)) ;; fallback to the default pagination otherwise
                                     (update :direction #(if % (keyword %) :before)) ; default is before
                                     (assoc :container-id config/seen-replies-container-id)
                                     (assoc :last-seen-at (:seen-at container-seen))
                                     (assoc :next-seen-at (db-common/current-timestamp))
                                     (assoc :author-uuid author-uuid)
                                     (assoc :sort-type (if (= (:sort ctx-params) "activity") :recent-activity :recently-posted)))

                             boards (board-res/list-boards-by-org conn org-id board-props)
                             board-uuids (map :uuid boards)
                             allowed-boards (map :uuid (filter #(access/access-level-for org % user) boards))
                             board-slugs-and-names (map #(array-map :slug (:slug %) :access (:access %) :name (:name %)) boards)
                             board-by-uuids (zipmap board-uuids board-slugs-and-names)
                             items (assemble-contributions conn params org board-by-uuids allowed-boards author-uuid)]
                          (activity-rep/render-activity-list params org "contributions" items boards user))))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; All activity operations
      (OPTIONS "/orgs/:slug/entries" [slug] (pool/with-pool [conn db-pool] (activity conn slug)))
      (OPTIONS "/orgs/:slug/entries/" [slug] (pool/with-pool [conn db-pool] (activity conn slug)))
      (GET "/orgs/:slug/entries" [slug] (pool/with-pool [conn db-pool] (activity conn slug)))
      (GET "/orgs/:slug/entries/" [slug] (pool/with-pool [conn db-pool] (activity conn slug)))

      (OPTIONS "/orgs/:slug/bookmarks" [slug] (pool/with-pool [conn db-pool] (bookmarks conn slug)))
      (OPTIONS "/orgs/:slug/bookmarks/" [slug] (pool/with-pool [conn db-pool] (bookmarks conn slug)))
      (GET "/orgs/:slug/bookmarks" [slug] (pool/with-pool [conn db-pool] (bookmarks conn slug)))
      (GET "/orgs/:slug/bookmarks/" [slug] (pool/with-pool [conn db-pool] (bookmarks conn slug)))

      (OPTIONS "/orgs/:slug/inbox" [slug] (pool/with-pool [conn db-pool] (inbox conn slug)))
      (OPTIONS "/orgs/:slug/inbox/" [slug] (pool/with-pool [conn db-pool] (inbox conn slug)))
      (GET "/orgs/:slug/inbox" [slug] (pool/with-pool [conn db-pool] (inbox conn slug)))
      (GET "/orgs/:slug/inbox/" [slug] (pool/with-pool [conn db-pool] (inbox conn slug)))

      (OPTIONS "/orgs/:slug/replies" [slug] (pool/with-pool [conn db-pool] (replies conn slug)))
      (OPTIONS "/orgs/:slug/replies/" [slug] (pool/with-pool [conn db-pool] (replies conn slug)))
      (GET "/orgs/:slug/replies" [slug] (pool/with-pool [conn db-pool] (replies conn slug)))
      (GET "/orgs/:slug/replies/" [slug] (pool/with-pool [conn db-pool] (replies conn slug)))

      (OPTIONS "/orgs/:slug/contributions/:author-uuid"
        [slug author-uuid] (pool/with-pool [conn db-pool] (contributions conn slug author-uuid)))
      (OPTIONS "/orgs/:slug/contributions/:author-uuid/"
        [slug author-uuid] (pool/with-pool [conn db-pool] (contributions conn slug author-uuid)))
      (GET "/orgs/:slug/contributions/:author-uuid"
        [slug author-uuid] (pool/with-pool [conn db-pool] (contributions conn slug author-uuid)))
      (GET "/orgs/:slug/contributions/:author-uuid/"
        [slug author-uuid] (pool/with-pool [conn db-pool] (contributions conn slug author-uuid))))))