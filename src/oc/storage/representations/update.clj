(ns oc.storage.representations.update
  "Resource representations for OpenCompany updates."
  (:require [defun.core :refer (defun)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]))

(def representation-props [:slug :org-name :currency :logo-url :logo-width :logo-height
                           :title :note :entries :medium :author :created-at])

(defun url
  ([org-slug slug :guard string?] (str "/orgs/" org-slug "/updates/" slug))
  ([org-slug update :guard map?] (url org-slug (:slug update))))

(defn- self-link [org-slug slug] (hateoas/self-link (url org-slug slug) {:accept mt/update-media-type}))

(defn- item-link [org-slug slug] (hateoas/item-link (url org-slug slug) {:accept mt/update-media-type}))

(defn- up-link [org-slug] (hateoas/up-link (org-rep/url org-slug) {:accept mt/org-media-type}))

(defn- update-links
  [update org-slug]
  (let [slug (:slug update)]
    (assoc update :links [(self-link org-slug slug) (up-link org-slug)])))

(defn render-update [org-slug update]
  (json/generate-string
    (-> update
      (select-keys representation-props)
      (update-links org-slug))))