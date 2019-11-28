(ns oc.storage.api.activity
  "Liberator API for org resources."
  (:require [clojure.walk :refer (keywordize-keys)]
            [if-let.core :refer (if-let*)]
            [compojure.core :as compojure :refer (OPTIONS GET)]
            [liberator.core :refer (defresource by-method)]
            [clj-time.core :as t]
            [clj-time.format :as f]
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
            [oc.storage.lib.sort :as sort]
            [oc.storage.lib.timestamp :as ts]
            [oc.lib.change.resources.read :as read]))

;; TODO This `assemble-activity` is overly complicated because it used to merge entries
;; and stories. It no longer does so can be simplified. This also may entail some
;; changes to `entry/list-entries-by-org`
(defn- assemble-activity
  "Assemble the requested activity (params) for the provided org."
  [conn {start :start direction :direction must-see :must-see digest-request :digest-request}
   org sort-type board-by-uuid allowed-boards user-id]
  (let [order (if (= :after direction) :asc :desc)
        user-reads (read/retrieve-by-user config/dynamodb-opts user-id)
        activities (cond

                  (= direction :around)
                  ;; around is inclusive of the provided timestamp, so we offset the after timestamp by 1ms so as not
                  ;; to exclude the provided timestamp (essentially with '> timestamp' and '< timestamp').
                  ;; This means we actually have a 1ms overlap, but in practice, this is OK.
                  (let [start-stamp (f/parse db-common/timestamp-format start)
                        around-stamp (t/minus start-stamp (t/millis 1))
                        around-start (f/unparse db-common/timestamp-format around-stamp)
                        previous-entries (entry-res/list-entries-by-org conn (:uuid org) :asc around-start :after allowed-boards {:must-see must-see})
                        previous-activity-limit (if digest-request (count previous-entries) config/default-activity-limit)
                        next-entries (entry-res/list-entries-by-org conn (:uuid org) :desc start :before allowed-boards {:must-see must-see})
                        next-activity-limit (if digest-request (count previous-entries) config/default-activity-limit)
                        previous-activity (sort/sort-activity previous-entries sort-type around-start :asc previous-activity-limit user-id user-reads)
                        next-activity (sort/sort-activity next-entries sort-type start :desc next-activity-limit user-id user-reads)]
                    {:direction :around
                     :previous-count (count previous-activity)
                     :next-count (count next-activity)
                     :activity (concat (reverse previous-activity) next-activity)})
                  
                  (= order :asc)
                  (let [previous-entries (entry-res/list-entries-by-org conn (:uuid org) order start direction allowed-boards {:must-see must-see})
                        activity-limit (if digest-request (count previous-entries) config/default-activity-limit)
                        previous-activity (sort/sort-activity previous-entries sort-type start :asc activity-limit user-id user-reads)]
                    {:direction :previous
                     :previous-count (count previous-activity)
                     :activity (reverse previous-activity)})

                  :else
                  (let [next-entries (entry-res/list-entries-by-org conn (:uuid org) order start direction allowed-boards {:must-see must-see})
                        activity-limit (if digest-request (count next-entries) config/default-activity-limit)
                        next-activity (sort/sort-activity next-entries sort-type start :desc activity-limit user-id user-reads)]
                    {:direction :next
                     :next-count (count next-activity)
                     :activity next-activity}))]
    ;; Give each activity its board name
    (update activities :activity #(map (fn [activity] (let [board (board-by-uuid (:board-uuid activity))]
                                                       (merge activity {
                                                        :board-slug (:slug board)
                                                        :board-access (:access board)
                                                        :board-name (:name board)})))
                                    %))))

(defn- assemble-follow-ups
  "Assemble the requested activity (params) for the provided org."
  [conn {start :start direction :direction must-see :must-see} org sort-type board-by-uuid user-id]
  (let [order (if (= :after direction) :asc :desc)
        user-reads (read/retrieve-by-user config/dynamodb-opts user-id)
        activities (cond

                  (= direction :around)
                  ;; around is inclusive of the provided timestamp, so we offset the after timestamp by 1ms so as not
                  ;; to exclude the provided timestamp (essentially with '> timestamp' and '< timestamp').
                  ;; This means we actually have a 1ms overlap, but in practice, this is OK.
                  (let [start-stamp (f/parse db-common/timestamp-format start)
                        around-stamp (t/minus start-stamp (t/millis 1))
                        around-start (f/unparse db-common/timestamp-format around-stamp)
                        previous-entries (entry-res/list-entries-by-org conn (:uuid org) :asc around-start :after)
                        next-entries (entry-res/list-all-entries-by-follow-ups conn (:uuid org) user-id :desc start :before)
                        previous-activity (sort/sort-activity previous-entries sort-type around-start :asc config/default-activity-limit user-id user-reads)
                        next-activity (sort/sort-activity next-entries sort-type start :desc config/default-activity-limit user-id user-reads)]
                    {:direction :around
                     :previous-count (count previous-activity)
                     :next-count (count next-activity)
                     :activity (concat (reverse previous-activity) next-activity)})

                  (= order :asc)
                  (let [previous-entries (entry-res/list-all-entries-by-follow-ups conn (:uuid org) user-id order start direction)
                        previous-activity (sort/sort-activity previous-entries sort-type start :asc config/default-activity-limit user-id user-reads)]
                    {:direction :previous
                     :previous-count (count previous-activity)
                     :activity (reverse previous-activity)})

                  :else
                  (let [next-entries (entry-res/list-all-entries-by-follow-ups conn (:uuid org) user-id order start direction)
                        next-activity (sort/sort-activity next-entries sort-type start :desc config/default-activity-limit user-id user-reads)]
                    {:direction :next
                     :next-count (count next-activity)
                     :activity next-activity}))]
    ;; Give each activity its board name
    (update activities :activity #(map (fn [activity] (let [board (board-by-uuid (:board-uuid activity))]
                                                       (merge activity {
                                                        :board-slug (:slug board)
                                                        :board-access (:access board)
                                                        :board-name (:name board)})))
                                    %))))

