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
  "Properties of a resource that can't be specified during a create and are ignored during an update."
  #{:id :company-slug :section-name})

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

(defun update-notes-for

  ;; no notes in this section
  ([section :guard #(not (:notes %)) _original-section _author _timestamp]
  section) ; as you were

  ;; no notes should be in this section
  ([section :guard #(not (common/notes-sections (name (:section-name %)))) _original-section _author _timestamp]
  section) ; as you were

  ;; add author and timestamp to notes if notes' :body has been modified
  ([section original-section author timestamp]
  (if (= (get-in section [:notes :body]) (get-in original-section [:notes :body]))
    ;; use the original author and timestamp
    (-> section
      (assoc-in [:notes :author] (get-in original-section [:notes :author]))
      (assoc-in [:notes :updated-at] (get-in original-section [:notes :updated-at])))
    ;; use the new author and timestamp
    (-> section
      (assoc-in [:notes :author] author)
      (assoc-in [:notes :updated-at] timestamp)))))

(defn- revision-time-gt?
  "Compare the section's and notes original and updated `:updated-at`s, returning `true` if they
  are within bounds and false if they aren't."
  [[original-section updated-section]]
  (let [original-time (format/parse common/timestamp-format (:updated-at original-section))
        update-time (format/parse common/timestamp-format (:updated-at updated-section))
        time-limit (t/plus original-time (t/minutes c/collapse-edit-time))
        original-note-stamp (get-in original-section [:notes :updated-at])
        original-note-time (if original-note-stamp (format/parse common/timestamp-format original-note-stamp))
        update-note-stamp (get-in original-section [:notes :updated-at])
        update-note-time (if update-note-stamp (format/parse common/timestamp-format update-note-stamp))
        note-time-limit (if original-note-time (t/plus original-note-time (t/minutes c/collapse-edit-time)))]
    (cond
      ;; the section time difference is greater than the time limit gap allowed
      (not (t/within? (t/interval original-time time-limit) update-time)) true
      ;; both note times are nil
      (and (nil? original-note-time) (nil? update-note-time)) false
      ;; only one note time is nil
      (or (not (nil? original-note-time)) (not (nil? update-note-time))) true
      ;; the note time difference is greater than the time limit gap allowed
      (not (t/within? (t/interval original-note-time note-time-limit) update-note-time)) true
      ;; both section and note times are within limits
      :else false)))

(defn- different-author?
  "Compare the section's and notes original and updated authors' `:user-id`7s, returning `true` if they
  both match and false if they don't."
  [[original-section updated-section]]
  (not (and
    (= (get-in original-section [:author :user-id])
       (get-in updated-section [:author :user-id]))
    (= (get-in original-section [:notes :author :user-id])
       (get-in updated-section [:notes :author :user-id])))))

;; ----- Validations -----

(defun valid-section
  "
  Validate the company, and section name of the section
  returning `:bad-company`, `:bad-section-name` respectively.

  TODO: take company slug and section name separately and validate they match
  TODO: Use prismatic schema to validate section properties.
  "
  ; ([company-slug section-name section :guard #(or (not (:company-slug %)) (not (company/get-company (:company-slug %))))] :bad-company)
  ; ([company-slug section-name section :guard #(not (common/sections (:section-name %)))] :bad-section-name)
  ([_ _ _] true))

;; ----- Section revisions -----

(defn- revisions-for
  [company-slug section-name fields]
  (common/updated-at-order
    (common/read-resources table-name "company-slug-section-name" [company-slug section-name] fields)))

(defn list-revisions
  "Given the slug of the company, and a section name retrieve the timestamps and author of the section revisions."
  [company-slug section-name] (revisions-for company-slug section-name [:updated-at :author]))

(defn- list-revision-ids
  "Given the slug of the company, and a section name retrieve the timestamps and database id of the section revisions."
  [company-slug section-name] (revisions-for company-slug section-name [:updated-at :id]))

(defn get-revisions
  "Given the slug of the company a section name, and an optional specific updated-at timestamp,
  retrieve the section revisions from the database."
  ([company-slug section-name]
  (common/updated-at-order
    (common/read-resources table-name "company-slug-section-name" [company-slug section-name])))

  ([slug section-name updated-at]
  (common/updated-at-order
    (common/read-resources table-name "company-slug-section-name-updated-at" [slug section-name updated-at]))))


(defun- revise-or-update

  ; this is the first time for the section, so create it
  ([[nil updated-section] timestamp]
    (println "\n\nfirst\n\n")
    (common/create-resource table-name updated-section timestamp))

  ; it's been more than the allowed time since the last revision, so create a new revision
  ([[_original-section updated-section] :guard revision-time-gt? timestamp]
    (println "\n\ntime!\n\n")
    (common/create-resource table-name updated-section timestamp))

  ; it's a different author than the last revision, so create a new revision
  ([[_original-section updated-section] :guard different-author? timestamp]
    (println "\n\nauthor!\n\n")
    (common/create-resource table-name updated-section timestamp))

  ; it's both the same author and less than the allowed time, so just update the current revision
  ([[original-section updated-section] timestamp]
    (println "\n\nUpdate!\n\n")
    (common/update-resource table-name primary-key original-section updated-section timestamp)))

;; ----- Section CRUD -----

(defun get-section
  "
  Given the company slug, section name, and optional as-of timestamp of a section, retrieve the section from
  the database at the revision that matches the as-of timestamp or the most recent revision prior to that timestamp.
  Return nil if no section revision exists that satisfies this."
  ([company-slug section-name] (get-section company-slug section-name nil))

  ([company-slug section-name _as-of :guard nil?]
  ;; retrieve the id most recent revision of this section
  (if-let [id (:id (first (list-revision-ids company-slug section-name)))]
    ;; retrieve the section by its id
    (common/read-resource table-name id)))

  ([company-slug section-name as-of]
  ;; retrieve the id of the section revision that matches the as-of or is the first revision prior to it
  (if-let [id (some #(time-lte % as-of) (list-revision-ids company-slug section-name))]
    ;; retrieve the section by its id
    (common/read-resource table-name id))))

(defun put-section
  "
  Given the company slug, section name, and section property map, create a new section revision,
  updating the company with a new revision and returning the property map for the resource or `false`.

  If you get a false response and aren't sure why, use the `valid-section` function to get a reason keyword.

  TODO: :author and :updated-at for notes if it's changed
  "
  ([company-slug section-name section author]
    (put-section company-slug section-name section author (common/current-timestamp)))


  ([_company-slug _section-name _section :guard #(not (map? %)) _author _timestamp] false)
  ([_company-slug _section-name _section _author :guard #(not (map? %)) _timestamp] false)

  ([company-slug section-name :guard #(not (keyword? %)) section author timestamp]
  (put-section company-slug (keyword section-name) section author timestamp))

  ([company-slug section-name section author timestamp]
  (let [original-company (company/get-company company-slug)
        original-section (get-section company-slug section-name)
        clean-section (clean section)
        updated-section (-> clean-section
          (assoc :company-slug company-slug)
          (assoc :section-name section-name)
          (assoc :author author)
          (assoc :updated-at timestamp)
          (update-notes-for (original-company section-name) author timestamp))
        updated-company (assoc original-company section-name (-> updated-section
          (dissoc :company-slug)
          (dissoc :section-name)))]
    (if (true? (valid-section company-slug section-name updated-section))
      (do
        ;; update the company
        (company/update-company company-slug updated-company)
        ;; create a new section revision or update the latest section
        (revise-or-update [original-section updated-section] timestamp))
      false))))

;; ----- Armageddon -----

(defn delete-all-sections!
  "Use with caution! Failure can result in partial deletes of just some sections. Returns `true` if successful."
  [] (common/delete-all-resources! table-name))