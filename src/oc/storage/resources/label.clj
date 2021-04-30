(ns oc.storage.resources.label
  (:require [oc.lib.schema :as lib-schema]
            [oc.lib.html :as lib-html]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [oc.storage.resources.common :as common]
            [oc.lib.slugify :as slug]
            [oc.lib.db.common :as db-common]))

;; Table, props and primary key

(def table-name common/label-table-name)
(def primary-key :uuid)

(def reserved-slugs #{})

(def ignored-properties
  "Properties of a resource that are ignored during an update."
  common/reserved-properties)

(defn ignore-props
  "Remove any ignored properties from the org."
  [label]
  (apply dissoc label ignored-properties))

(defn clean-input [label]
  (as-> label l
    (if (:name l)
      (update l :name #(lib-html/strip-xss-tags (or % "")))
      l)))

;; Utilities

(declare list-labels-by-org)
(defn taken-slugs
  "Return all org slugs which are in use as a set."
  [conn org-uuid]
  {:pre [(db-common/conn? conn)]}
  (into reserved-slugs (map :slug (list-labels-by-org conn org-uuid))))

(defn- slug-available?
  "Return true if the slug is not used by any org in the system."
  [conn slug org-uuid]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? slug)
         (lib-schema/unique-id? org-uuid)]}
  (not (contains? (taken-slugs conn org-uuid) slug)))

(schema/defn entry-label :- (schema/maybe common/EntryLabel)
  [label :- (schema/maybe common/Entry)]
  (select-keys label [:uuid :name :slug]))

(schema/defn ^:always-validate get-label :- (schema/maybe common/Label)
  "Given the slug of the label, return the label object, or return nil if it doesn't exist."
  ([conn label-uuid]
   {:pre [(db-common/conn? conn)]}
   (db-common/read-resource conn table-name label-uuid))

  ([conn org-uuid :- lib-schema/UniqueID label-slug]
   {:pre [(db-common/conn? conn)]}
   (first (db-common/read-resources conn table-name :org-label [[org-uuid label-slug]]))))

(defn label-slug-for-org [conn org-uuid label-name]
  (slug/find-available-slug label-name (taken-slugs conn org-uuid)))

(schema/defn ^:always-validate ->label :- common/Label
  [label-name :- common/LabelName
   org-uuid :- lib-schema/UniqueID
   author :- lib-schema/Author]
  (let [ts (db-common/current-timestamp)]
    (-> {primary-key (db-common/unique-id)}
        (assoc :created-at ts)
        (assoc :updated-at ts)
        (assoc :org-uuid org-uuid)
        (assoc :name label-name)
        (clean-input)
        (assoc :slug (slug/slugify label-name)) ;; Will be adjusted later during save
        (assoc :author author)
        (assoc :used-by [{:user-id (:user-id author) :count 0}]))))

(schema/defn ^:always-validate create-label!
  "
  Create an org in the system. Throws a runtime exception if the org doesn't conform to the common/Org schema.

  Check the slug in the response as it may change if there is a conflict with another org.
  "
  [conn label :- common/Label]
  {:pre [(db-common/conn? conn)]}
  (timbre/infof "Creating label %s for org %s user %s" (:name label) (:org-uuid label) (-> label :author :user-id))
  (db-common/create-resource conn table-name (assoc label :slug (label-slug-for-org conn (:org-uuid label) (:name label)))
                             (db-common/current-timestamp)))

(schema/defn ^:always-validate update-label! :- (schema/maybe common/Label)
  "
  Given the UUID of a label and a map containing the update, apply the changes to the object and return it on success.

  Throws an exception if the merge doesn't conform to the common/Label schema.
  "
  [conn label-uuid :- common/Slug updating-label :- {schema/Keyword schema/Any}]
  {:pre [(db-common/conn? conn)]}
  (timbre/debugf "Updating label %s (%s) for org %s user %s" (:name updating-label) (:uuid updating-label) (:org-uuid updating-label) (-> updating-label :author :user-id))
  (when-let [original-label (get-label conn label-uuid)]
    (let [updated-label (->> updating-label
                             ignore-props
                             clean-input
                            (merge original-label))]
      (schema/validate common/Label updated-label)
      (db-common/update-resource conn table-name primary-key label-uuid updated-label))))

