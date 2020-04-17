(ns oc.storage.api.orgs
  "Liberator API for org resources."
  (:require [clojure.walk :refer (keywordize-keys)]
            [if-let.core :refer (if-let*)]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (ANY OPTIONS GET POST DELETE)]
            [liberator.core :refer (defresource by-method)]
            [schema.core :as schema]
            [oc.lib.time :as lib-time]
            [oc.lib.schema :as lib-schema]
            [oc.lib.slugify :as slugify]
            [oc.lib.db.pool :as pool]
            [oc.lib.db.common :as db-common]
            [oc.lib.api.common :as api-common]
            [oc.lib.change.resources.follow :as follow]
            [oc.storage.config :as config]
            [oc.storage.async.notification :as notification]
            [oc.storage.api.access :as access]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.representations.board :as board-rep]
            [oc.storage.resources.common :as common-res]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.entry :as entry-res]))

;; ----- Utility functions -----

(def org-name-min-length 3)
(def org-name-max-length 50)

(defn- board-with-access-level
  "
  Merge in `access` level user is accessing this board with, and if that level is public, remove author and
  viewer lists.
  "
  [org board user]
  (let [level (access/access-level-for org board user)
        public? (= :public (:access-level level))
        clean-board (if public? (dissoc board :authors :viewers) board)]
    (if level (merge clean-board level) clean-board)))

(defn- default-entries-for
  "Return any sample posts for a specific board slug."
  [board-name]
  (try
    (->> board-name
      (slugify/slugify)
      (format "samples/%s.edn")
      (clojure.java.io/resource)
      (slurp)
      (read-string))
    (catch Exception e
      [])))

(defn- create-interaction
  "Create any default interactions (from config) for a new default entry."
  [conn org-uuid interaction resource]
  (let [ts (f/unparse lib-time/timestamp-format (t/minus (t/now) (t/minutes (or (:time-offset interaction) 0))))
        content-key (if (:body interaction) :body :reaction)]
    (db-common/create-resource conn common-res/interaction-table-name {
      :uuid (db-common/unique-id)
      content-key (content-key interaction)
      :board-uuid (:board-uuid resource)
      :org-uuid org-uuid
      :resource-uuid (:uuid resource)
      :author (:author interaction)} ts)))

(defn- create-entry
  "Create any default entries (from config) for a new default board."
  [conn org entry user]
  (timbre/info "Creating sample entry:" (:headline entry) "for board:" (:board-slug entry) "of org:" (:uuid org))
  (let [ts (f/unparse lib-time/timestamp-format (t/minus (t/now) (t/minutes (or (:time-offset entry) 0))))
        board (board-res/get-board conn (:uuid org) (:board-slug entry))
        entry-res (db-common/create-resource conn common-res/entry-table-name 
                    (-> (entry-res/->entry conn (:uuid board)
                                                (dissoc entry :board-slug :author :time-offset :comments :reactions)
                                                (:author entry))
                      (assoc :sample true)
                      (assoc :status :published)
                      (assoc :created-at ts)
                      (assoc :updated-at ts)
                      (assoc :published-at ts)
                      (assoc :publisher (:author entry))) ts)
        interaction-resource (merge entry entry-res)]
    ;; notify of new entry
    (notification/send-trigger! (notification/->trigger :add org board {:new entry-res} user nil))
    ;; create any entry interactions (comments and/or reactions) in parallel
    (doall (pmap #(create-interaction conn (:org-uuid board) % interaction-resource)
              (concat (:comments interaction-resource) (:reactions interaction-resource))))))

(defn- create-board
  "Create a boards for a new org."
  [conn org board author]
  (board-res/create-board! conn (board-res/->board (:uuid org) (assoc board :entries []) author)))
 
;; ----- Actions -----

