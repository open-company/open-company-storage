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


(defn- migrate-author [author]
  {:avatar-url (:image author)
   :name (:name author)
   :user-id "0000-0000-0000"})

(defn- migrate-section [section]
  (let [entry (select-keys section [:section-name :title :headline :body :image-url :image-width :image-height :created-at :updated-at :data :metrics])
        key-names (clojure.set/rename-keys entry {:section-name :topic-slug})
        author (map migrate-author (:author section))]
    (assoc key-names :author author)))

(defn- migrate-update-section [topic data]
  (let [section ((keyword topic) data)]
    {:topic-slug (:section section)
     :created-at (:created-at section)}))

;; SAME
;; slug
;; title
;; note
;; logo-height
;; logo-width
;; currency
;; medium
;; to
;; created-at
;; updated-at

;; MIGRATE
;; author
;; logo -> logo-url
;; name -> org-name 

(defn- migrate-update [data]
  (let [author (migrate-author (:author data))
        update (select-keys data [:slug :title :note :medium :to :created-at])
        entries (map #(migrate-update-section % data) (:sections data))]
    (-> update
      (assoc :author author)
      (assoc :entries entries))))

;; SAME
;; slug
;; name
;; currency
;; logo-height
;; logo-width
;; promoted
;; created-at
;; updated-at

;; MIGRATE
;; logo -> logo-url
;; sections -> board/topics 
;; first section, first author -> author
;; section -> entry

;; NEW
;; team-id ""
(defn migrate [conn data result-file]
  (println "Migrate!")
  (let [company (:company data)
        org-author (migrate-author (first (:author (first (:sections data)))))
        org (select-keys company [:slug :name :currency :logo :logo-height :logo-width :promoted :created-at :updated-at])
        key-names (clojure.set/rename-keys org {:logo :logo-url})
        entries (map migrate-section (:sections data))
        new-board {:name "General" :topics (:sections company) :entries entries}
        updates (map migrate-update (:updates data))
        new-keys (-> key-names
                    (assoc :team-id "")
                    (assoc :author org-author)
                    (assoc :boards [new-board])
                    (assoc :updates updates))]
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