(schema/defn ^:always-validate list-labels-by-org :- [common/Label]
  [conn org-uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/read-resources conn table-name :org-uuid org-uuid))

(schema/defn ^:always-validate delete-label!
  "Given the slug of the label, delete it."
  [conn label-uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (timbre/infof "Deleting label %s" label-uuid)
  (db-common/delete-resource conn table-name label-uuid))

(schema/defn ^:always-validate delete-org-labels! [conn org-uuid :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/delete-resource conn table-name :org-uuid org-uuid))

(def UsedByUpdateStrategy (schema/enum :inc :dec))

(schema/defn ^:always-validate update-label-used-by! :- common/Label
  [conn label-uuid :- lib-schema/UniqueID org-uuid :- lib-schema/UniqueID user :- lib-schema/User update-strategy :- UsedByUpdateStrategy]
  {:pre [(db-common/conn? conn)]}
  (if-let [original-label (get-label conn label-uuid)]
    (do
      (timbre/debugf "Increment label %s use for org %s by user %s" label-uuid org-uuid (:user-id user))
      (let [found? (atom false)
            update-fn (if (= update-strategy :inc)
                        inc
                        dec)
            updated-used-by (update original-label
                                    :used-by (fn [used-by]
                                               (mapv (fn [{user-id :user-id use-count :count :or {use-count 0} :as used-by-row}]
                                                       (if (= user-id (:user-id user))
                                                         (let [next-count (max 0 (update-fn use-count))]
                                                           (reset! found? true)
                                                           {:user-id user-id :count next-count})
                                                         used-by-row))
                                                     used-by)))
            updated-label (if @found?
                            updated-used-by
                            (update original-label
                                    :used-by #(concat (vec %) [{:user-id (:user-id user) :count 1}])))]
        (db-common/update-resource conn table-name primary-key label-uuid updated-label)))
    (do
      (timbre/errorf "No label found for user %s and org %s." (:user-id user) org-uuid)
      (throw (ex-info "Invalid label uuid." {:label-uuid label-uuid :org-uuid org-uuid :user user})))))

(defn label-used-by! [conn label-uuid org-uuid user]
  (update-label-used-by! conn label-uuid org-uuid user :inc))

(defn label-unused-by! [conn label-uuid org-uuid user]
  (update-label-used-by! conn label-uuid org-uuid user :dec))

(defn labels-used-by! [conn label-uuids org-uuid user]
  (mapv #(update-label-used-by! conn % org-uuid user :inc) (vec label-uuids)))

(defn labels-unused-by! [conn label-uuids org-uuid user]
  (mapv #(update-label-used-by! conn % org-uuid user :dec) (vec label-uuids)))

(schema/defn ^:always-validate list-labels-by-org-user :- [common/Label]
  [conn org-uuid :- lib-schema/UniqueID user-id :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (db-common/read-resources conn table-name :org-uuid-user-id-labels [[org-uuid user-id]]))

(comment
  (require '[oc.storage.resources.org :as org])
  (require '[oc.storage.resources.entry :as entry])
  (require '[clojure.pprint :refer (pprint)])

  (def carrot (org/get-org conn "carrot"))
  (def author (:author carrot))
  (println (create-label! conn (->label "My label test" (:uuid carrot) author)))

  (def test-xss (create-label! conn (->label "<style>Test</style>out" (:uuid carrot) author)))
  (delete-label! conn (:uuid test-xss))

  (def test-label (get-label conn (:uuid carrot) "my-label-test"))

  (update-label! conn (:uuid test-label) {:name "My label test updated"})
  (:name (get-label conn (:uuid carrot) "my-label-test"))

  (pprint (list-labels-by-org conn (:uuid carrot)))


  (label-slug-for-org conn (:uuid carrot) "My label test") ;; => my-label-test-1

  (label-used-by! conn (:uuid test-label) (:uuid carrot) author)

  (def tmp-labels (list-labels-by-org-user conn (:uuid carrot) (:user-id author)))

  (labels-used-by! conn (mapv :uuid (take 2 tmp-labels)) (:uuid carrot) author)

  (labels-unused-by! conn (mapv :uuid (take 2 tmp-labels)) (:uuid carrot) author)

  (delete-label! conn (:uuid test-label))

  (get-label conn (:uuid carrot) "my-label-test"))