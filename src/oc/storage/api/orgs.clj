(ns oc.storage.api.orgs
  "Liberator API for org resources."
  (:require [if-let.core :refer (if-let*)]
            [clojure.java.io :as j-io]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.string :as string]
            [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (ANY OPTIONS POST DELETE)]
            [liberator.core :refer (defresource by-method)]
            [schema.core :as schema]
            [oc.lib.time :as lib-time]
            [oc.lib.user :as lib-user]
            [oc.lib.schema :as lib-schema]
            [oc.lib.slugify :as slugify]
            [oc.lib.db.pool :as pool]
            [oc.lib.db.common :as db-common]
            [oc.lib.api.common :as api-common]
            [oc.lib.auth :as auth]
            [oc.lib.change.resources.follow :as follow]
            [oc.lib.change.resources.seen :as seen]
            [oc.lib.change.resources.read :as read]
            [oc.storage.config :as config]
            [oc.storage.async.notification :as notification]
            [oc.storage.api.access :as access]
            [oc.storage.api.activity :as activity-api]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.representations.board :as board-rep]
            [oc.storage.resources.common :as common-res]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.resources.entry :as entry-res]
            [oc.storage.resources.label :as label-res]
            [oc.storage.urls.entry :as entry-urls]
            [oc.storage.resources.activity :as activity-res]
            [oc.storage.urls.org :as org-urls]))

(def org-board-props [:created-at :updated-at :authors :viewers :access :publisher-board :author :description :slack-mirror])

;; ----- Utility functions -----

(def org-name-min-length 3)
(def org-name-max-length 50)

(defn- default-entries-for
  "Return any sample posts for a specific board slug."
  [board-name]
  (try
    (->> board-name
      (slugify/slugify)
      (format "samples/%s.edn")
      (j-io/resource)
      (slurp)
      (read-string))
    (catch Exception _
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
        new-entry (db-common/create-resource conn common-res/entry-table-name 
                    (-> (entry-res/->entry conn (:uuid board)
                                                (dissoc entry :board-slug :author :time-offset :comments :reactions)
                                                (:author entry))
                      (assoc :sample true)
                      (assoc :status :published)
                      (assoc :created-at ts)
                      (assoc :updated-at ts)
                      (assoc :published-at ts)
                      (assoc :publisher (:author entry))) ts)
        interaction-resource (merge entry new-entry)]
    ;; notify of new entry
    (notification/send-trigger! (notification/->trigger :add org board {:new new-entry} user nil))
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

(defn- add-author [conn _ctx slug user-id]
  (timbre/info "Adding author:" user-id "to org:" slug)
  (if-let [updated-org (org-res/add-author conn slug user-id)]
    (do
      (timbre/info "Added author:" user-id "to org:" slug)
      {:updated-org (api-common/rep updated-org)})
    
    (do
      (timbre/error "Failed adding author:" user-id "to org:" slug)
      false)))

(defn- remove-author [conn _ctx slug user-id]
  (timbre/info "Removing author:" user-id "from org:" slug)
  (if-let [updated-org (org-res/remove-author conn slug user-id)]
    (do
      (timbre/info "Removed author:" user-id "from org:" slug)
      {:updated-org (api-common/rep updated-org)})
    
    (do
      (timbre/error "Failed removing author:" user-id "to org:" slug)
      false)))

;; ---- Read data -----

(defn- sort-users [user-id users]
  (let [reduced-users (map #(select-keys % [:user-id :first-name :last-name :email :avatar-url :teams :admin]) users)
        {:keys [self-users other-users]} (group-by #(if (= (:user-id %) user-id)
                                                      :self-users
                                                      :other-users)
                                                   reduced-users)
        updated-other-users (mapv #(assoc %
                                          :sort-name (if (and (string/blank? (:first-name %))
                                                              (string/blank? (:last-name %)))
                                                       (str Character/MAX_VALUE)
                                                       (str (:first-name %) " " (:last-name %)))
                                          :name (lib-user/name-for-csv %))
                                  other-users)
        updated-self (as-> self-users u
                       (first u)
                       (assoc u :name (str (lib-user/name-for-csv u) " (you)")))]
    (vec (remove nil? (cons updated-self (vec updated-other-users))))))

(defn read-data-for-entries [org allowed-boards-map entries active-users user-id]
  (let [cleaned-users (sort-users user-id active-users)
        users-for-board-cache (atom {})
        csv-users-for-entry (fn [board entry]
                              (let [cached-users-list (get @users-for-board-cache (:uuid board))
                                    users-list (if (sequential? cached-users-list)
                                                 cached-users-list
                                                 (if (= (:access board) "private")
                                                   (filterv #(or ((set (:viewers board)) (:user-id %))
                                                                ((set (:authors board)) (:user-id %)))
                                                           cleaned-users)
                                                   cleaned-users))
                                    item-reads (read/retrieve-by-item config/dynamodb-opts (:uuid entry))
                                    item-reads-map (zipmap (map :user-id item-reads) item-reads)]
                                (when-not cached-users-list
                                  (swap! users-for-board-cache assoc (:uuid board) users-list))
                                (map #(assoc % :read-at
                                            (get-in item-reads-map [(:user-id %) :read-at]))
                                    users-list)))
        with-entries-data (map #(let [board (get allowed-boards-map (:board-uuid %))
                                      csv-users (csv-users-for-entry board %)
                                      users-count (count csv-users)
                                      reads-count (count (filter :read-at csv-users))]
                                  {:entry (assoc %
                                                 :board-slug (:slug board)
                                                 :board-name (:name board)
                                                 :board-access (:access board)
                                                 :url (entry-urls/ui-entry (:slug org) (:slug board) (:uuid %)))
                                   :csv-users (sort-by :sort-name csv-users)
                                   :reads-percent (when (pos? reads-count)
                                                    (str (format "%.2f" (float (* (/ reads-count users-count) 100))) "%"))
                                   :reads-count reads-count})
                                entries)]
    (sort-by :published-at with-entries-data)))