(defn- assemble-inbox
  "Assemble the requested activity (params) for the provided org."
  [conn {start :start direction :direction must-see :must-see} org board-by-uuid user-id]
  (let [order (if (= :after direction) :asc :desc)
        user-reads (read/retrieve-by-user config/dynamodb-opts user-id)
        activities (cond

                  (= direction :around)
                  ;; around is inclusive of the provided timestamp, so we offset the after timestamp by 1ms so as not
                  ;; to exclude the provided timestamp (essentially with '> timestamp' and '< timestamp').
                  ;; This means we actually have a 1ms overlap, but in practice, this is OK.
                  (let [start-stamp (f/parse db-common/timestamp-format start)
                        around-stamp (t/minus start-stamp (t/millis 1))
                        around-start (f/unparse db-common/timestamp-format around-stamp)
                        previous-entries (entry-res/list-entries-by-org conn (:uuid org) :asc around-start :after)
                        next-entries (entry-res/list-all-entries-for-inbox conn (:uuid org) user-id :desc start :before)
                        previous-activity (sort/sort-activity previous-entries :recent-activity around-start :asc config/default-activity-limit user-id user-reads)
                        next-activity (sort/sort-activity next-entries :recent-activity start :desc config/default-activity-limit user-id user-reads)]
                    {:direction :around
                     :previous-count (count previous-activity)
                     :next-count (count next-activity)
                     :activity (concat (reverse previous-activity) next-activity)})

                  (= order :asc)
                  (let [previous-entries (entry-res/list-all-entries-for-inbox conn (:uuid org) user-id order start direction)
                        previous-activity (sort/sort-activity previous-entries :recent-activity start :asc config/default-activity-limit user-id user-reads)]
                    {:direction :previous
                     :previous-count (count previous-activity)
                     :activity (reverse previous-activity)})

                  :else
                  (let [next-entries (entry-res/list-all-entries-for-inbox conn (:uuid org) user-id order start direction)
                        next-activity (sort/sort-activity next-entries :recent-activity start :desc config/default-activity-limit user-id user-reads)]
                    {:direction :next
                     :next-count (count next-activity)
                     :activity next-activity}))]
    ;; Give each activity its board name
    (update activities :activity #(map (fn [activity] (let [board (board-by-uuid (:board-uuid activity))]
                                                       (merge activity {
                                                        :board-slug (:slug board)
                                                        :board-access (:access board)
                                                        :board-name (:name board)})))
                                    %))))

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
                             sort (:sort ctx-params)
                             sort-type (if (= sort "activity") :recent-activity :recently-posted)
                             start? (if (:start ctx-params) true false) ; flag if a start was specified
                             start-params (update ctx-params :start #(or % (db-common/current-timestamp))) ; default is now
                             direction (or (#{:after :around} (keyword (:direction ctx-params))) :before) ; default is before
                             params (merge start-params {:direction direction :start? start?})
                             boards (board-res/list-boards-by-org conn org-id [:created-at :updated-at :authors :viewers :access])
                             allowed-boards (map :uuid (filter #(access/access-level-for org % user) boards))
                             board-uuids (map :uuid boards)
                             board-slugs-and-names (map #(array-map :slug (:slug %) :access (:access %) :name (:name %)) boards)
                             board-by-uuid (zipmap board-uuids board-slugs-and-names)
                             fixed-params (if (= (:auth-source user) "digest")
                                            (assoc params :digest-request true)
                                            params)
                             activity (assemble-activity conn fixed-params org sort-type board-by-uuid allowed-boards user-id)]
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
                             ctx-params (-> ctx :request :params keywordize-keys)
                             sort (:sort ctx-params)
                             sort-type (if (= sort "activity") :recent-activity :recently-posted)
                             start? (if (:start ctx-params) true false) ; flag if a start was specified
                             start-params (update ctx-params :start #(or % (db-common/current-timestamp))) ; default is now
                             direction (or (-> ctx-params :direction keyword #{:after :around}) :before) ; default is before
                             params (merge start-params {:direction direction :start? start?})
                             boards (board-res/list-boards-by-org conn org-id [:created-at :updated-at :authors :viewers :access])
                             board-uuids (map :uuid boards)
                             board-slugs-and-names (map #(array-map :slug (:slug %) :access (:access %) :name (:name %)) boards)
                             board-by-uuid (zipmap board-uuids board-slugs-and-names)
                             activity (assemble-follow-ups conn params org board-by-uuid user-id)]
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
                             board-slugs-and-names (map #(array-map :slug (:slug %) :access (:access %) :name (:name %)) boards)
                             board-by-uuid (zipmap board-uuids board-slugs-and-names)
                             activity (assemble-inbox conn params org board-by-uuid user-id)]
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