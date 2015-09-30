(ns open-company.resources.company
  (:require [clojure.string :as s]
            [defun :refer (defun defun-)]
            [rethinkdb.query :as r]
            [open-company.lib.slugify :as slug]
            [open-company.config :as c]
            [open-company.resources.common :as common]))

(def table-name :companies)
(def primary-key :slug)

(def stuart ; temporary hard-coded author
  {
    :name "Stuart Levinson"
    :slack_id "U06SQLDFT"
    :image "https://secure.gravatar.com/avatar/6ef85399c45b7affe7fc8fb361a3366f.jpg?s=192&d=https%3A%2F%2Fslack.global.ssl.fastly.net%2F66f9%2Fimg%2Favatars%2Fava_0015.png"
  })

;; ----- Utility functions -----

(defn- sections-for
  "Add or replace the :sections vector in the company with the sections this company map contains."
  [company]
  (assoc company :sections
    (vec (clojure.set/intersection common/sections (set (map name (keys company)))))))

(defn commentary-for
  "Return a sequence of the sections that do have commentary in the form [[:section-name :commentary] [:section-name commentary]]"
  [company]
  (let [possible-sections-with-commentary (clojure.set/intersection common/commentary-sections (set (:sections company)))
        sections-with-commentary (filter #(get-in company [(keyword %) :commentary]) possible-sections-with-commentary)]
    (vec (map #(vec [(keyword %) :commentary]) sections-with-commentary))))

(defun- author-for
  "Add or replace the :author map for each specified section with the specified author for this revision."
  ;; determine sections with commentary
  ([author sections company] (author-for author sections (commentary-for company) company))
  ;; all done!
  ([_author sections :guard empty? commentary-sections :guard empty? company] company)
  ;; replace the :author in the section commentary and recurse
  ([author _sections :guard empty? commentary-sections company]
    (author-for author [] (rest commentary-sections) (assoc-in company (flatten [(first commentary-sections) :author]) author)))
  ;; replace the :author in the section and recurse
  ([author sections commentary-sections company]
    (author-for author (rest sections) commentary-sections (assoc-in company [(keyword (first sections)) :author] author))))

(defun- updated-for
  "Add or replace the :updated-at for each specified section with the specified timestamp for this revision."
  ;; determine sections with commentary
  ([timestamp sections company] (updated-for timestamp sections (commentary-for company) company))
  ;; all done!
  ([_timestamp sections :guard empty? commentary-sections :guard empty? company] company)
  ;; replace the :updated-at in the section commentary and recurse
  ([timestamp sections :guard empty? commentary-sections company]
    (updated-for timestamp [] (rest commentary-sections) (assoc-in company (flatten [(first commentary-sections) :updated-at]) timestamp)))
  ;; replace the :updated-at in the section and recurse
  ([timestamp sections commentary-sections company]
    (updated-for timestamp (rest sections) commentary-sections (assoc-in company [(keyword (first sections)) :updated-at] timestamp))))

;; ----- Validations -----

(defun valid-company
  "Given the name and optionally the slug of a new company, and a map of the new company's properties,
  check if the everything is in order to create the new company.

  Ensures the company is provided as an associative data structure or returns `:invalid-map`.

  Ensures the name of the company is specified or returns `:invalid-name`.

  Ensures the slug is valid, or returns `:invalid-slug`.

  If everything is OK with the proposed new company, `true` is returned.

  TODO: Use prismatic schema to validate company properties."
  ([_ :guard #(not (map? %))] :invalid-map)
  ([_ :guard #(and (:slug %) (not (slug/valid-slug? (:slug %))))] :invalid-slug)
  ([_ :guard #(or (not (string? (:name %))) (s/blank? (:name %)))] :invalid-name)
  ([_] true))

;; ----- Company CRUD -----

(defn get-company
  "Given the slug of the company, retrieve it from the database, or return nil if it doesn't exist."
  [slug]
  (common/read-resource table-name slug))

(defun create-company
  "Given the company property map, create the company returning the property map for the resource or `false`.
  If you get a false response and aren't sure why, use the `valid-company` function to get a reason keyword.
  TODO: author is hard-coded, how will this be passed in from API's auth?
  TODO: what to use for author when using Clojure API?"
  ;; not a map
  ([company :guard #(not (map? %))] false)
  ;; missing a slug, create it from the name
  ([company :guard #(and (:name %) (not (:slug %)))] (create-company (assoc company :slug (slug/slugify (:name company)))))
  ;; potentially a valid company
  ([company] 
    (let [company-with-sections (sections-for company) ;; add/replace the :sections
          company-with-revision-author (author-for stuart (:sections company-with-sections) company-with-sections) ;; add/replace the :author
          timestamp (common/current-timestamp)
          company-with-updated-at (updated-for timestamp (:sections company-with-revision-author) company-with-revision-author)]
      (if (true? (valid-company company-with-updated-at))
        (common/create-resource table-name company-with-updated-at timestamp)
        false))))

(defn update-company
  "Given an updated company property map, update the company and return `true` on success.
  TODO: handle case of slug change."
  [company]
  (if-let [original-company (get-company (company primary-key))]
    (common/update-resource table-name primary-key original-company company)))

(defun put-company
  "Given the slug of the company and a company property map, create or update the company
  and return `true` on success.
  TODO: handle case of slug mismatch between URL and properties.
  TODO: handle case of slug change."
  ([_ :guard get-company company] (update-company company))
  ([_ company] (create-company company)))

(defn delete-company
  "Given the slug of the company, delete it and all its sections and return `true` on success."
  [slug]
  (common/delete-resource :sections :slug slug)
  (common/delete-resource table-name slug))

;; ----- Collection of companies -----

(defn list-companies
  "Return a sequence of company property maps with slugs and names, sorted by slug."
  []
  (vec (sort-by primary-key
    (with-open [conn (apply r/connect c/db-options)]
      (-> (r/table table-name)
        (r/with-fields [primary-key "name"])
        (r/run conn))))))

(defn delete-all-companies!
  "Use with caution! Failure can result in partial deletes of sections and companies. Returns `true` if successful."
  []
  ;; Delete all reports and all companies
  (common/delete-all-resources! "sections")
  (common/delete-all-resources! table-name))