;; ----- Validations -----

(def -org-name-error (str "Error in org name length: allowed length is " org-name-min-length " to " org-name-max-length " characters"))

(defn- org-name-error [org-name]
  (format "%s, proposed value \"%s\" is %d" -org-name-error org-name (count org-name)))

(defn- valid-org-name? [org-name]
  (<= org-name-min-length (count org-name) org-name-max-length))

(defn- is-first-org? [conn user]
  {:pre [(db-common/conn? conn)]}
  (let [authed-orgs (org-res/list-orgs-by-teams conn (:teams user))]
    (zero? (count authed-orgs))))

(defn- valid-new-org? [_conn ctx]
  (try
    ;; Create the new org from the data provided
    (let [org-map (:data ctx)
          org-name (:name org-map)
          author (:user ctx)]
      (if (valid-org-name? org-name)
        {:new-org (api-common/rep (org-res/->org org-map author))}
        [false, {:reason (org-name-error org-name)}]))

    (catch clojure.lang.ExceptionInfo e
      [false, {:reason (.getMessage e)}]))) ; Not a valid new org

(defn- clean-brand-color [new-props ctx]
  (if (:premium? ctx)
    new-props
    (dissoc new-props :brand-color)))

(defn- valid-org-update? [conn slug ctx]
  (try
    (if-let [org (org-res/get-org conn slug)]
      (let [org-props (:data ctx)
            cleaned-org-props (-> org-props
                                  (org-res/ignore-props)
                                  (clean-brand-color ctx))
            updated-org (merge org cleaned-org-props)
            updating-org-name? (contains? org-props :name)
            org-name (:name org-props)]
        (cond (and updating-org-name?
                   (not (valid-org-name? org-name)))
              [false {:reason (org-name-error org-name)
                      :updated-org updated-org}]
              (not (lib-schema/valid? common-res/Org updated-org))
              [false {:reason (schema/check common-res/Org updated-org)
                      :updated-org updated-org}]
              :else
              {:existing-org (api-common/rep org) :updated-org (api-common/rep updated-org)
               :boards (api-common/rep (:boards org-props))}))
      true) ; No org for this slug, so this will fail existence check later
    (catch clojure.lang.ExceptionInfo e
      [false {:reason (.getMessage e)}])))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for operations on a particular Org
