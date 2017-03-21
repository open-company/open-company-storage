(ns oc.storage.util.migrate
  "Migrate from legacy export format to new import format."
  (:require [clojure.string :as s]
            [clojure.tools.cli :refer (parse-opts)]
            [clojure.walk :refer (keywordize-keys)]
            [zprint.core :as zp]
            [oc.lib.db.pool :as db]
            [oc.lib.db.common :as db-common]
            [oc.storage.resources.org :as org]
            [oc.storage.resources.board :as board]
            [oc.storage.resources.entry :as entry]
            [oc.storage.resources.update :as update]            
            [oc.storage.config :as c]
            [zprint.core :as zp])
  (:gen-class))

(defn exit [status msg]
  (println msg)
  (System/exit status))

;; ----- Migration -----

; SAME
; slug
; name
; currency
; logo-height
; logo-width
; promoted
; created-at
; updated-at


; MIGRATE
; logo -> logo-url
; sections -> board/topics 
; first section, first author -> author
; section -> entry

; NEW
; team-id ""
; boards

(defn- migrate-author [author]
  {:avatar-url (:image author)
   :name (:name author)
   :user-id "0000-0000-0000"})

; :as-of nil,
; :author
;  [{:image
;      "https://secure.gravatar.com/avatar/63e2e4d667a857c7c850fe8c4f1efddc.jpg?s=192&d=https%3A%2F%2Fa.slack-edge.com%2F7fa9%2Fimg%2Favatars%2Fava_0009-192.png",
;    :name "Alec Fink",
;    :updated-at "2016-10-04T14:17:31.437Z",
;    :user-id "slack:U03C4NJBW"}],
; :body "",
; :body-placeholder
;  "Provide an overview of important happenings in the company since the last update.",
; :company-slug "10-foot-wave",
; :created-at "2016-10-04T14:17:31.437Z",
; :description "Company update",
; :headline "10 Foot Wave signs deal with Great Expressions",
; :id "a3067151-64a8-4cad-ba6f-60add56626a5",
; :image-height 0,
; :image-url nil,
; :image-width 0,
; :read-only false,
; :section "update",
; :section-name "update",
; :title "CEO Update",
; :updated-at "2016-10-04T14:17:31.437Z"

; :topic-slug "custom-db84"          
; :title "Welcome!"
; :headline "Sue Topeka"
; :body "<p>Sue joins 18F from Microsoft where she worked on the Office 365 team as a developer.</p><p>\"I'm up for this new challenge. I've always wanted a chance to use my skills for good.\"</p><p>Sue's pronoun is she/her.</p>"
; :image-url "https://cdn.filestackcontent.com/IsgWxSqQRmfRmEh12Njw"
; :image-width 500
; :image-height 207
; :created-at "2016-10-11T12:02:16.539Z"
(defn- migrate-section [section]
  (let [entry (select-keys section [:section-name :title :headline :body :image-url :image-width :image-height :created-at :updated-at :data :metrics])
        key-names (clojure.set/rename-keys entry {:section-name :topic-slug})
        author (map migrate-author (:author section))]
    (assoc key-names :author author)))

(defn migrate [conn data result-file]
  (println "Migrate!")
  (let [company (:company data)
        org-author (migrate-author (first (:author (first (:sections data)))))
        org (select-keys company [:slug :name :currency :logo :logo-height :logo-width :promoted :created-at :updated-at])
        key-names (clojure.set/rename-keys org {:logo :logo-url})
        entries (map migrate-section (:sections data))
        new-board {:name "General" :topics (:sections company) :entries entries}
        new-keys (-> key-names
                    (assoc :team-id "")
                    (assoc :author org-author)
                    (assoc :boards [new-board]))]
    (zp/set-options! {:map {:comma? false
                            :key-order [:slug :name :team-id :currency :logo-url :logo-width :logo-height :promoted
                                        :created-at :updated-at :author :boards]}})
    (spit result-file (zp/zprint-str new-keys))))

;; ----- CLI -----

(def cli-options [])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    
    ;; Handle arg error conditions
    (cond
      (not= (count arguments) 2) (exit 1 "requires 2 args")
      errors (exit 1 "specify an initial and result .edn file"))
    
    ;; Get the file names to migrate
    (try
      (let [initial-file (first arguments)
            result-file (second arguments)
            conn (db/init-conn c/db-options)
            edn-file #".*\.edn$"]

        ;; Sanity check
        (when-not (and (re-matches edn-file initial-file)
                       (re-matches edn-file result-file))
          (exit 1 "specify an initial and result .edn file"))
        
        ;; Migrate thefile
        (let [data (read-string (slurp initial-file))]
          (if (and (map? data) (:company data))
            (migrate conn (keywordize-keys data) result-file)
            (exit 1 "this doesn't seem to be a legacy edn file"))))
      (catch Exception e
        (println e)
        (exit 1 "Exception migrating.")))))