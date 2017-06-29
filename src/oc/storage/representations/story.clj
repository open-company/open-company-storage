; (ns oc.storage.representations.story
;   "Resource representations for OpenCompany stories."
;   (:require [defun.core :refer (defun)]
;             [cheshire.core :as json]
;             [oc.lib.hateoas :as hateoas]
;             [oc.storage.config :as config]
;             [oc.storage.representations.media-types :as mt]
;             [oc.storage.representations.org :as org-rep]))

; (def representation-props [:slug :org-name :currency :logo-url :logo-width :logo-height
;                            :title :note :entries :medium :author :created-at])

; (def list-props [:slug :title :medium :created-at])

; (defun url
;   ([org-slug slug :guard string?] (str "/orgs/" org-slug "/stories/" slug))
;   ([org-slug story :guard map?] (url org-slug (:slug story))))

; (defn- self-link [org-slug slug] (hateoas/self-link (url org-slug slug) {:accept mt/story-media-type}))

; (defn- up-link [org-slug] (hateoas/up-link (org-rep/url org-slug) {:accept mt/org-media-type}))

; (defn- create-link [org-slug] (hateoas/create-link (str (org-rep/url org-slug) "/stories/")
;                                 {:content-type mt/share-request-media-type
;                                  :accept mt/story-media-type}))

; (defn- story-links
;   [story org-slug]
;   (let [slug (:slug story)]
;     (assoc story :links [(self-link org-slug slug) (up-link org-slug)])))

; (defn render-story
;  "Create a JSON representation of the story for the REST API"
;    [org-slug story]
;   (json/generate-string
;     (-> story
;       (select-keys representation-props)
;       (story-links org-slug))
;     {:pretty config/pretty?}))

; (defn render-story-list
;   "
;   Given a org slug and a sequence of story maps, create a JSON representation of a list of
;   stories for the REST API.
;   "
;   [org-slug storien]
;   (let [collection-url (str (org-rep/url org-slug) "/stories")]
;     (json/generate-string
;       {:collection {:version hateoas/json-collection-version
;                     :href collection-url
;                     :links [(hateoas/self-link collection-url {:accept mt/story-collection-media-type})
;                             (create-link org-slug)]
;                     :items (map #(story-links % org-slug)
;                               (map #(select-keys % list-props) stories))}}
;       {:pretty config/pretty?})))