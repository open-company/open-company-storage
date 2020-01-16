(ns oc.storage.representations.activity
  "Resource representations for OpenCompany activity."
  (:require [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.api.access :as access]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.representations.entry :as entry-rep]
            [oc.storage.resources.reaction :as reaction-res]
            [oc.storage.config :as config]))

(defn- inbox-url
  ([collection-type {slug :slug}]
  (str "/orgs/" slug "/" collection-type))
  ([collection-type {slug :slug :as org} {start :start}]
  (str (inbox-url collection-type org) "?start=" start)))

(defn- dismiss-all-url [org]
  (str (inbox-url "inbox" org) "/dismiss-all"))

(defn- url
  ([collection-type {slug :slug} sort-type]
  (let [sort-path (when (= sort-type :recent-activity) "?sort=activity")]
    (str "/orgs/" slug "/" collection-type sort-path)))
  ([collection-type {slug :slug :as org} sort-type {start :start direction :direction}]
  (let [concat-str (if (= sort-type :recent-activity) "&" "?")]
    (str (url collection-type org sort-type) concat-str "start=" start (when direction (str "&direction=" (name direction)))))))

(defn- is-inbox? [collection-type]
  (= collection-type "inbox"))

(defn- pagination-link
  "Add `next` and/or `prior` links for pagination as needed."
  [org collection-type sort-type {:keys [start direction]} data]
  (let [activity (:activity data)
        activity? (not-empty activity)
        last-activity (last activity)
        last-activity-date (when activity? (or (:last-activity-at last-activity) (:last-activity-at last-activity)))
        next? (= (:next-count data) config/default-activity-limit)
        next-url (when next?
                   (if (is-inbox? collection-type)
                     (inbox-url collection-type org {:start last-activity-date :direction direction})
                     (url collection-type org sort-type {:start last-activity-date :direction direction})))
        next-link (when next-url (hateoas/link-map "next" hateoas/GET next-url {:accept mt/activity-collection-media-type}))]
    next-link))

(defn render-activity-for-collection
  "Create a map of the activity for use in a collection in the API"
  [org activity comments reactions access-level user-id]
  (entry-rep/render-entry-for-collection org {:slug (:board-slug activity)
                                              :access (:board-access activity)
                                              :uuid (:board-uuid activity)}
    activity comments reactions access-level user-id))

(defn render-activity-list
  "
  Given an org and a sequence of entry maps, create a JSON representation of a list of
  activity for the API.
  "
  [params org collection-type sort-type activity boards user]
  (let [inbox? (is-inbox? collection-type)
        collection-url (if inbox?
                         (inbox-url collection-type org)
                         (url collection-type org sort-type))
        recent-activity-sort? (= sort-type :recent-activity)
        other-sort-url (when-not inbox?
                         (url collection-type org (if recent-activity-sort? :recently-posted :recent-activity)))
        collection-rel (if recent-activity-sort? "activity" "self")
        other-sort-rel (if recent-activity-sort? "self" "activity")
        links (remove nil?
               [(hateoas/link-map collection-rel hateoas/GET collection-url {:accept mt/activity-collection-media-type} {})
                (if inbox? ;; Inbox has no sort
                 (hateoas/link-map "dismiss-all" hateoas/POST (dismiss-all-url org) {:accept mt/entry-media-type
                                                                                     :content-type "text/plain"} {})
                 (hateoas/link-map other-sort-rel hateoas/GET other-sort-url {:accept mt/activity-collection-media-type} {}))
                (hateoas/up-link (org-rep/url org) {:accept mt/org-media-type})
                (pagination-link org collection-type sort-type params activity)])]
    (json/generate-string
      {:collection {:version hateoas/json-collection-version
                    :href collection-url
                    :links links
                    :total-count (:total-count activity)
                    :items (map (fn [entry]
                                  (let [board (first (filterv #(= (:slug %) (:board-slug entry)) boards))
                                        access-level (access/access-level-for org board user)]
                                   (render-activity-for-collection org entry
                                     (entry-rep/comments entry)
                                     (reaction-res/aggregate-reactions (entry-rep/reactions entry))
                                     (:access-level access-level) (:user-id user)))) (:activity activity))}}
      {:pretty config/pretty?})))