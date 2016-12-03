(ns open-company.resources.section
  (:require [clj-time.core :as t]
            [clj-time.format :as format]
            [defun.core :refer (defun defun-)]
            [open-company.config :as c]
            [open-company.resources.common :as common]
            [open-company.resources.company :as company]))

;; ----- RethinkDB metadata -----

(def table-name common/section-table-name)
(def primary-key :id)

;; ----- Metadata -----

(def reserved-properties
  "Properties of a section that can't be specified during a create and are ignored during an update."
  #{:id :company-slug :section-name})

;; ----- Utility functions -----

(defn- clean
  "Remove any reserved properties from the section."
  [section]
  (apply dissoc (common/clean section) reserved-properties))

(defun time-lte
  "
  Very specialized function to return the :id of the {:id :created-at} map to satisfy the `some`
  function if the timestamp strings are the same, or if the timestamp in the map (time1) is before
  timestamp2, otherwise return nil.
  "
  ([time1 :guard map? time2 :guard string?]
  (if (= (:created-at time1) time2)
    (:id time1)
    (time-lte time1 (format/parse common/timestamp-format time2))))

  ([time1 time2]
  (when (t/before? (format/parse common/timestamp-format (:created-at time1)) time2)
    (:id time1))))

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
  (common/read-resources-in-order conn table-name "company-slug-section-name" [company-slug section-name] [:created-at :author]))

(defn- list-revision-ids
  "Given the slug of the company, and a section name retrieve the timestamps and database id of the section revisions."
  [conn company-slug section-name]
  (common/read-resources-in-order conn table-name "company-slug-section-name" [company-slug section-name] [:created-at :id]))

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
        author (common/author-for-user user)
        original-section (get-section conn company-slug section-name) ; section before the update (if any)
        merged-section (merge original-section section) ; old section updated with the new
        completed-section (-> merged-section
          clean
          (common/complete-section company-slug section-name user)
          (dissoc :placeholder)
          (assoc :updated-at timestamp)) ; make sure the section has all the right properties
        original-sections (:sections original-company)
        updated-sections (if (some #{(name section-name)} (map name original-sections)) ; if section is in sections
                            original-sections ; already there
                            (conj original-sections section-name)) ; not there, add it
        sectioned-company (assoc original-company :sections updated-sections)
        updated-company (assoc sectioned-company section-name (-> completed-section
          (dissoc :placeholder)
          (dissoc :company-slug)
          (dissoc :section-name)))]
    (if (= (:org-id original-company) (:org-id user)) ; user is valid to do this update
      (do
        ;; update the company
        (company/update-company conn company-slug updated-company)
        ;; create a new section revision or update the latest section
        (common/create-resource conn table-name completed-section timestamp))
      false))))

(defn patch-revision [conn company-slug section-name timestamp revision user]
  "
  Given the company slug, section name, timestamp and section property map, update an exising section revision,
  returning the property map for the resource or `false`.
  "
  (let [original-company (company/get-company conn company-slug) ; company before the update
        author (common/author-for-user user)
        original-revision (get-section conn company-slug section-name timestamp) ; revision before the update
        merged-revision (merge original-revision revision) ; old revision updated with the new
        completed-revision (-> merged-revision
                               clean
                               (common/complete-section company-slug section-name user)
                               (dissoc :placeholder)
                               (assoc :updated-at timestamp)) ; make sure the section has all the right properties
        original-sections (:sections original-company)
        updated-sections (if (some #{(name section-name)} (map name original-sections)) ; if section is in sections
                            original-sections ; already there
                            (conj original-sections section-name)) ; not there, add it
        sectioned-company (assoc original-company :sections updated-sections)
        current-revision ((keyword section-name) original-company) ; most recent revision of this section
        updated-company (if (= (:created-at current-revision) timestamp) ; is the modified revision the current revision?
                          ;; update the section in the company too, because the modified revision is the current one
                          (assoc sectioned-company section-name 
                              (-> completed-revision
                                (dissoc :placeholder)
                                (dissoc :company-slug)
                                (dissoc :section-name)))
                          ;; no need to update the section in company
                          sectioned-company)]
    (println current-revision)
    (println (:created-at current-revision))
    (println timestamp)
    (if (= (:org-id original-company) (:org-id user)) ; user is valid to do this update
      (do
        ;; update the company
        (company/update-company conn company-slug updated-company)
        ;; update the section revision or
        (common/update-resource conn table-name primary-key original-revision completed-revision timestamp))
      false)))

;; ----- Armageddon -----

(defn delete-all-sections!
  "Use with caution! Failure can result in partial deletes of just some sections. Returns `true` if successful."
  [conn]
  (common/delete-all-resources! conn table-name))