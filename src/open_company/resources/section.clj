(ns open-company.resources.section
  (:require [clj-time.core :as t]
            [clj-time.format :as format]
            [clojure.walk :refer (keywordize-keys)]
            [defun.core :refer (defun defun-)]
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

(defun- time-lte
  "
  Specialized function to return the :id of the {:id :created-at} map to satisfy the `some`
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

(defn- list-revision-ids
  "Given the slug of the company, and a section name retrieve the timestamps and database id of the section revisions."
  [conn company-slug section-name]
  (common/read-resources-in-order conn table-name "company-slug-section-name" [company-slug section-name] [:created-at :updated-at :id]))

;; ----- Section revisions -----

(defn list-revisions
  "Given the slug of the company, and a section name retrieve the timestamp and author of the section revisions."
  [conn company-slug section-name]
  (common/read-resources-in-order conn table-name "company-slug-section-name" [company-slug section-name] [:created-at :updated-at :author]))

(defn get-revisions
  "Given the slug of the company a section name, and an optional specific updated-at timestamp,
  retrieve the section revisions from the database."
  ([conn company-slug section-name]
  (common/updated-at-order
    (common/read-resources conn table-name "company-slug-section-name" [company-slug section-name])))

  ([conn slug section-name updated-at]
  (common/updated-at-order
    (common/read-resources conn table-name "company-slug-section-name-updated-at" [slug section-name updated-at]))))

(declare get-section)
(defn patch-revision
  "
  Given the company slug, section name, optional revision timestamp and an updated section property map, update an
  exising section revision, returning the property map for the resource or `false`.
  "
  ([conn company-slug section-name revision user]
  {:pre [(map? conn)
         (or (string? company-slug) (keyword? company-slug))
         (or (string? section-name) (keyword? section-name))
         (map? revision)
         (map? user)]}
  (if-let [original-section (get-section conn company-slug section-name)]
    (patch-revision conn company-slug section-name (:created-at original-section) revision user)
    false))
  
  ([conn company-slug section-name original-timestamp revision user]
  {:pre [(map? conn)
         (or (string? company-slug) (keyword? company-slug))
         (or (string? section-name) (keyword? section-name))
         (or (string? original-timestamp) (keyword? original-timestamp))
         (map? revision)
         (map? user)]}
  (let [original-company (company/get-company conn company-slug) ; company before the update
        original-revision (get-section conn company-slug section-name original-timestamp) ; revision before the update
        original-authorship (:author original-revision)
        merged-revision (merge (keywordize-keys original-revision) (keywordize-keys revision)) ; old revision updated with the new
        updated-at (common/current-timestamp)
        completed-revision (-> merged-revision
                               clean
                               (assoc :updated-at updated-at)
                               (assoc :author original-authorship)
                               (common/complete-section company-slug section-name user)
                               (dissoc :placeholder)) ; make sure the section has all the right properties
        original-sections (:sections original-company)
        updated-sections (if (some #{(name section-name)} (map name original-sections)) ; if section is in sections
                            original-sections ; already there
                            (conj original-sections (name section-name))) ; not there, add it
        sectioned-company (assoc original-company :sections updated-sections)
        current-revision ((keyword section-name) original-company) ; most recent revision of this section
        update-company? (= (:updated-at current-revision) original-timestamp) ; is the revision being modified the current revision?
        updated-company (if update-company?
                          ;; update the section in the company too, because the modified revision is the current one
                          (assoc sectioned-company (keyword section-name)
                              (-> completed-revision
                                (dissoc :placeholder)
                                (dissoc :company-slug)
                                (dissoc :section-name)))
                          ;; no need to update the section in company
                          sectioned-company)]
    (if (= (:org-id original-company) (:org-id user)) ; user is valid to do this update
      (do
        (when update-company? 
          ;; update the company
          (company/update-company conn company-slug updated-company))
        ;; update the section revision or
        (common/update-resource conn table-name primary-key original-revision completed-revision updated-at))
      false))))

(defn delete-revision
  "
  Given the company slug, section name, optional revision timestamp and an updated section property map, update an
  exising section revision, returning the property map for the resource or `false`.
  "
  [conn company-slug section-name original-timestamp user]
  {:pre [(map? conn)
         (or (string? company-slug) (keyword? company-slug))
         (or (string? section-name) (keyword? section-name))
         (or (string? original-timestamp) (keyword? original-timestamp))
         (map? user)]}
  (let [original-company (company/get-company conn company-slug) ; company before the update
        company-revision ((keyword section-name) original-company) ; current section in the company
        all-revisions (get-revisions conn company-slug section-name)
        filtered-revisions (sort #(compare (:created-at %2) (:created-at %1)) (filter #(not= (:created-at %) original-timestamp) all-revisions))
        update-sections? (zero? (count filtered-revisions)) ; are we removing the last revision of this section?
        update-company-section? (= (:updated-at company-revision) original-timestamp) ; are we removing the latest revision
        original-sections (:sections original-company)
        updated-sections (if update-sections?
                           ; if we are removing the last section, remove the section from the list of sections
                           (filter #(not= (name section-name) (name %)) original-sections)
                           original-sections)
        sectioned-company (assoc original-company :sections updated-sections)
        updated-company (if update-company-section?
                          ; if we are removing the latest revision, replace the section in company with the previous revision
                          (assoc sectioned-company (keyword section-name) (dissoc (first filtered-revisions) :placeholder :company-slug :section-name))
                          (when update-sections?
                            ; if we are removing the last revision remove the section from the company too
                            (dissoc sectioned-company section-name)))]
    (if (= (:org-id original-company) (:org-id user)) ; user is valid to do this update
      (do
        (when (or update-sections? update-company-section?)
          (company/update-company conn company-slug updated-company))
        (common/delete-resource conn common/section-table-name "company-slug-section-name-updated-at" [company-slug section-name original-timestamp]))
      false)))

;; ----- Section CRUD -----

(defun get-section
  "
  Given the company slug, section name, and an optional as-of timestamp of a section, retrieve the section from
  the database at the revision that matches the as-of timestamp, or the most recent revision prior to that timestamp,
  or the most recent revision if a timestamp is not provided.

  Return nil if no section revision exists that satisfies this.
  "
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
  ;; Get a timestamp for this section revision creation
  ([conn company-slug section-name section user]
  (put-section conn company-slug section-name section user (common/current-timestamp)))

  ;; It's an invalid section or user
  ([conn _company-slug _section-name _section :guard #(not (map? %)) _user _timestamp] false)
  ([conn _company-slug _section-name _section _user :guard #(not (map? %)) _timestamp] false)

  ;; force section-name to a keyword
  ([conn company-slug section-name :guard #(not (keyword? %)) section user timestamp]
  (put-section conn company-slug (keyword section-name) section user timestamp))

  ([conn company-slug section-name section user timestamp]
  (let [original-company (company/get-company conn company-slug) ; company before the update
        original-section (get-section conn company-slug section-name) ; section before the update (if any)
        completed-section (-> section
          clean
          (assoc :created-at timestamp)
          (assoc :updated-at timestamp)
          (common/complete-section company-slug section-name user)
          (dissoc :placeholder)) ; make sure the section has all the right properties
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