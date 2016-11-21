(ns open-company.representations.stakeholder-update
  (:require [cheshire.core :as json]
            [open-company.representations.common :as common]))

(def ^:private clean-properties [:id :updated-at :company-slug])

(def media-type "application/vnd.open-company.stakeholder-update.v1+json")
(def collection-media-type "application/vnd.collection+vnd.open-company.stakeholder-update+json;version=1")

(defn stakeholder-updates-url [company-url]
  (str company-url "/updates"))

(defn stakeholder-update-url [company-url slug]
  (str (stakeholder-updates-url company-url) "/" slug))

(defn self-link [company-url slug]
  (common/link-map "self" common/GET (stakeholder-update-url company-url slug) media-type))

(defn create-link [company-url]
  (common/link-map "share" common/POST (stakeholder-updates-url company-url) nil))

(defn stakeholder-updates-link [company-url]
  (common/link-map "stakeholder-updates" common/GET (stakeholder-updates-url company-url) collection-media-type))

(defn- stakeholder-update-fragment [company-url update authorized]
  (let [slug (:slug update)
        su-link (self-link company-url slug)]
    {
      :title (:title update)
      :slug slug
      :created-at (:created-at update)
      :medium (:medium update)
      :author (:author update)
      :links (if authorized [su-link (common/delete-link (stakeholder-update-url company-url slug))] [su-link])
    }))

(defn- stakeholder-update-links
  "Add the HATEAOS links to the stakeholder update"
  [update company-url read-access write-access]
  (let [su-links [(self-link company-url (:slug update))]
        with-read (if read-access
                   (conj su-links (common/link-map "company" common/GET company-url common/company-media-type))
                   su-links)
        with-write (if write-access
                     (conj with-read (common/delete-link (stakeholder-update-url company-url (:slug update))))
                     with-read)]
    (assoc update :links with-write)))

(defn- stakeholder-update-for-rendering
  "Get a representation of the stakeholder update for the REST API"
  [company-url update read-access write-access]
  (-> update
    (stakeholder-update-links company-url read-access write-access)
    (common/clean clean-properties)))

(defn render-stakeholder-update
  "Create a JSON representation of a stakeholder updates for the REST API"
  [company-url update read-access write-access]
  (json/generate-string (stakeholder-update-for-rendering company-url update read-access write-access) {:pretty true}))

(defn render-stakeholder-update-list
  "Create a JSON representation of a group of stakeholder updates for the REST API"
  [company-url updates authorized]
  (let [su-url (stakeholder-updates-url company-url)]
    (json/generate-string
      {:collection {:version common/json-collection-version
                    :href su-url
                    :links [
                      (common/self-link su-url collection-media-type)
                      (common/link-map "company" common/GET company-url common/company-media-type)
                    ]
                    :stakeholder-updates (map #(stakeholder-update-fragment company-url % authorized) updates)}}
    {:pretty true})))