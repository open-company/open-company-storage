(ns oc.storage.representations.org
  "Resource representations for OpenCompany orgs."
  (:require [defun.core :refer (defun)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.representations.media-types :as mt]))

(def representation-props [:slug :name :team-id :currency :logo-url :logo-width :logo-height
                           :boards :author :authors :created-at :updated-at])

(defun url
  ([slug :guard string?] (str "/orgs/" slug))
  ([org :guard map?] (url (:slug org))))

(defn- self-link [org] (hateoas/self-link (url org) {:accept mt/org-media-type}))

(defn- item-link [org] (hateoas/item-link (url org) {:accept mt/org-media-type}))

(defn partial-update-link [org] (hateoas/partial-update-link (url org) {:content-type mt/org-media-type
                                                                        :accept mt/org-media-type}))

(defn- board-create-link [org] (hateoas/create-link (str (url org) "/boards/") {:content-type mt/board-media-type
                                                                                :accept mt/board-media-type}))

(defn- add-author-link [org] 
  (hateoas/add-link hateoas/POST (str (url org) "/authors/") {:content-type mt/org-author-media-type
                                                              :accept mt/org-author-media-type}))

(defn- remove-author-link [org user-id]
  (hateoas/remove-link (str (url org) "/authors/" user-id)))

(defn- org-collection-links [org]
  (assoc org :links [(item-link org) (board-create-link org)]))

(defn- org-links [org access-level]
  (let [links [(self-link org) (board-create-link org)]
        full-links (if (= access-level :author) 
                      (concat links [(partial-update-link org)
                                     (add-author-link org)
                                     (hateoas/create-link (str (url org) "/updates/")
                                        {:content-type mt/share-request-media-type
                                         :accept mt/update-media-type})])
                      links)]
    (assoc org :links full-links)))

(defn render-author-for-collection
  "Create a map of the org author for use in a collection in the REST API"
  [org user-id]
  {:user-id user-id
   :links [(remove-author-link org user-id)]})

(defn render-org
  "Given an org, create a JSON representation of the org for the REST API."
  [org access-level]
  (let [slug (:slug org)]
    (json/generate-string
      (-> org
        (select-keys representation-props)
        (org-links access-level))
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
                  :items (map org-collection-links orgs)}}
    {:pretty true}))