(ns oc.storage.representations.org
  "Resource representations for OpenCompany orgs."
  (:require [defun.core :refer (defun)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.representations.media-types :as mt]))

(defun url
  ([slug :guard string?] (str "/orgs/" slug))
  ([org :guard map?] (url (:slug org))))

(defn- org-links
  "HATEOAS links for an org resource."
  [org]
  (assoc org :links [
    (hateoas/item-link (url org) {:accept mt/org-media-type})]))

(defn render-org-list
  "
  Given a sequence of org maps, create a JSON representation of a list of orgs for the REST API.
  "
  [orgs]
  (json/generate-string
    {:collection {:version hateoas/json-collection-version
                  :href "/"
                  :links [(hateoas/self-link "/" {:accept mt/org-collection-media-type})]
                  :orgs (map org-links orgs)}}
    {:pretty true}))