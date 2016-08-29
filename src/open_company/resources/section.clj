(ns open-company.resources.section
  (:require [clj-time.core :as t]
            [clj-time.format :as format]
            [defun :refer (defun defun-)]
            [open-company.config :as c]
            [open-company.resources.common :as common]
            [open-company.resources.company :as company]))

;; ----- RethinkDB metadata -----

(def table-name common/section-table-name)
(def primary-key :id)

;; ----- Metadata -----

(def reserved-properties
  "Properties of a section that can't be specified during a create and are ignored during an update."
  #{:id :company-slug :section-name :body-placeholder :core})

(def custom-topic-body-placeholder "What would you like to say about this?")

(def initial-custom-properties 
  "Custom sections don't get initialized from a template, so need blank initial data."
  {
    :description "Custom topic"
    :headline ""
    :body custom-topic-body-placeholder
    :image-url nil
    :image-width 0
    :image-height 0
    :pin false
  })

;; ----- Utility functions -----

(defn- clean
  "Remove any reserved properties from the section."
  [section]
  (apply dissoc (common/clean section) reserved-properties))

(defun time-lte
  "
  Very specialized function to return the :id of the {:id :updated-at} map to satisfy the `some`
  function if the timestamp strings are the same, or if the timestamp in the map (time1) is before
  timestamp2, otherwise return nil.
  "
  ([time1 :guard map? time2 :guard string?]
  (if (= (:updated-at time1) time2)
    (:id time1)
    (time-lte time1 (format/parse common/timestamp-format time2))))

  ([time1 time2]
  (when (t/before? (format/parse common/timestamp-format (:updated-at time1)) time2)
    (:id time1))))

(defn- sections-with
  "Add the provided section name to the end of the correct category, unless it's already in the category."
  [sections section-name]
  (if ((set (map keyword (flatten (vals sections)))) (keyword section-name))
    sections ; already contains the section-name
    (let [category-name (or (common/category-for section-name) common/default-category)] ; category for this section
      (update-in sections [category-name] conj section-name)))) ; add the section name to the category

(defn- revision-time-gt?
  "Compare the section's original and updated `:updated-at`s, returning `true` if they
  are within bounds and false if they aren't."
  [[original-section updated-section]]
  (let [original-time (format/parse common/timestamp-format (:updated-at original-section))
        update-time (format/parse common/timestamp-format (:updated-at updated-section))
        time-limit (t/plus original-time (t/minutes c/collapse-edit-time))]
    ;; the section time difference is greater than the time limit gap allowed
    (not (t/within? (t/interval original-time time-limit) update-time))))

(defn- different-author?
  "Compare the section's original and updated authors' `:user-id`7s, returning `true` if they
  both match and false if they don't."
  [[original-section updated-section]]
  (not= (get-in original-section [:author :user-id])
        (get-in updated-section [:author :user-id])))

;; ----- Section revisions -----

(defn list-revisions
  "Given the slug of the company, and a section name retrieve the timestamps and author of the section revisions."
  [conn company-slug section-name]
  (common/read-resources-in-order conn table-name "company-slug-section-name" [company-slug section-name] [:updated-at :author]))

(defn- list-revision-ids
  "Given the slug of the company, and a section name retrieve the timestamps and database id of the section revisions."
  [conn company-slug section-name]
  (common/read-resources-in-order conn table-name "company-slug-section-name" [company-slug section-name] [:updated-at :id]))

(defn get-revisions
  "Given the slug of the company a section name, and an optional specific updated-at timestamp,
  retrieve the section revisions from the database."
  ([conn company-slug section-name]
  (common/updated-at-order
    (common/read-resources conn table-name "company-slug-section-name" [company-slug section-name])))

  ([conn slug section-name updated-at]
  (common/updated-at-order
    (common/read-resources conn table-name "company-slug-section-name-updated-at" [slug section-name updated-at]))))


(defun- revise-or-update
  ; this is the first time for the section, so create it
  ([conn [nil updated-section] timestamp]
    (common/create-resource conn table-name updated-section timestamp))

  ; it's been more than the allowed time since the last revision, so create a new revision
  ([conn [_original-section updated-section] :guard revision-time-gt? timestamp]
    (common/create-resource conn table-name updated-section timestamp))

  ; it's a different author than the last revision, so create a new revision
  ([conn [_original-section updated-section] :guard different-author? timestamp]
    (common/create-resource conn table-name updated-section timestamp))

  ; it's both the same author and less than the allowed time, so just update the current revision
  ([conn [original-section updated-section] timestamp]
    (common/update-resource conn table-name primary-key original-section updated-section timestamp)))

;; ----- Section CRUD -----

(defun get-section
  "
  Given the company slug, section name, and optional as-of timestamp of a section, retrieve the section from
  the database at the revision that matches the as-of timestamp or the most recent revision prior to that timestamp.
  Return nil if no section revision exists that satisfies this."
  ([conn company-slug section-name] (get-section conn company-slug section-name nil))

  ([conn company-slug section-name _as-of :guard nil?]
  ;; retrieve the id most recent revision of this section
  (if-let [id (:id (first (list-revision-ids conn company-slug section-name)))]
    ;; retrieve the section by its id
    (common/read-resource conn table-name id)))

  ([conn company-slug section-name as-of]
  ;; retrieve the id of the section revision that matches the as-of or is the first revision prior to it
  (if-let [id (some #(time-lte % as-of) (list-revision-ids conn company-slug section-name))]
    ;; retrieve the section by its id
    (common/read-resource conn table-name id))))

(defun put-section
  "
  Given the company slug, section name, and section property map, create a new section revision,
  updating the company with a new revision and returning the property map for the resource or `false`.
  "
  ([conn company-slug section-name section user]
  (put-section conn company-slug section-name section user (common/current-timestamp)))

  ;; invalid section or user
  ([conn _company-slug _section-name _section :guard #(not (map? %)) _user _timestamp] false)
  ([conn _company-slug _section-name _section _user :guard #(not (map? %)) _timestamp] false)

  ;; force section-name to a keyword
  ([conn company-slug section-name :guard #(not (keyword? %)) section user timestamp]
  (put-section conn company-slug (keyword section-name) section user timestamp))

  ([conn company-slug section-name section user timestamp]
  (let [original-company (company/get-company conn company-slug) ; company before the update
        original-section (get-section conn company-slug section-name) ; section before the update
        template-section (common/section-by-name section-name) ; canonical version of this section (unless custom)
        author (common/author-for-user user)
        updated-section (-> section
          (clean)
          (dissoc :placeholder)
          (assoc :company-slug company-slug)
          (assoc :section-name section-name)
          (assoc :author author)
          (assoc :updated-at timestamp)
          (assoc :pin (or (:pin section) (:pin original-section) (:pin template-section)))
          (assoc :description (:description template-section)) ; read-only property
          (assoc :body (or (:body section) (:body original-section) (:body template-section)))
          (assoc :body-placeholder (:body-placeholder template-section)) ; read-only property
          (assoc :headline (or (:headline section) (:headline original-section) (:headline template-section))))
        updated-sections (sections-with (:sections original-company) section-name)
        sectioned-company (assoc original-company :sections updated-sections)
        updated-company (assoc sectioned-company section-name (-> updated-section
          (dissoc :placeholder)
          (dissoc :company-slug)
          (dissoc :section-name)))]
    (if (= (:org-id original-company) (:org-id user)) ; user is valid to do this update
      (do
        ;; update the company
        (company/update-company conn company-slug updated-company)
        ;; create a new section revision or update the latest section
        (revise-or-update conn [original-section updated-section] timestamp))
      false))))

;; ----- Armageddon -----

(defn delete-all-sections!
  "Use with caution! Failure can result in partial deletes of just some sections. Returns `true` if successful."
  [conn]
  (common/delete-all-resources! conn table-name))