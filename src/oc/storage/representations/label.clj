(ns oc.storage.representations.label
  (:require [cheshire.core :as json]
            [oc.storage.urls.label :as label-urls]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]))

(def reserved-props [:used-by])

;; ----- Representation -----

(defn label-list-link [org]
  (hateoas/link-map "labels" hateoas/GET (label-urls/labels org) {:accept mt/label-collection-media-type}))

(defn label-up-link [org]
  (hateoas/up-link (label-urls/labels org) {:accept mt/org-media-type}))

(defn create-link [org]
  (hateoas/link-map "create-label" hateoas/POST
                    (label-urls/labels org)
                    {:content-type mt/label-media-type
                     :accept mt/label-media-type}))

(defn label-link [org label]
  (hateoas/self-link (label-urls/label org label) {:accept mt/label-media-type}))

(defn partial-update-link [org label]
  (hateoas/partial-update-link (label-urls/label org label) {:content-type mt/label-media-type
                                                             :accept mt/label-media-type}))

(defn delete-link [org label]
  (hateoas/delete-link (label-urls/label org label)))

(defn label-entries-link [org label]
  (hateoas/link-map "label-entries" hateoas/GET
                    (label-urls/label-entries org label)
                    {:accept mt/entry-collection-media-type}))

(defn label-links [org label]
  [(label-up-link org)
   (label-link org label)
   (partial-update-link org label)
   (delete-link org label)
   (label-entries-link org label)])

(defn label-for-render [org label user]
  (as-> label lb
    (assoc lb :links (label-links org label))
    (assoc lb :count (or (some #(when (= (:user-id %) (:user-id user))
                                  (:count %))
                               (:used-by label)) 0))
    (apply dissoc lb reserved-props)))

(defn render-label [org label user]
  (json/generate-string
   (label-for-render org label user)
   {:pretty config/pretty?}))

(defn labels-list [org labels user]
  (let [labels-to-render (map #(label-for-render org % user) labels)]
    (reverse (sort-by :count labels-to-render))))

(defn render-label-list [org items user]
  (json/generate-string
   {:collection {:version hateoas/json-collection-version
                 :href (label-urls/labels org)
                 :links [(label-list-link org)
                         (create-link org)]
                 :items (labels-list org items user)}}
   {:pretty config/pretty?}))