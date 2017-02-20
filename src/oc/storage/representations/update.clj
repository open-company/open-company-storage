(ns oc.storage.representations.update
  "Resource representations for OpenCompany updates."
  (:require [defun.core :refer (defun)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]))

(def representation-props [:slug :org-name :currency :logo-url :logo-width :logo-height
                           :title :note :entries :medium :author :created-at])

(def list-props [:slug :title :medium :created-at])

(defun url
  ([org-slug slug :guard string?] (str "/orgs/" org-slug "/updates/" slug))
  ([org-slug update :guard map?] (url org-slug (:slug update))))

(defn- self-link [org-slug slug] (hateoas/self-link (url org-slug slug) {:accept mt/update-media-type}))

(defn- item-link [org-slug slug] (hateoas/item-link (url org-slug slug) {:accept mt/update-media-type}))

(defn- up-link [org-slug] (hateoas/up-link (org-rep/url org-slug) {:accept mt/org-media-type}))

(defn- create-link [org-slug] (hateoas/create-link (str (org-rep/url org-slug) "/updates/")
                                {:content-type mt/share-request-media-type
                                 :accept mt/update-media-type}))

(defn- update-links
  [update org-slug]
  (let [slug (:slug update)]
    (assoc update :links [(self-link org-slug slug) (up-link org-slug)])))

(defn render-update
 "Create a JSON representation of the update for the REST API"
   [org-slug update]
  (json/generate-string
    (-> update
      (select-keys representation-props)
      (update-links org-slug))
    {:pretty true}))

(defn render-update-list
  "
  Given a org slug and a sequence of update maps, create a JSON representation of a list of
  updates for the REST API.
  "
  [org-slug updates]
  (let [collection-url (str (org-rep/url org-slug) "/updates")]
    (json/generate-string
      {:collection {:version hateoas/json-collection-version
                    :href collection-url
                    :links [(hateoas/self-link collection-url {:accept mt/update-collection-media-type})
                            (create-link org-slug)]
                    :items (map #(update-links % org-slug)
                              (map #(select-keys % list-props) updates))}}
      {:pretty true})))