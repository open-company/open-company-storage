(ns oc.storage.representations.org
  "Resource representations for OpenCompany orgs."
  (:require [defun.core :refer (defun)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.representations.media-types :as mt]))

(def representation-props [:slug :name :team-id :currency :logo-url :logo-width :logo-height
                           :boards :author :created-at :updated-at])

(defun url
  ([slug :guard string?] (str "/orgs/" slug))
  ([org :guard map?] (url (:slug org))))

(defn- org-links
  "HATEOAS links for an org resource."
  [org]
  (assoc org :links [
    (hateoas/item-link (url org) {:accept mt/org-media-type})
    (hateoas/create-link (str (url org) "/boards/") {:content-type mt/board-media-type
                                                     :accept mt/board-media-type})]))

(defn render-org
  "Given an org, create a JSON representation of the org for the REST API."
  [org]
  (let [slug (:slug org)]
    (json/generate-string
      (-> org
        (select-keys representation-props)
        (org-links))
      {:pretty true})))

(defn render-org-list
  "Given a sequence of org maps, create a JSON representation of a list of orgs for the REST API."
  [orgs]
  (json/generate-string
    {:collection {:version hateoas/json-collection-version
                  :href "/"
                  :links [(hateoas/self-link "/" {:accept mt/org-collection-media-type})
                          (hateoas/create-link "/orgs/" {:content-type mt/org-media-type
                                                         :accept mt/org-media-type})]
                  :items (map org-links orgs)}}
    {:pretty true}))