(defn create-org [conn ctx]
  (timbre/info "Creating org.")
  (if-let* [new-org (:new-org ctx)
            org-result (org-res/create-org! conn new-org)] ; Add the org

    ;; Org creation succeeded, so create the default boards
    (let [uuid (:uuid org-result)
          author (:user ctx)
          sample-entries (flatten (map default-entries-for config/new-org-board-names))]
      (timbre/info "Created org:" uuid)
      (notification/send-trigger! (notification/->trigger :add {:new org-result} (:user ctx)))
      (timbre/info "Creating default boards for org:" uuid)
      (doseq [board (map #(hash-map :name %) config/new-org-board-names)]
        (create-board conn org-result board author))
      (timbre/info "Creating default entries for org:" uuid)
      (doseq [entry sample-entries]
        (create-entry conn org-result entry author))
      {:created-org (api-common/rep org-result)})
      
    (do (timbre/error "Failed creating org.") false)))
      
(defn- update-org [conn ctx slug]
  (timbre/info "Updating org:" slug)
  (if-let* [author (:user ctx)
            updated-org (:updated-org ctx)
            update-result (org-res/update-org! conn slug updated-org)
            org-uuid (:uuid updated-org)]
    (do
      (timbre/info "Updated org:" slug)
      (notification/send-trigger! (notification/->trigger :update {:old (:existing-org ctx) :new update-result}
                                                          author))
      {:updated-org (api-common/rep update-result)})

    (do (timbre/error "Failed updating org:" slug) false)))

(defn- add-author [conn ctx slug user-id]
  (timbre/info "Adding author:" user-id "to org:" slug)
  (if-let [updated-org (org-res/add-author conn slug user-id)]
    (do
      (timbre/info "Added author:" user-id "to org:" slug)
      {:updated-org (api-common/rep updated-org)})
    
    (do
      (timbre/error "Failed adding author:" user-id "to org:" slug)
      false)))

(defn- remove-author [conn ctx slug user-id]
  (timbre/info "Removing author:" user-id "from org:" slug)
  (if-let [updated-org (org-res/remove-author conn slug user-id)]
    (do
      (timbre/info "Removed author:" user-id "from org:" slug)
      {:updated-org (api-common/rep updated-org)})
    
    (do
      (timbre/error "Failed removing author:" user-id "to org:" slug)
      false)))

;; ----- Validations -----

(defn- is-first-org? [conn user]
  {:pre [(db-common/conn? conn)]}
  (let [authed-orgs (org-res/list-orgs-by-teams conn (:teams user))]
    (zero? (count authed-orgs))))

(defn- valid-new-org? [conn ctx]
  (try
    ;; Create the new org from the data provided
    (let [org-map (:data ctx)
          org-name (:name org-map)
          author (:user ctx)]
      (if (and (<= (count org-name) org-name-max-length)
               (>= (count org-name) org-name-min-length))
        {:new-org (api-common/rep (org-res/->org org-map author))}
        [false, {:reason (str "Org name length. Allowed length is " org-name-min-length
                              " to " org-name-max-length ".")}]))

    (catch clojure.lang.ExceptionInfo e
      [false, {:reason (.getMessage e)}]))) ; Not a valid new org

(defn- valid-org-update? [conn slug org-props]
  (if-let [org (org-res/get-org conn slug)]
    (let [updated-org (merge org (org-res/ignore-props org-props))
          updating-org-name? (contains? org-props :name)
          org-name (:name org-props)]
      (if (and (lib-schema/valid? common-res/Org updated-org)
               (or (not updating-org-name?)
                   (and updating-org-name?
                        (<= (count org-name) org-name-max-length)
                        (>= (count org-name) org-name-min-length))))
        {:existing-org (api-common/rep org) :updated-org (api-common/rep updated-org)
         :boards (api-common/rep (:boards org-props))}
        [false, {:updated-org (api-common/rep updated-org)}])) ; invalid update
    true)) ; No org for this slug, so this will fail existence check later

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for operations on a particular Org
(defresource org [conn slug]
  (api-common/open-company-anonymous-resource config/passphrase) ; verify validity of optional JWToken

  :allowed-methods [:options :get :patch]

  ;; Media type client accepts
  :available-media-types [mt/org-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/org-media-type)

  ;; Media type client sends
  :known-content-type? (by-method {
    :options true
    :get true
    :patch (fn [ctx] (api-common/known-content-type? ctx mt/org-media-type))})
  
  ;; Authorization
  :allowed? (by-method {
    :options true
    :get (fn [ctx] (access/access-level-for conn slug (:user ctx)))
    :patch (fn [ctx] (access/allow-authors conn slug (:user ctx)))})

  ;; Validations
  :processable? (by-method {
    :options true
    :get true
    :patch (fn [ctx] (and (slugify/valid-slug? slug) (valid-org-update? conn slug (:data ctx))))})

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slug? (slugify/valid-slug? slug)
                               org (or (:existing-org ctx) (org-res/get-org conn slug))]
                        {:existing-org (api-common/rep org)}
                        false))

  ;; Actions
  :patch! (fn [ctx] (update-org conn ctx slug))

  ;; Responses
  :handle-ok (fn [ctx] (let [user (:user ctx)
                             user-id (:user-id user)
                             org (or (:updated-org ctx) (:existing-org ctx))
                             user-is-member? (and (not (:id-token user))
                                                  (or (= (:access-level ctx) :author)
                                                      (= (:access-level ctx) :viewer)))
                             org-id (:uuid org)
                             boards (board-res/list-boards-by-org conn org-id [:created-at :updated-at :authors :viewers :access :publisher-board :author])
                             board-access (map #(board-with-access-level org % user) boards)
                             allowed-boards (filter :access-level board-access)
                             author-access-boards (filter #(= (:access-level %) :author) board-access)
                                               ;; Add the draft board
                             show-draft-board? (and ;; if user is logged in and
                                                    (seq user-id)
                                                    ;; or is an author of the org
                                                    (or (access/allow-authors conn slug user)
                                                        ;; or has at least one board with author access
                                                        (pos? (count author-access-boards))))
                             draft-entry-count (if show-draft-board? (entry-res/list-drafts-by-org-author conn org-id user-id {:count true}) 0)
                             bookmarks-count (if user-is-member?
                                              (entry-res/list-all-bookmarked-entries conn org-id user-id :asc (db-common/current-timestamp) :before
                                               0 {:count true})
                                              0)
                             following-data (when user-is-member?
                                              (follow/retrieve config/dynamodb-opts user-id (:slug org)))
                             following-count (if user-is-member?
                                               (entry-res/paginated-entries-by-org conn org-id :asc (db-common/current-timestamp) :before 0 :recent-activity
                                                (map :uuid allowed-boards) following-data {:count true})
                                               0)
                             following-inbox-count (if user-is-member?
                                           (entry-res/list-all-entries-for-inbox conn org-id user-id :asc (db-common/current-timestamp)
                                            0 (map :uuid allowed-boards) following-data {:count true})
                                           0)
                             inbox-count (if user-is-member?
                                           (entry-res/list-all-entries-for-inbox conn org-id user-id :asc (db-common/current-timestamp)
                                            0 (map :uuid allowed-boards) nil {:count true})
                                           0)
                             user-count (if user-is-member?
                                          (entry-res/list-entries-by-org-author conn org-id user-id :asc (db-common/current-timestamp) :before
                                            0 :recently-posted (map :uuid allowed-boards) {:count true})
                                           0)
                             full-boards (if show-draft-board?
                                            (conj allowed-boards (board-res/drafts-board org-id user))
                                            allowed-boards)
                             board-reps (map #(board-rep/render-board-for-collection slug % draft-entry-count)
                                          full-boards)
                             authors (:authors org)
                             author-reps (map #(org-rep/render-author-for-collection org % (:access-level ctx)) authors)
                             has-sample-content? (> (entry-res/sample-entries-count conn org-id) 1)]
                         (org-rep/render-org (-> org
                                                 (assoc :boards (if user-is-member?
                                                                  board-reps
                                                                  (map #(dissoc % :authors :viewers) board-reps)))
                                                 (assoc :bookmarks-count bookmarks-count)
                                                 (assoc :following-count following-count)
                                                 (assoc :inbox-count inbox-count)
                                                 (assoc :following-inbox-count inbox-count)
                                                 (assoc :contributions-count user-count)
                                                 (assoc :authors author-reps))
                                             (:access-level ctx)
                                             user
                                             has-sample-content?)))
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (schema/check common-res/Org (:updated-org ctx)))))

;; A resource for the authors of a particular org
(defresource author [conn org-slug user-id]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :post :delete]

  ;; Media type client accepts
  :available-media-types [mt/org-author-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/org-author-media-type)

  ;; Media type client sends
  :malformed? (by-method {
    :options false
    :post (fn [ctx] (access/malformed-user-id? ctx))
    :delete false})
  :known-content-type? (by-method {
    :options true
    :post (fn [ctx] (api-common/known-content-type? ctx mt/org-author-media-type))
    :delete true})

  ;; Authorization
  :allowed? (by-method {
    :options true
    :post (fn [ctx] (access/allow-authors conn org-slug (:user ctx)))
    :delete (fn [ctx] (access/allow-authors conn org-slug (:user ctx)))})

  ;; Existentialism
  :exists? (by-method {
    :post (fn [ctx] (if-let [org (and (slugify/valid-slug? org-slug) (org-res/get-org conn org-slug))]
                        {:existing-org (api-common/rep org)
                         :existing-author? (api-common/rep ((set (:authors org)) (:data ctx)))}
                        false))
    :delete (fn [ctx] (if-let* [org (and (slugify/valid-slug? org-slug) (org-res/get-org conn org-slug))
                                exists? ((set (:authors org)) user-id)] ; short circuits the delete w/ a 404
                        {:existing-org (api-common/rep org) :existing-author? true}
                        false))}) ; org or author doesn't exist

  ;; Actions
  :post! (fn [ctx] (when-not (:existing-author? ctx) (add-author conn ctx org-slug (:data ctx))))
  :delete! (fn [ctx] (when (:existing-author? ctx) (remove-author conn ctx org-slug user-id)))
  
  ;; Responses
  :respond-with-entity? false
  :handle-created (fn [ctx] (if (:existing-org ctx)
                              (api-common/blank-response)
                              (api-common/missing-response)))
  :handle-no-content (fn [ctx] (when-not (:updated-org ctx) (api-common/missing-response)))
  :handle-options (if user-id
                    (api-common/options-response [:options :delete])
                    (api-common/options-response [:options :post])))


;; A resource for operations on a list of orgs
(defresource org-list [conn]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get :post]

  ;; Media type client accepts
  :available-media-types [mt/org-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/org-media-type)
  
  ;; Media type client sends
  :known-content-type? (by-method {
                          :options true
                          :get true
                          :post (fn [ctx] (api-common/known-content-type? ctx mt/org-media-type))})

  ;; Authorization
  :allowed? (by-method {
    :options true
    :get (fn [ctx] (if-let* [team-id (:team-id ctx)
                             teams (-> ctx :user :teams)
                             _member? (.contains teams team-id)]
                      true))
    :post (fn [ctx] (access/allow-team-admins-or-no-org
                      conn (:user ctx)))}) ; don't allow non-team-admins to get stuck w/ no org

  ;; Validations
  :malformed? (by-method {
    :options false
    :get (fn [ctx] (if-let* [ctx-params (keywordize-keys (-> ctx :request :params))
                             team-id (:team-id ctx-params) ; org lookup is by team-id
                             _team-id? (lib-schema/unique-id? team-id)]
                     [false {:team-id team-id}]
                     true))
    :post (fn [ctx] (api-common/malformed-json? ctx))})
  :processable? (by-method {
    :options true
    :get true
    :post (fn [ctx] (valid-new-org? conn ctx))})

  :conflict? (fn [ctx] (not (is-first-org? conn (:user ctx))))

  ;; Existentialism
  :exists? (by-method {
             :get (fn [ctx] (if-let* [team-id (:team-id ctx)
                                      orgs (org-res/list-orgs-by-team conn team-id [:logo-url :logo-width :logo-height])]
                              (if (empty? orgs) false {:existing-orgs (api-common/rep orgs)})
                              false))})

  ;; Actions
  :post! (fn [ctx] (create-org conn ctx))

  ;; Responses
  :handle-created (fn [ctx] (let [new-org (:created-org ctx)
                                  slug (:slug new-org)
                                  org-id (:uuid new-org)
                                  user-id (-> ctx :user :user-id)
                                  boards (board-res/list-boards-by-org conn org-id [:created-at :updated-at :publisher-board])
                                  board-reps (map #(board-rep/render-board-for-collection slug % 0)
                                                (map #(assoc % :access-level :author) boards))
                                  author-reps [(org-rep/render-author-for-collection new-org user-id :author)]
                                  org-for-rep (-> new-org
                                                (assoc :authors author-reps)
                                                (assoc :boards (map #(dissoc % :authors :viewers) board-reps)))
                                  has-sample-content? (> (entry-res/sample-entries-count conn org-id) 1)]
                              (api-common/location-response
                                (org-rep/url slug)
                                (org-rep/render-org org-for-rep :author (:user ctx) has-sample-content?)
                                  mt/org-media-type)))
  :handle-ok (fn [ctx] (let [existing-orgs (:existing-orgs ctx)]
                         (org-rep/render-org-list existing-orgs (:user ctx))))
  :handle-unprocessable-entity (fn [ctx]
    (api-common/unprocessable-entity-response (:reason ctx))))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Org creation and lookup
      (ANY "/orgs" [] (pool/with-pool [conn db-pool] (org-list conn)))
      (ANY "/orgs/" [] (pool/with-pool [conn db-pool] (org-list conn)))
      ;; Org operations
      (ANY "/orgs/:slug" [slug] (pool/with-pool [conn db-pool] (org conn slug)))
      (ANY "/orgs/:slug/" [slug] (pool/with-pool [conn db-pool] (org conn slug)))
      ;; Org author operations
      (OPTIONS "/orgs/:slug/authors" [slug] (pool/with-pool [conn db-pool] (author conn slug nil)))
      (OPTIONS "/orgs/:slug/authors/" [slug] (pool/with-pool [conn db-pool] (author conn slug nil)))
      (POST "/orgs/:slug/authors" [slug] (pool/with-pool [conn db-pool] (author conn slug nil)))
      (POST "/orgs/:slug/authors/" [slug] (pool/with-pool [conn db-pool] (author conn slug nil)))
      (OPTIONS "/orgs/:slug/authors/:user-id" [slug user-id] (pool/with-pool [conn db-pool]
        (author conn slug user-id)))
      (OPTIONS "/orgs/:slug/authors/:user-id/" [slug user-id]
        (pool/with-pool [conn db-pool] (author conn slug user-id)))
      (DELETE "/orgs/:slug/authors/:user-id" [slug user-id]
        (pool/with-pool [conn db-pool] (author conn slug user-id)))
      (DELETE "/orgs/:slug/authors/:user-id/" [slug user-id] (pool/with-pool [conn db-pool]
        (author conn slug user-id))))))