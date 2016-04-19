(ns open-company.representations.stakeholder-update
  (:require [open-company.representations.common :as common]))

(def ^:private clean-properties [:id])

(def media-type "application/vnd.open-company.stakeholder-update.v1+json")
(def collection-media-type "application/vnd.collection+vnd.open-company.stakeholder-update+json;version=1")

(defn create-link [company-url]
  (common/link-map "share" common/POST (str company-url "/updates") nil))

(defn stakeholder-updates-link [company-url]
  (common/link-map "stakeholder-updates" common/GET (str company-url "/updates") collection-media-type))