(ns open-company.db.migrations.add-front-of-card-properties
  (:require [rethinkdb.query :as r]))

(def new-fields [{:name :snippet :value ""}
                 {:name :image-url :value nil}
                 {:name :image-height :value 0}
                 {:name :image-width :value 0}])

(defn up [conn]

  ;; add new fields with defaults to existing companies
  (println "\nUpdating companies...")

  ;; add new fields with defaults to existing sections
  (println "\nUpdating sections...")
  (doseq [field new-fields]
 
    (println (str "\nAdding field: " (:name field) " with value: " (:value field)))
    (println
      (-> (r/table "sections")
        (r/filter (r/fn [row]
                  (r/not (r/has-fields row (:name field)))))
        (r/update {(:name field) (:value field)})
        (r/run conn))))

  true) ; return true on success