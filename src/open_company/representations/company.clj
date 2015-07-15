(ns open-company.representations.company
  (:require [cheshire.core :as json]
            [open-company.representations.common :as common]
            [open-company.resources.company :as company]))

(defn- url [company] 
  (str "/v1/companies/" (:symbol company)))

(defn- self-link [company]
  (common/self-link (url company) company/media-type))

(defn- update-link [company]
  (common/update-link (url company) company/media-type))

(defn- delete-link [company]
  (common/delete-link (url company)))

(defn company-links
  "Add the HATEAOS links to the company"
  [company]
  (apply array-map (concat (flatten (vec company)) 
    [:links [
      (self-link company)
      (update-link company)
      (delete-link company)]])))

(defn- company-to-json-map [company]
  ;; Generate JSON from the sorted array map that results from:
  ;; 1) removing unneeded :id key
  ;; 2) render timestamps as strings
  ;; 3) adding the HATEAOS links to the array hash
  (let [company-props (dissoc company :id)]
    (-> company-props
      company-links)))

(defn render-company
  "Create a JSON representation of a company for the REST API"
  [company]
  (json/generate-string (company-to-json-map company) {:pretty true}))