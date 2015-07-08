(ns open-company.representations.company
  (:require [cheshire.core :as json]
            [clj-time.format :refer (unparse)]
            [open-company.resources.common :refer (timestamp-format)]
            [open-company.resources.company :as company]))

(defn- company-to-json-map [company]
  company)

(defn render-company
  "Create a JSON representation of a company for the REST API"
  [company]
  (json/generate-string (company-to-json-map company) {:pretty true}))