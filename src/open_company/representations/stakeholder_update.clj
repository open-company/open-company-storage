(ns open-company.representations.stakeholder-update
  (:require [open-company.representations.common :as common]))

(def ^:private clean-properties [:id])

(defn create-link [company-url]
  (common/link-map "share" common/POST (str company-url "/updates") nil))