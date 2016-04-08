(ns open-company.resources.stakeholder-update
  (:require [schema.core :as schema]
            [defun :refer (defun-)]
            [open-company.lib.slugify :as slug]
            [open-company.resources.common :as common]
            [open-company.resources.section :as section]))

;; ----- RethinkDB metadata -----

(def table-name common/stakeholder-update-table-name)
(def primary-key :id)

;; ----- Utility functions -----

(defn- slug-for [title]
  "Create a slug for the stakeholder update from the slugified title and a short UUID."
  (str (slug/slugify title) "-" (subs (str (java.util.UUID/randomUUID)) 0 4)))

(defun- sections-for
  "Recursive function to get each specified section and add it to the stakeholder update."
  ([su-props conn] (sections-for su-props conn (:sections su-props)))
  ([su-props _conn sections :guard empty?] su-props)
  ([su-props conn sections]
    (let [company-slug (:company-slug su-props)
          section-name (keyword (first sections))
          as-of (:created-at su-props)
          section (section/get-section conn company-slug section-name as-of)
          clean-section (dissoc section :section-name :id :company-slug)]
      (recur (assoc su-props section-name clean-section) conn (rest sections)))))
  
;; ----- Stakeholder update CRD (Create, Read, Delete) -----

(schema/defn ->stakeholder-update :- common/Stakeholder-update
  "Take a minimal map describing a stakeholder-update and a user and 'fill the blanks'"
  ([conn company-slug su-props user] (->stakeholder-update conn company-slug su-props (common/current-timestamp) user))
  ([conn company-slug {:keys [title sections] :as su-props} timestamp user]
  {:pre [(and (string? title) (sequential? sections))]} ; title and sections required
  (-> su-props
    (assoc :slug (slug-for title))
    (assoc :company-slug company-slug)
    (update :intro #(or % {:body ""}))
    (assoc :author (common/author-for-user user))
    (assoc :created-at timestamp)
    (sections-for conn))))

(schema/defn ^:always-validate create-stakeholder-update!
  "Create a stakeholder-update document in RethinkDB."
  [conn stakeholder-update :- common/Stakeholder-update]
  (common/create-resource conn table-name stakeholder-update (:created-at stakeholder-update)))