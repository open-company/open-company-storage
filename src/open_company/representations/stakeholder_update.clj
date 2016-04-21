(ns open-company.representations.stakeholder-update
  (:require [cheshire.core :as json]
            [open-company.representations.common :as common]))

(def ^:private clean-properties [:id])

(def media-type "application/vnd.open-company.stakeholder-update.v1+json")
(def collection-media-type "application/vnd.collection+vnd.open-company.stakeholder-update+json;version=1")

(defn stakeholder-updates-url [company-url]
  (str company-url "/updates"))

(defn stakeholder-update-link [company-url slug]
  (common/link-map "self" common/GET (str (stakeholder-updates-url company-url) "/" slug) media-type))

(defn create-link [company-url]
  (common/link-map "share" common/POST (stakeholder-updates-url company-url) nil))

(defn stakeholder-updates-link [company-url]
  (common/link-map "stakeholder-updates" common/GET (stakeholder-updates-url company-url) collection-media-type))

(defn- stakeholder-update-fragment [company-url update]
  {
    :title (:title update)
    :slug (:slug update)
    :intro (:intro update)
    :created-at (:created-at update)
    :links [(stakeholder-update-link company-url (:slug update))]
  })

(defn render-stakeholder-update
  "Create a JSON representation of a stakeholder updates for the REST API"
  [company-url update authorized]
  )

(defn render-stakeholder-update-list
  "Create a JSON representation of a group of stakeholder updates for the REST API"
  [company-url updates]
  (let [su-url (stakeholder-updates-url company-url)]
    (json/generate-string
      {:collection {:version common/json-collection-version
                    :href su-url
                    :links [(common/self-link su-url collection-media-type)]
                    :stakeholder-updates (map #(stakeholder-update-fragment company-url %) updates)}}
    {:pretty true})))