(ns oc.storage.representations.topic
  "Resource representations for OpenCompany topics."
  (:require [oc.lib.hateoas :as hateoas]
            [oc.storage.representations.media-types :as mt]))

(defn url [org-slug board-slug topic]
  (str "/orgs/" org-slug "/boards/" board-slug "/topics/" (:slug topic)))

(defn topic-template-for-rendering
  "Add a create link to the provided topic template."
  [org-slug board-slug topic]
  (println org-slug)
  (println board-slug)
  (println topic)
  (assoc topic :links [(hateoas/create-link (url org-slug board-slug topic) {:content-type mt/entry-media-type
                                                                             :accept mt/entry-media-type})]))