(ns open-company.resources.company)

(def
  company-media-type "application/vnd.open-company.company+json;version=1")

(defn get-company [ticker]
  (println ticker)
  (if (= ticker "OPEN") 
    {:symbol "OPEN" :name "Transparency, LLC" :url "https://opencompany.io"}
    nil))