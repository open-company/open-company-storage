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

  ([collection-type {slug :slug :as org}]
  (str "/orgs/" slug "/" collection-type))

  ([collection-type {slug :slug :as org} {start :start following :following unfollowing :unfollowing}]
  (let [follow-concat (if start "&" "?")]
    (str (inbox-url collection-type org) (when start (str "?start=" start))
     (cond
      following (str follow-concat "following=true")
      unfollowing (str follow-concat "unfollowing=true")
      :else "")))))

(defn- dismiss-all-url [org]
  (str (inbox-url "inbox" org) "/dismiss"))

(defn- url
  ([collection-type {slug :slug} sort-type]
  (let [sort-path (when (= sort-type :recent-activity) "?sort=activity")]
    (str "/orgs/" slug "/" collection-type sort-path)))

  ([collection-type {slug :slug :as org} sort-type {start :start direction :direction}]
  (let [concat-str (if (= sort-type :recent-activity) "&" "?")]
    (str (url collection-type org sort-type) concat-str "start=" start (when direction (str "&direction=" (name direction)))))))

(defn- follow-url
  ([following? collection-type org-slug sort-type]
  (let [sort-path (when (= sort-type :recent-activity) "?sort=activity")
        follow-str (str (if sort-path "&" "?")
                        (if following? "following" "unfollowing")
                        "=true")]
    (str "/orgs/" org-slug "/" collection-type sort-path follow-str)))

  ([following? collection-type org-slug sort-type {start :start direction :direction}]
  (let [start-str (str "&start=" start)
        direction-str (when direction (str "&direction=" (name direction)))]
    (str (follow-url following? collection-type org-slug sort-type) start-str direction-str))))

(defn- following-url
  ([collection-type {slug :slug} sort-type]
  (follow-url true collection-type slug sort-type))

  ([collection-type {slug :slug :as org} sort-type params]
  (follow-url true collection-type slug sort-type params)))

(defn- unfollowing-url
  ([collection-type {slug :slug} sort-type]
  (follow-url false collection-type slug sort-type))

  ([collection-type {slug :slug :as org} sort-type params]
  (follow-url false collection-type slug sort-type params)))

(defn- contributions-url

  ([{slug :slug :as org} author-uuid sort-type]
  (let [sort-path (when (= sort-type :recent-activity) "?sort=activity")]
    (str "/orgs/" slug "/contributions/" author-uuid sort-path)))

  ([{slug :slug :as org} author-uuid sort-type {start :start direction :direction}]
  (let [concat-str (if (= sort-type :recent-activity) "&" "?")]
    (str (contributions-url org author-uuid sort-type) concat-str "start=" start (when direction (str "&direction=" (name direction)))))))

(defn- is-inbox? [collection-type]
  (= collection-type "inbox"))

(defn- is-contributions? [collection-type]
  (= collection-type "contributions"))

(defn- pagination-link
  "Add `next` and/or `prior` links for pagination as needed."
  [org collection-type {:keys [start direction sort-type author-uuid following unfollowing]} data]
  (let [activity (:activity data)
        activity? (not-empty activity)
        last-activity (last activity)
        last-activity-date (when activity? (:last-activity-at last-activity))
        next? (= (:next-count data) config/default-activity-limit)
        next-url (when next?
                   (cond
                     (is-inbox? collection-type)
                     (inbox-url collection-type org {:start last-activity-date
                                                     :direction direction
                                                     :following following
                                                     :unfollowing unfollowing})
                     (is-contributions? collection-type)
                     (contributions-url org author-uuid sort-type {:start last-activity-date
                                                                   :direction direction})
                     following
                     (following-url collection-type org sort-type {:start last-activity-date
                                                                   :direction direction})
                     unfollowing
                     (unfollowing-url collection-type org sort-type {:start last-activity-date
                                                                     :direction direction})
                     :else
                     (url collection-type org sort-type {:start last-activity-date
                                                         :direction direction})))
        next-link (when next-url (hateoas/link-map "next" hateoas/GET next-url {:accept mt/entry-collection-media-type}))]
    next-link))

(defn render-activity-for-collection
  "Create a map of the activity for use in a collection in the API"
  [org activity comments reactions access-level user-id]
  (entry-rep/render-entry-for-collection org {:slug (:board-slug activity)
                                              :access (:board-access activity)
                                              :uuid (:board-uuid activity)
                                              :publisher-board (:publisher-board activity)}
    activity comments reactions access-level user-id))

(defn render-activity-list
  "
  Given an org and a sequence of entry maps, create a JSON representation of a list of
  activity for the API.
  "
  [params org collection-type activity boards user]
  (let [sort-type (:sort-type params)
        following? (:following params)
        unfollowing? (:unfollowing params)
        inbox? (is-inbox? collection-type)
        contributions? (is-contributions? collection-type)
        collection-url (cond
                        inbox?
                        (inbox-url collection-type org {:following following? :unfollowing unfollowing?})
                        contributions?
                        (contributions-url org (:author-uuid params) (:sort-type params))
                        following?
                        (following-url collection-type org sort-type)
                        unfollowing?
                        (unfollowing-url collection-type org sort-type)
                        :else
                        (url collection-type org sort-type))
        recent-activity-sort? (= sort-type :recent-activity)
        other-sort-url (cond
                        inbox?
                        nil
                        contributions?
                        (contributions-url org (:author-uuid params) (if recent-activity-sort? :recently-posted :recent-activity))
                        following?
                        (following-url collection-type org (if recent-activity-sort? :recently-posted :recent-activity))
                        unfollowing?
                        (unfollowing-url collection-type org (if recent-activity-sort? :recently-posted :recent-activity))
                        :else
                        (url collection-type org (if recent-activity-sort? :recently-posted :recent-activity)))
        collection-rel (cond
                         following?
                         "following"
                         unfollowing?
                         "unfollowing"
                         recent-activity-sort?
                         "activity"
                         :default
                         "self")
        other-sort-rel (if recent-activity-sort? "self" "activity")
        links (remove nil?
               [(hateoas/link-map collection-rel hateoas/GET collection-url {:accept mt/entry-collection-media-type} {})
                (if inbox? ;; Inbox has no sort
                 (hateoas/link-map "dismiss-all" hateoas/POST (dismiss-all-url org) {:accept mt/entry-media-type
                                                                                     :content-type "text/plain"} {})
                 (hateoas/link-map other-sort-rel hateoas/GET other-sort-url {:accept mt/entry-collection-media-type} {}))
                (hateoas/up-link (org-rep/url org) {:accept mt/org-media-type})
                (pagination-link org collection-type params activity)])
        base-response (if contributions? {:author-uuid (:author-uuid params)} {})]
    (json/generate-string
      {:collection (merge base-response
                    {:version hateoas/json-collection-version
                     :href collection-url
                     :links links
                     :total-count (:total-count activity)
                     :items (map (fn [entry]
                                   (let [board (first (filterv #(= (:slug %) (:board-slug entry)) boards))
                                         access-level (access/access-level-for org board user)]
                                    (render-activity-for-collection org entry
                                      (entry-rep/comments entry)
                                      (reaction-res/aggregate-reactions (entry-rep/reactions entry))
                                      (:access-level access-level) (:user-id user)))) (:activity activity))})}
      {:pretty config/pretty?})))