(defresource org-item [conn slug]
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
    :patch (fn [ctx] (and (slugify/valid-slug? slug) (valid-org-update? conn slug ctx)))})

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [_slug? (slugify/valid-slug? slug)
                               org (or (:existing-org ctx) (org-res/get-org conn slug))
                               existing-org-labels (or (:existing-labels ctx) (label-res/list-labels-by-org conn (:uuid org)))]
                       {:existing-org (api-common/rep org)
                        :existing-org-labels (api-common/rep existing-org-labels)}
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
                             boards-by-uuid (activity-api/user-boards-by-uuid conn user org org-board-props)
                             allowed-boards (map (fn [board]
                                                   (assoc board
                                                          :total-count (entry-res/list-entries-by-board conn board {:count true})
                                                          :last-entry-at (:published-at (entry-res/last-entry-of-board conn (:uuid board)))))
                                                 (vals boards-by-uuid))
                             contrib-boards (filter #(= (:access-level %) :author) allowed-boards)
                             ;; Add the draft board
                             show-draft-board? (and ;; if user is logged in and
                                                    (seq user-id)
                                                    ;; or is an author of the org
                                                    (or (access/allow-authors conn slug user)
                                                        ;; or has at least one board with author access
                                                        (seq contrib-boards)))
                             draft-entry-count (if show-draft-board?
                                                 (entry-res/list-drafts-by-org-author conn org-id user-id {:count true})
                                                 0)
                             bookmarks-count (if user-is-member?
                                              (activity-res/list-all-bookmarked-entries conn org-id user-id allowed-boards :desc nil :before
                                               0 {:count true})
                                              0)
                             follow-data (when user-is-member?
                                           (follow/retrieve config/dynamodb-opts user-id (:slug org)))
                             full-boards (if show-draft-board?
                                            (conj allowed-boards (board-res/drafts-board org-id user))
                                            allowed-boards)
                             following-seen (when user-is-member?
                                              (seen/retrieve-by-user-container config/dynamodb-opts user-id config/seen-home-container-id))
                             badge-following? (if user-is-member?
                                                (pos?
                                                 (activity-res/paginated-recently-posted-entries-by-org conn org-id :desc nil :before 0
                                                  allowed-boards follow-data (:seen-at following-seen) {:unseen true :count true}))
                                                false)
                             replies-seen (when user-is-member?
                                            (seen/retrieve-by-user-container config/dynamodb-opts user-id config/seen-replies-container-id))
                             badge-replies? (if user-is-member?
                                              (pos?
                                               (activity-res/list-entries-for-user-replies conn org-id allowed-boards user-id :desc
                                                nil :before 0 (:seen-at replies-seen) {:unseen true :count true}))
                                              false)
                             board-reps (map #(board-rep/render-board-for-collection slug % ctx draft-entry-count) full-boards)
                             authors (:authors org)
                             author-reps (map #(org-rep/render-author-for-collection org % (:access-level ctx)) authors)
                             has-sample-content? (> (entry-res/sample-entries-count conn org-id) 1)
                             wrt-posts-count (activity-res/list-latest-published-entries conn (:uuid org) allowed-boards config/default-csv-days {:count true})
                             org-map (-> org
                                         (assoc :boards (if user-is-member?
                                                          board-reps
                                                          (map #(dissoc % :authors :viewers) board-reps)))
                                         (assoc :bookmarks-count bookmarks-count)
                                         (assoc :wrt-posts-count wrt-posts-count)
                                         (assoc :badge-following badge-following?)
                                         (assoc :home-last-seen-at (:seen-at following-seen))
                                         (assoc :replies-last-seen-at (:seen-at replies-seen))
                                         (assoc :badge-replies badge-replies?)
                                         (assoc :authors author-reps))]
                         (org-rep/render-org org-map ctx has-sample-content?))))

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
    :delete (fn [_] (if-let* [org (and (slugify/valid-slug? org-slug) (org-res/get-org conn org-slug))
                              _exists? ((set (:authors org)) user-id)] ; short circuits the delete w/ a 404
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
    :get (fn [ctx] (if-let* [ctx-params (-> ctx :request :params)
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
                                      orgs (org-res/list-orgs-by-team conn team-id [:logo-url :logo-width :logo-height :brand-color])]
                              (if (empty? orgs) false {:existing-orgs (api-common/rep orgs)})
                              false))})

  ;; Actions
  :post! (fn [ctx] (create-org conn ctx))

  ;; Responses
  :handle-created (fn [ctx] (let [new-org (:created-org ctx)
                                  slug (:slug new-org)
                                  org-id (:uuid new-org)
                                  user-id (-> ctx :user :user-id)
                                  boards (board-res/list-boards-by-org conn org-id [:created-at :updated-at :publisher-board :access :slack-mirror])
                                  board-reps (->> boards
                                                  (map #(assoc % :access-level :author))
                                                  (map #(board-rep/render-board-for-collection slug % ctx 0)))
                                  author-reps [(org-rep/render-author-for-collection new-org user-id :author)]
                                  org-for-rep (-> new-org
                                                (assoc :authors author-reps)
                                                (assoc :boards (map #(dissoc % :authors :viewers) board-reps)))
                                  has-sample-content? (> (entry-res/sample-entries-count conn org-id) 1)]
                              (api-common/location-response
                                (org-urls/org slug)
                                (org-rep/render-org org-for-rep (assoc ctx :access-level :author) has-sample-content?)
                                  mt/org-media-type)))
  :handle-ok (fn [ctx] (let [existing-orgs (:existing-orgs ctx)]
                         (org-rep/render-org-list existing-orgs ctx))))

;; Download CSV

(defresource wrt-csv [conn org-slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get]

  ;; Media type client accepts
  :available-media-types [mt/csv-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/csv-media-type)

  ;; Authorization
  :allowed? (by-method {:options true
                        :get (fn [ctx] (access/allow-premium conn org-slug (:user ctx)))})

    ;; Validations
  :malformed? (by-method {:options false
                          :get (fn [ctx] (if-let* [ctx-params (-> ctx :request :params)
                                                   days (Integer/parseInt (get ctx-params :days (str config/default-csv-days))) ;; get number of days from request or fallback to the default number
                                                   _valid? (lib-schema/valid? schema/Num days)]
                                           [false {:days days}]
                                           true))})
    ;; Existentialism
  :exists? (by-method {:get (fn [ctx] (if-let* [user (:user ctx)
                                                org (org-res/get-org conn org-slug)
                                                boards-by-uuid (activity-api/user-boards-by-uuid conn user org org-board-props)
                                                entries (activity-res/list-latest-published-entries conn (:uuid org) (vals boards-by-uuid) (:days ctx))
                                                active-users (-> (:request ctx)
                                                                 (api-common/get-token)
                                                                 (auth/active-users config/auth-server-url (:team-id org))
                                                                 :collection
                                                                 :items)
                                                entries-with-reads (read-data-for-entries org boards-by-uuid entries active-users (:user-id user))]
                                        {:org (api-common/rep org)
                                         :active-users (api-common/rep active-users)
                                         :csv-entries (api-common/rep entries-with-reads)}
                                        false))})
  :handle-ok (fn [ctx] (let [org (:org ctx)
                             csv-entries (:csv-entries ctx)]
                        (org-rep/render-wrt-csv org csv-entries (:user ctx) (:days ctx)))))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Org creation and lookup
      (ANY org-urls/orgs [] (pool/with-pool [conn db-pool] (org-list conn)))
      (ANY (str org-urls/orgs "/") [] (pool/with-pool [conn db-pool] (org-list conn)))
      ;; Org operations
      (ANY (org-urls/org ":slug") [slug] (pool/with-pool [conn db-pool] (org-item conn slug)))
      (ANY (str (org-urls/org ":slug") "/") [slug] (pool/with-pool [conn db-pool] (org-item conn slug)))
      ;; Org author operations
      (OPTIONS (org-urls/org-authors ":slug") [slug] (pool/with-pool [conn db-pool] (author conn slug nil)))
      (OPTIONS (str (org-urls/org-authors ":slug") "/") [slug] (pool/with-pool [conn db-pool] (author conn slug nil)))
      (POST (org-urls/org-authors ":slug") [slug] (pool/with-pool [conn db-pool] (author conn slug nil)))
      (POST (str (org-urls/org-authors ":slug") "/") [slug] (pool/with-pool [conn db-pool] (author conn slug nil)))
      (OPTIONS (org-urls/org-author ":slug" ":user-id") [slug user-id]
        (pool/with-pool [conn db-pool] (author conn slug user-id)))
      (OPTIONS (str (org-urls/org-author ":slug" ":user-id") "/") [slug user-id]
        (pool/with-pool [conn db-pool] (author conn slug user-id)))
      (DELETE (org-urls/org-author ":slug" ":user-id") [slug user-id]
        (pool/with-pool [conn db-pool] (author conn slug user-id)))
      (DELETE (str (org-urls/org-author ":slug" ":user-id") "/")
        [slug user-id] (pool/with-pool [conn db-pool] (author conn slug user-id)))
      (ANY (org-urls/wrt-csv ":slug")
        [slug] (pool/with-pool [conn db-pool] (wrt-csv conn slug)))
      (ANY (str (org-urls/wrt-csv ":slug") "/")
        [slug] (pool/with-pool [conn db-pool] (wrt-csv conn slug))))))