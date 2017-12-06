(ns oc.storage.api.activity
  "Liberator API for org resources."
  (:require [clojure.walk :refer (keywordize-keys)]
            [if-let.core :refer (if-let*)]
            [compojure.core :as compojure :refer (OPTIONS GET)]
            [liberator.core :refer (defresource by-method)]
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
            [oc.storage.resources.entry :as entry-res]))

;; ----- Utility functions -----

(defn- valid-timestamp? [ts]
  (try
    (f/parse db-common/timestamp-format ts)
    true
    (catch IllegalArgumentException e
      false)))

;; TODO This activity stuff, `activity-sort`, `merge-activity` and `assemble-activity` is overly complicated
;; because it used to merge entries and stories. It no longer does so can be simplified. This also may entail some
;; changes to `entry/list-entries-by-org`

(defn- activity-sort
  "
  Compare function to sort 2 entries and/or activity by their `created-at` or `published-at` order respectively,
  in the order (:asc or :desc) provided.
  "
  [order x y]
  (let [order-flip (if (= order :desc) -1 1)]
    (* order-flip (compare (or (:published-at x) (:created-at x))
                           (or (:published-at y) (:created-at y))))))

(defn- merge-activity
  "Given a set of entries and stories and a sort order, return up to the default limit of them, intermixed and sorted."
  [entries stories order]
  (take config/default-activity-limit (sort (partial activity-sort order) (concat entries stories))))

(defn- assemble-activity
  "Assemble the requested activity (params) for the provided org."
  [conn {start :start direction :direction} org board-by-uuid allowed-boards]
  (let [order (if (= :after direction) :asc :desc)
        activities (cond

                  (= direction :around)
                  (let [previous-entries (entry-res/list-entries-by-org conn (:uuid org) :asc start :after allowed-boards)
                        next-entries (entry-res/list-entries-by-org conn (:uuid org) :desc start :before allowed-boards)
                        previous-activity (merge-activity previous-entries [] :asc)
                        next-activity (merge-activity next-entries [] :desc)]
                    {:direction :around
                     :previous-count (count previous-activity)
                     :next-count (count next-activity)
                     :activity (concat (reverse previous-activity) next-activity)})
                  
                  (= order :asc)
                  (let [previous-entries (entry-res/list-entries-by-org conn (:uuid org) order start direction allowed-boards)
                        previous-activity (merge-activity previous-entries [] :asc)]
                    {:direction :previous
                     :previous-count (count previous-activity)
                     :activity (reverse previous-activity)})

                  :else
                  (let [next-entries (entry-res/list-entries-by-org conn (:uuid org) order start direction allowed-boards)
                        next-activity (merge-activity next-entries [] :desc)]
                    {:direction :next
                     :next-count (count next-activity)
                     :activity next-activity}))]
    ;; Give each activity its board name
    (update activities :activity #(map (fn [activity] (merge activity {
                                                        :board-slug (:slug (board-by-uuid (:board-uuid activity)))
                                                        :board-name (:name (board-by-uuid (:board-uuid activity)))}))
                                    %))))

;; Calendar not used right now
; (defn- assemble-calendar
;   "
;   Given a sequence of months, e.g. `[[2017 06] [2017 04] [2016 11] [2016 07] [2015 12]]`

;   Return a map of the months by year, e.g. `{'2017' [[2017 06] [2017 04]]
;                                              '2016' [[2016 11] [2016 07]]
;                                              '2015' [[2015 12]]}`
;   "
;   [months]
;   (let [years (distinct (map first months))
;         months-by-year (map #(filter (fn [month] (= % (first month))) months) years)]
;     (zipmap years months-by-year)))

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
                              valid-start? (if start (valid-timestamp? start) true)
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
                        {:existing-org org}
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
                             allowed-boards (map :uuid (filter #(access/access-level-for org % user) boards))
                             board-uuids (map :uuid boards)
                             board-slugs-and-names (map #(array-map :slug (:slug %) :name (:name %)) boards)
                             board-by-uuid (zipmap board-uuids board-slugs-and-names)
                             activity (assemble-activity conn params org board-by-uuid allowed-boards)]
                          (activity-rep/render-activity-list conn params org activity (:access-level ctx) user-id))))

;; Calendar not used right now
;; A resource for operations on the calendar of activity for a particular Org
; (defresource calendar [conn slug]
;   (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

;   :allowed-methods [:options :get]

;   ;; Media type client accepts
;   :available-media-types [mt/activity-calendar-media-type]
;   :handle-not-acceptable (api-common/only-accept 406 mt/activity-calendar-media-type)

;   ;; Authorization
;   :allowed? (by-method {
;     :options true
;     :get (fn [ctx] (access/allow-members conn slug (:user ctx)))})

;   ;; Existentialism
;   :exists? (fn [ctx] (if-let* [_slug? (slugify/valid-slug? slug)
;                                org (or (:existing-org ctx) (org-res/get-org conn slug))]
;                         {:existing-org org}
;                         false))

;   ;; Responses
;   :handle-ok (fn [ctx] (let [user (:user ctx)
;                              user-id (:user-id user)
;                              org (:existing-org ctx)
;                              org-id (:uuid org)
;                              ;; TODO filter by allowed boards
;                              ;boards (board-res/list-boards-by-org conn org-id [:created-at :updated-at :authors :viewers :access])
;                              ;allowed-boards (map :uuid (filter #(access/access-level-for org % user) boards))
;                              ;board-uuids (map :uuid boards)
;                              months (entry-res/entry-months-by-org conn org-id)
;                              calendar-data (assemble-calendar months)]
;                           (activity-rep/render-activity-calendar org calendar-data (:access-level ctx) user-id))))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; All activity operations
      (OPTIONS "/orgs/:slug/activity" [slug] (pool/with-pool [conn db-pool] (activity conn slug)))
      (OPTIONS "/orgs/:slug/activity/" [slug] (pool/with-pool [conn db-pool] (activity conn slug)))
      (GET "/orgs/:slug/activity" [slug] (pool/with-pool [conn db-pool] (activity conn slug)))
      (GET "/orgs/:slug/activity/" [slug] (pool/with-pool [conn db-pool] (activity conn slug)))
      ;; Calendar not used right now
      ;; Calendar of activity operations
      ; (OPTIONS "/orgs/:slug/activity/calendar" [slug] (pool/with-pool [conn db-pool] (calendar conn slug)))
      ; (OPTIONS "/orgs/:slug/activity/calendar/" [slug] (pool/with-pool [conn db-pool] (calendar conn slug)))
      ; (GET "/orgs/:slug/activity/calendar" [slug] (pool/with-pool [conn db-pool] (calendar conn slug)))
      ; (GET "/orgs/:slug/activity/calendar/" [slug] (pool/with-pool [conn db-pool] (calendar conn slug)))
      )))