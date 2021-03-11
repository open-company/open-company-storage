(ns oc.storage.resources.label
  (:require [oc.lib.schema :as lib-schema]
            [clojure.set :as clj-set]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [oc.storage.resources.common :as common]
            [oc.lib.slugify :as slug]
            [oc.lib.db.common :as db-common]))

(def table-name common/label-table-name)
(def primary-key :uuid)

(def reserved-properties
  (clj-set/union common/reserved-properties #{:used-by}))

(def reserved-slugs #{})

(declare list-labels-by-org)
(defn taken-slugs
  "Return all org slugs which are in use as a set."
  [conn org-uuid]
  {:pre [(db-common/conn? conn)]}
  (into reserved-slugs (map :slug (list-labels-by-org conn org-uuid))))

(def ignored-properties
  "Properties of a resource that are ignored during an update."
  (clj-set/union reserved-properties #{:team-id :utm-data}))

(defn ignore-props
  "Remove any ignored properties from the org."
  [label]
  (apply dissoc label ignored-properties))

(schema/defn ^:always-validate get-label :- (schema/maybe common/Label)
  "Given the slug of the label, return the label object, or return nil if it doesn't exist."
  ([conn :- lib-schema/Conn label-uuid :- lib-schema/UniqueID]
   (db-common/read-resource conn table-name label-uuid))

  ([conn :- lib-schema/Conn org-uuid :- lib-schema/UniqueID label-slug :- common/Slug]
   (first (db-common/read-resources conn table-name :org-label [org-uuid label-slug]))))

(defn slug-available?
  "Return true if the slug is not used by any org in the system."
  [conn slug org-uuid]
  {:pre [(db-common/conn? conn)
         (slug/valid-slug? slug)
         (lib-schema/unique-id? org-uuid)]}
  (not (contains? (taken-slugs conn org-uuid) slug)))

(defn label-slug-for-org [conn org-uuid label-name]
  (slug/find-available-slug label-name (taken-slugs conn org-uuid)))

(schema/defn ^:always-validate ->label :- common/Label
  ([label-map org user]
   (->label (:name label-map) (:color label-map) (:uuid org) (lib-schema/author-for-user user)))
  ([label-name :- common/LabelName
    label-color :- lib-schema/HEXColor
    org-uuid :- lib-schema/UniqueID
    author :- lib-schema/Author]
   (let [ts (db-common/current-timestamp)]
     {primary-key (db-common/unique-id)
      :created-at ts
      :updated-at ts
      :org-uuid org-uuid
      :name label-name
      :slug (slug/slugify label-name) ;; Will be adjusted later during save
      :color label-color
      :author author
      :used-by [{:user-id (:user-id author) :count 0}]})))

(schema/defn ^:always-validate create-label!
  "
  Create an org in the system. Throws a runtime exception if the org doesn't conform to the common/Org schema.

  Check the slug in the response as it may change if there is a conflict with another org.
  "
  [conn :- lib-schema/Conn label :- common/Label]
  (timbre/infof "Creating label %s with color %s" (:name label) (:color label))
  (db-common/create-resource conn table-name (assoc label :slug (label-slug-for-org conn (:org-uuid label) (:name label)))
                             (db-common/current-timestamp)))

(schema/defn ^:always-validate update-label! :- (schema/maybe common/Label)
  "
  Given the UUID of a label and a map containing the update, apply the changes to the object and return it on success.

  Throws an exception if the merge doesn't conform to the common/Label schema.
  "
  [conn :- lib-schema/Conn label-uuid :- common/Slug updating-label :- {schema/Keyword schema/Any}]
  (when-let [original-label (get-label conn label-uuid)]
    (let [updated-label (merge original-label (ignore-props updating-label))]
      (schema/validate common/Label updated-label)
      (db-common/update-resource conn table-name primary-key label-uuid updated-label))))

(schema/defn ^:always-validate list-labels-by-org :- [common/Label]
  [conn :- lib-schema/Conn org-uuid :- lib-schema/UniqueID]
  (db-common/read-resources conn table-name :org-uuid org-uuid))

(schema/defn ^:always-validate delete-label!
  "Given the slug of the label, delete it."
  [conn :- lib-schema/Conn label-uuid :- lib-schema/UniqueID]
  (timbre/infof "Deleting label %s" label-uuid)
  (db-common/delete-resource conn table-name label-uuid))

(schema/defn ^:always-validate delete-org-labels! [conn :- lib-schema/Conn org-uuid :- lib-schema/UniqueID]
  (db-common/delete-resource conn table-name :org-uuid org-uuid))

(def UsedByUpdateStrategy (schema/enum :inc :dec))

(schema/defn ^:always-validate update-label-used-by! :- common/Label
  [conn :- lib-schema/Conn label-uuid :- lib-schema/UniqueID org-uuid :- lib-schema/UniqueID user :- lib-schema/User update-strategy :- UsedByUpdateStrategy]
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
  [conn :- lib-schema/Conn org-uuid :- lib-schema/UniqueID user-id :- lib-schema/UniqueID]
  (db-common/read-resources conn table-name :org-uuid-user-id-labels [org-uuid user-id]))