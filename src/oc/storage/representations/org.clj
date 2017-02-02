(ns oc.storage.representations.org
  "Resource representations for OpenCompany orgs."
  (:require [clojure.string :as s]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.representations.media-types :as mt]))

(defn render-org-list
  "
  Given a sequence of org maps, create a JSON representation of a list of orgs for the REST API.
  "
  [orgs]
  (json/generate-string
    {:collection {:version hateoas/json-collection-version
                  :href "/"
                  :links [(hateoas/self-link "/" {:accept mt/org-collection-media-type})]
                  :orgs orgs}}
    {:pretty true}))