(ns oc.storage.api.activity
  "Liberator API for entry collection resources."
  (:require [if-let.core :refer (if-let*)]
            [defun.core :refer (defun)]
            [compojure.core :as compojure :refer (OPTIONS GET)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.slugify :as slugify]
            [oc.lib.db.pool :as pool]
            [oc.lib.db.common :as db-common]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.resources.common :as common]
            [oc.storage.api.access :as access]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.activity :as activity-rep]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.activity :as activity-res]
            [oc.storage.resources.label :as label-res]
            [oc.lib.change.resources.follow :as follow]
            [oc.lib.change.resources.seen :as seen]
            [oc.storage.urls.org :as org-urls]
            [oc.storage.urls.label :as label-urls]))

;; ---- Boards list -----

(def board-props [:created-at :updated-at :authors :viewers :access :publisher-board])

(defn user-boards-by-uuid
  ([conn user org] (user-boards-by-uuid conn user org board-props))
  ([conn user org props-list]
   (let [boards (board-res/list-boards-by-org conn (:uuid org) props-list)
         boards-with-access (map #(access/board-with-access-level org % user) boards)
         allowed-boards (filter :access-level boards-with-access)]
     (zipmap (map :uuid allowed-boards) allowed-boards))))

;; ---- Helpers for request parameters ----

(defun follow-parameters-map
  ([user-id org false]
   (-> (follow-parameters-map user-id org)
       (assoc :unfollowing true)))
  ([user-id org true]
   (-> (follow-parameters-map user-id org)
       (assoc :following true)))
  ([user-id org :guard map?]
   (follow-parameters-map user-id (:slug org)))
  ([user-id org-slug]
   (follow/retrieve config/dynamodb-opts user-id org-slug)))

;; ---- Activity lists assemble ----

(defn assemble-activity
  "Assemble the requested (by the params) activity for the provided org."
  [conn {start :start direction :direction container-id :container-id following :following unfollowing :unfollowing
         last-seen-at :last-seen-at limit :limit}
   org boards-by-uuid user-id]
  (let [allowed-boards (vals boards-by-uuid)
        follow? (or following unfollowing)
        follow-data (cond following
                          (follow-parameters-map user-id org true)
                          unfollowing
                          (follow-parameters-map user-id org false)
                          :else
                          (follow-parameters-map user-id org))
        total-count (activity-res/paginated-recently-posted-entries-by-org conn (:uuid org) :desc nil :before 0 allowed-boards
                                                                           follow-data last-seen-at {:container-id container-id :count true})
        entries (activity-res/paginated-recently-posted-entries-by-org conn (:uuid org) :desc start direction limit allowed-boards
                                                                       follow-data last-seen-at {:container-id container-id})
        activities {:next-count (count entries)
                    :direction direction
                    :total-count total-count}]
    ;; Give each activity its board name
    (assoc activities :activity (map (fn [activity] (let [board (boards-by-uuid (:board-uuid activity))]
                                                      (merge activity {
                                                       :board-slug (:slug board)
                                                       :board-access (:access board)
                                                       :board-name (:name board)
                                                       :container-seen-at last-seen-at})))
                                    entries))))

(defn- assemble-bookmarks
  "Assemble the requested activity (params) for the provided org."
  [conn {start :start direction :direction limit :limit} org boards-by-uuid user-id]
  (let [allowed-boards (vals boards-by-uuid)
        total-count (activity-res/list-all-bookmarked-entries conn (:uuid org) user-id allowed-boards :desc nil :before 0 {:count true})
        entries (activity-res/list-all-bookmarked-entries conn (:uuid org) user-id allowed-boards :desc start direction limit {:count false})
        activities {:direction direction
                    :next-count (count entries)
                    :total-count total-count}]
    ;; Give each activity its board name
    (assoc activities :activity (map (fn [activity] (let [board (boards-by-uuid (:board-uuid activity))]
                                                      (merge activity {
                                                       :board-slug (:slug board)
                                                       :board-access (:access board)
                                                       :board-name (:name board)})))
                                  entries))))

(defn assemble-replies
  "Assemble the requested (by the params) entries for the provided org to populate the replies view."
  [conn {start :start direction :direction last-seen-at :last-seen-at limit :limit}
   org boards-by-uuid user-id]
  (let [allowed-boards (vals boards-by-uuid)
        total-count (activity-res/list-entries-for-user-replies conn (:uuid org) allowed-boards user-id :desc nil :before 0 nil {:count true})
        replies (activity-res/list-entries-for-user-replies conn (:uuid org) allowed-boards user-id :desc start direction limit last-seen-at {})
        result {:next-count (count replies)
                :direction direction
                :total-count total-count}]
    ;; Give each activity its board name
    (assoc result :activity (map (fn [entry] (let [board (boards-by-uuid (:board-uuid entry))]
                                                          (merge entry {
                                                           :board-slug (:slug board)
                                                           :board-access (:access board)
                                                           :board-name (:name board)
                                                           :container-seen-at last-seen-at})))
                             replies))))

(defn- assemble-contributions
  "Assemble the requested activity (based on the params) for the provided org that's published by the given user."
  [conn {start :start direction :direction limit :limit} org boards-by-uuid author-uuid]
  (let [allowed-boards (vals boards-by-uuid)
        total-count (activity-res/list-entries-by-org-author conn (:uuid org) author-uuid :desc nil direction 0 allowed-boards {:count true})
        entries (activity-res/list-entries-by-org-author conn (:uuid org) author-uuid :desc start direction limit allowed-boards {})
        activities {:next-count (count entries)
                    :author-uuid author-uuid
                    :total-count total-count}]
    ;; Give each activity its board name
    (assoc activities :activity (map (fn [activity] (let [board (boards-by-uuid (:board-uuid activity))]
                                                      (merge activity {
                                                       :board-slug (:slug board)
                                                       :board-access (:access board)
                                                       :board-name (:name board)})))
                                 entries))))

(defn- assemble-label
  "Assemble the requested activity (based on the params) for the provided org that contains the give label."
  [conn {start :start direction :direction limit :limit label-by-uuid :label-by-uuid} org boards-by-uuid label]
  (let [allowed-boards (vals boards-by-uuid)
        total-count (if label-by-uuid
                      (activity-res/list-entries-by-org-label-uuid conn (:uuid org) label :desc nil direction 0 allowed-boards {:count true})
                      (activity-res/list-entries-by-org-label-slug conn (:uuid org) label :desc nil direction 0 allowed-boards {:count true}))
        entries (if label-by-uuid
                  (activity-res/list-entries-by-org-label-uuid conn (:uuid org) label :desc start direction limit allowed-boards {})
                  (activity-res/list-entries-by-org-label-slug conn (:uuid org) label :desc start direction limit allowed-boards {}))
        activities {:next-count (count entries)
                    :label label
                    :total-count total-count}]
    ;; Give each activity its board name
    (assoc activities :activity (map (fn [activity] (let [board (boards-by-uuid (:board-uuid activity))]
                                                      (merge activity {
                                                       :board-slug (:slug board)
                                                       :board-access (:access board)
                                                       :board-name (:name board)})))
                                 entries))))

;; ---- Responses -----

(defn activity-response [conn ctx]
  (let [user (:user ctx)
        user-id (:user-id user)
        org (:existing-org ctx)
        ctx-params (-> ctx :request :params)
        following? (:following ctx-params)
        container-seen (when following?
                         (seen/retrieve-by-user-container config/dynamodb-opts user-id config/seen-home-container-id))
        params (-> ctx-params
                   (dissoc :slug)
                   (update :direction #(if % (keyword %) :before)) ; default is before
                   (assoc :limit (if (= :after (keyword (:direction ctx-params)))
                                   0 ;; In case of a digest request or if a refresh request
                                   config/default-activity-limit)) ;; fallback to the default pagination otherwise
                   (assoc :container-id (when following? config/seen-home-container-id))
                   (assoc :last-seen-at (:seen-at container-seen))
                   (assoc :next-seen-at (db-common/current-timestamp)))
        boards-by-uuid (:boards-by-uuid ctx)
        items (assemble-activity conn params org boards-by-uuid user-id)]
    (activity-rep/render-activity-list params org "entries" items boards-by-uuid user)))

(defn bookmarks-response [conn ctx]
  (let [user (:user ctx)
        user-id (:user-id user)
        org (:existing-org ctx)
        ctx-params (-> ctx :request :params)
        params (-> ctx-params
                   (dissoc :slug)
                   (update :direction #(if % (keyword %) :before)) ; default is before
                   (assoc :limit (if (= :after (keyword (:direction ctx-params)))
                                   0 ;; In case of a digest request or if a refresh request
                                   config/default-activity-limit))) ;; fallback to the default pagination otherwise
        boards-by-uuid (:boards-by-uuid ctx)
        items (assemble-bookmarks conn params org boards-by-uuid user-id)]
    (activity-rep/render-activity-list params org "bookmarks" items boards-by-uuid user)))

(defn replies-response [conn ctx]
  (let [user (:user ctx)
        user-id (:user-id user)
        org (:existing-org ctx)
        container-seen (seen/retrieve-by-user-container config/dynamodb-opts user-id config/seen-replies-container-id)
        ctx-params (-> ctx :request :params)
        params (-> ctx-params
                   (dissoc :slug)
                   (assoc :limit (if (= :after (keyword (:direction ctx-params)))
                                   0 ;; In case of a digest request or if a refresh request
                                   config/default-activity-limit)) ;; fallback to the default pagination otherwise
                   (update :direction #(if % (keyword %) :before)) ; default is before
                   (assoc :container-id config/seen-replies-container-id)
                   (assoc :last-seen-at (:seen-at container-seen))
                   (assoc :next-seen-at (db-common/current-timestamp)))
        boards-by-uuid (:boards-by-uuid ctx)
        items (assemble-replies conn params org boards-by-uuid user-id)]
    (activity-rep/render-activity-list params org "replies" items boards-by-uuid user)))

(defn contributions-response [conn ctx author-uuid]
  (let [user (:user ctx)
        user-id (:user-id ctx)
        org (:existing-org ctx)
        container-seen (seen/retrieve-by-user-container config/dynamodb-opts user-id author-uuid)
        ctx-params (-> ctx :request :params)
        params (-> ctx-params
                   (dissoc :slug)
                   (assoc :limit (if (= :after (keyword (:direction ctx-params)))
                                   0 ;; In case of a digest request or if a refresh request
                                   config/default-activity-limit)) ;; fallback to the default pagination otherwise
                   (update :direction #(if % (keyword %) :before)) ; default is before
                   (assoc :container-id author-uuid)
                   (assoc :last-seen-at (:seen-at container-seen))
                   (assoc :next-seen-at (db-common/current-timestamp))
                   (assoc :author-uuid author-uuid))
        boards-by-uuid (:boards-by-uuid ctx)
        items (assemble-contributions conn params org boards-by-uuid author-uuid)]
    (activity-rep/render-activity-list params org "contributions" items boards-by-uuid user)))

(defn label-response [conn ctx label]
  (let [user (:user ctx)
        user-id (:user-id ctx)
        org (:existing-org ctx)
        existing-label (:existing-label ctx)
        container-seen (when (:uuid existing-label)
                         (seen/retrieve-by-user-container config/dynamodb-opts user-id (:uuid existing-label)))
        ctx-params (-> ctx :request :params)
        params (-> ctx-params
                   (dissoc :slug)
                   (assoc :limit (if (= :after (keyword (:direction ctx-params)))
                                   0 ;; In case of a digest request or if a refresh request
                                   config/default-activity-limit)) ;; fallback to the default pagination otherwise
                   (update :direction #(if % (keyword %) :before)) ; default is before
                   (assoc :container-id (:uuid existing-label))
                   (assoc :last-seen-at (:seen-at container-seen))
                   (assoc :next-seen-at (db-common/current-timestamp))
                   (assoc :label-slug label))
        boards-by-uuid (:boards-by-uuid ctx)
        updated-params (assoc params :label-by-uuid (:label-by-uuid ctx))
        items (assemble-label conn updated-params org boards-by-uuid label)]
    (activity-rep/render-activity-list params org "label" items boards-by-uuid user)))

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
  :malformed? (fn [ctx] (let [ctx-params (-> ctx :request :params)
                              start (:start ctx-params)
                              valid-start? (common/sort-value? start)
                              direction (keyword (:direction ctx-params))
                              ;; no direction is OK, but if specified it's from the allowed enumeration of options
                              valid-direction? (if direction (#{:before :after} direction) true)
                              ;; a specified start/direction must be together or ommitted
                              pairing-allowed? (or (and start direction)
                                                   (and (not start) (not direction)))
                              ;; can have :following or :unfollowing or none, but not both
                              following? (contains? ctx-params :following)
                              unfollowing? (contains? ctx-params :unfollowing)
                              valid-follow? (not (and following? unfollowing?))]
                           (not (and valid-start? valid-direction? pairing-allowed? valid-follow?))))

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slug? (slugify/valid-slug? slug)
                               user (:user ctx)
                               org (or (:existing-org ctx) (org-res/get-org conn slug))
                               boards-by-uuid (user-boards-by-uuid conn user org)]
                        {:existing-org (api-common/rep org)
                         :boards-by-uuid (api-common/rep boards-by-uuid)}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (activity-response conn ctx)))

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
  :malformed? (fn [ctx] (let [ctx-params (-> ctx :request :params)
                              start (:start ctx-params)
                              valid-start? (common/sort-value? start)
                              direction (keyword (:direction ctx-params))
                              ;; no direction is OK, but if specified it's from the allowed enumeration of options
                              valid-direction? (if direction (#{:before :after} direction) true)
                              ;; a specified start/direction must be together or ommitted
                              pairing-allowed? (or (and start direction)
                                                    (and (not start) (not direction)))]
                           (not (and valid-start? valid-direction? pairing-allowed?))))

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slug? (slugify/valid-slug? slug)
                               user (:user ctx)
                               org (or (:existing-org ctx) (org-res/get-org conn slug))
                               boards-by-uuid (user-boards-by-uuid conn user org)]
                        {:existing-org (api-common/rep org)
                         :boards-by-uuid (api-common/rep boards-by-uuid)}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (bookmarks-response conn ctx)))

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
  :malformed? (fn [ctx] (let [ctx-params (-> ctx :request :params)
                              start (:start ctx-params)
                              valid-start? (common/sort-value? start)
                              direction (keyword (:direction ctx-params))
                              ;; no direction is OK, but if specified it's from the allowed enumeration of options
                              valid-direction? (if direction (#{:before :after} direction) true)
                              ;; a specified start/direction must be together or ommitted
                              pairing-allowed? (or (and start direction)
                                                   (and (not start) (not direction)))]
                          (not (and valid-start? valid-direction? pairing-allowed?))))

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slug? (slugify/valid-slug? slug)
                               user (:user ctx)
                               org (or (:existing-org ctx) (org-res/get-org conn slug))
                               boards-by-uuid (user-boards-by-uuid conn user org)]
                        {:existing-org (api-common/rep org)
                         :boards-by-uuid (api-common/rep boards-by-uuid)}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (replies-response conn ctx)))

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
  :malformed? (fn [ctx] (let [ctx-params (-> ctx :request :params)
                              start (:start ctx-params)
                              valid-start? (common/sort-value? start)
                              direction (keyword (:direction ctx-params))
                              ;; no direction is OK, but if specified it's from the allowed enumeration of options
                              valid-direction? (if direction (#{:before :after} direction) true)
                              ;; a specified start/direction must be together or ommitted
                              pairing-allowed? (or (and start direction)
                                                   (and (not start) (not direction)))]
                          (not (and valid-start? valid-direction? pairing-allowed?))))

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slug? (slugify/valid-slug? slug)
                               user (:user ctx)
                               org (or (:existing-org ctx) (org-res/get-org conn slug))
                               boards-by-uuid (user-boards-by-uuid conn user org)]
                        {:existing-org (api-common/rep org)
                         :boards-by-uuid (api-common/rep boards-by-uuid)}
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (contributions-response conn ctx author-uuid)))

;; A resource to retrieve entries for a given user
(defresource label-entries [conn slug label]
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
  :malformed? (fn [ctx] (let [ctx-params (-> ctx :request :params)
                              start (:start ctx-params)
                              valid-start? (common/sort-value? start)
                              direction (keyword (:direction ctx-params))
                              ;; no direction is OK, but if specified it's from the allowed enumeration of options
                              valid-direction? (if direction (#{:before :after} direction) true)
                              ;; a specified start/direction must be together or ommitted
                              pairing-allowed? (or (and start direction)
                                                   (and (not start) (not direction)))]
                          (not (and valid-start? valid-direction? pairing-allowed?))))

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slug? (slugify/valid-slug? slug)
                               user (:user ctx)
                               org (or (:existing-org ctx) (org-res/get-org conn slug))
                               boards-by-uuid (user-boards-by-uuid conn user org)]
                        (let [label-by-uuid (label-res/get-label conn label)
                              ;; Try to retrive by label uuid
                              existing-label (or label-by-uuid (label-res/get-label conn (:uuid org) label))]
                          {:existing-org (api-common/rep org)
                           :boards-by-uuid (api-common/rep boards-by-uuid)
                           :existing-label (api-common/rep existing-label)
                           :label-by-uuid (api-common/rep (boolean label-by-uuid))})
                        false))

  ;; Responses
  :handle-ok (fn [ctx] (label-response conn ctx label)))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; All activity operations
      (OPTIONS (org-urls/entries ":slug") [slug] (pool/with-pool [conn db-pool] (activity conn slug)))
      (OPTIONS (str (org-urls/entries ":slug") "/") [slug] (pool/with-pool [conn db-pool] (activity conn slug)))
      (GET (org-urls/entries ":slug") [slug] (pool/with-pool [conn db-pool] (activity conn slug)))
      (GET (str (org-urls/entries ":slug") "/") [slug] (pool/with-pool [conn db-pool] (activity conn slug)))

      (OPTIONS (org-urls/bookmarks ":slug") [slug] (pool/with-pool [conn db-pool] (bookmarks conn slug)))
      (OPTIONS (str (org-urls/bookmarks ":slug") "/") [slug] (pool/with-pool [conn db-pool] (bookmarks conn slug)))
      (GET (org-urls/bookmarks ":slug") [slug] (pool/with-pool [conn db-pool] (bookmarks conn slug)))
      (GET (str (org-urls/bookmarks ":slug") "/") [slug] (pool/with-pool [conn db-pool] (bookmarks conn slug)))

      (OPTIONS (org-urls/replies ":slug") [slug] (pool/with-pool [conn db-pool] (replies conn slug)))
      (OPTIONS (str (org-urls/replies ":slug") "/") [slug] (pool/with-pool [conn db-pool] (replies conn slug)))
      (GET (org-urls/replies ":slug") [slug] (pool/with-pool [conn db-pool] (replies conn slug)))
      (GET (str (org-urls/replies ":slug") "/") [slug] (pool/with-pool [conn db-pool] (replies conn slug)))

      (OPTIONS (org-urls/contribution ":slug" ":author-uuid")
        [slug author-uuid] (pool/with-pool [conn db-pool] (contributions conn slug author-uuid)))
      (OPTIONS (str (org-urls/contribution ":slug" ":author-uuid") "/")
        [slug author-uuid] (pool/with-pool [conn db-pool] (contributions conn slug author-uuid)))
      (GET (org-urls/contribution ":slug" ":author-uuid")
        [slug author-uuid] (pool/with-pool [conn db-pool] (contributions conn slug author-uuid)))
      (GET (str (org-urls/contribution ":slug" ":author-uuid") "/")
        [slug author-uuid] (pool/with-pool [conn db-pool] (contributions conn slug author-uuid)))
      ;; Labels
      (OPTIONS (label-urls/label-entries ":slug" ":label")
        [slug label] (pool/with-pool [conn db-pool] (label-entries conn slug label)))
      (OPTIONS (str (label-urls/label-entries ":slug" ":label") "/")
        [slug label] (pool/with-pool [conn db-pool] (label-entries conn slug label)))
      (GET (label-urls/label-entries ":slug" ":label")
        [slug label] (pool/with-pool [conn db-pool] (label-entries conn slug label)))
      (GET (str (label-urls/label-entries ":slug" ":label") "/")
        [slug label] (pool/with-pool [conn db-pool] (label-entries conn slug label))))))