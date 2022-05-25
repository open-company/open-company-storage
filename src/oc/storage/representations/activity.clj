(ns oc.storage.representations.activity
  "Resource representations for OpenCompany activity."
  (:require [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.urls.org :as org-urls]
            [oc.storage.representations.entry :as entry-rep]
            [oc.storage.resources.reaction :as reaction-res]
            [oc.storage.urls.activity :as activity-urls]
            [oc.storage.config :as config]))

(defn- is-contributions? [collection-type]
  (= collection-type "contributions"))

(defn- is-replies? [collection-type]
  (= collection-type "replies"))

(defn- is-bookmarks? [collection-type]
  (= collection-type "bookmarks"))

(defn- is-label? [collection-type]
  (= collection-type "label"))

(defn invert-direction [direction]
  (case direction
    :after :before
    :before :after))

(defn- refresh-link
  "Add `refresh` links for pagination as needed to reload the whole set the client holds."
  [org collection-type {:keys [refresh direction author-uuid label following unfollowing]} data]
  (when (seq (:activity data))
    (let [refresh-start (-> data :activity last :sort-value)
          refresh-direction (if refresh direction (invert-direction direction))
          refresh-params {:refresh true
                          :direction refresh-direction
                          :start refresh-start
                          :following following
                          :unfollowing unfollowing}
          refresh-url (cond (is-replies? collection-type)
                            (activity-urls/replies org refresh-params)

                            (is-bookmarks? collection-type)
                            (activity-urls/bookmarks org refresh-params)

                            (is-contributions? collection-type)
                            (activity-urls/contributions org author-uuid refresh-params)

                            (is-label? collection-type)
                            (activity-urls/label-entries org label refresh-params)

                            following
                            (activity-urls/following org collection-type refresh-params)

                            unfollowing
                            (activity-urls/unfollowing org collection-type refresh-params)

                            :else
                            (activity-urls/activity org collection-type refresh-params))]
      (hateoas/link-map "refresh" hateoas/GET refresh-url {:accept mt/entry-collection-media-type}))))

(defn- pagination-link
  "Add `next` link for pagination as needed."
  [org collection-type {:keys [refresh direction author-uuid label following unfollowing limit]} {total-count :total-count next-count :next-count :as data}]
  (when (and (seq (:activity data))         ;; No need of refresh if there are no posts
             (not= next-count total-count)  ;; and we are NOT returning the whole set already
             (or refresh                    ;; and if this is a refresh request already
                 (= next-count limit)))     ;;    or the page is full
    (let [pagination-start (-> data :activity last :sort-value)
          pagination-direction (if refresh (invert-direction direction) direction) ;; Next pages have always opposit direction of refresh
          pagination-params {:direction pagination-direction
                             :start pagination-start
                             :following following
                             :unfollowing unfollowing}
          next-url (cond (is-replies? collection-type)
                         (activity-urls/replies org pagination-params)

                         (is-bookmarks? collection-type)
                         (activity-urls/bookmarks org pagination-params)

                         (is-contributions? collection-type)
                         (activity-urls/contributions org author-uuid pagination-params)

                         (is-label? collection-type)
                         (activity-urls/label-entries org label pagination-params)

                         following
                         (activity-urls/following org collection-type pagination-params)

                         unfollowing
                         (activity-urls/unfollowing org collection-type pagination-params)

                         :else
                         (activity-urls/activity org collection-type pagination-params))]
      (hateoas/link-map "next" hateoas/GET next-url {:accept mt/entry-collection-media-type}))))

(defn render-activity-for-collection
  "Create a map of the activity for use in a collection in the API"
  [org board activity comments reactions user-id]
  (entry-rep/render-entry-for-collection org board activity comments reactions (select-keys board [:access-level :role :premium?]) user-id))

(defn render-activity-list
  "
  Given an org and a sequence of entry maps, create a JSON representation of a list of
  activity for the API.
  "
  [params org collection-type activity boards-by-uuid user]
  (let [following? (:following params)
        unfollowing? (:unfollowing params)
        bookmarks? (is-bookmarks? collection-type)
        replies? (is-replies? collection-type)
        contributions? (is-contributions? collection-type)
        label? (is-label? collection-type)
        collection-url (cond
                        replies?
                        (activity-urls/replies org)
                        bookmarks?
                        (activity-urls/bookmarks org)
                        contributions?
                        (activity-urls/contributions org (:author-uuid params))
                        label?
                        (activity-urls/label-entries org (:label params))
                        following?
                        (activity-urls/following org collection-type)
                        unfollowing?
                        (activity-urls/unfollowing org collection-type)
                        :else
                        (activity-urls/activity org collection-type))
        collection-rel (cond
                         following?
                         "following"
                         unfollowing?
                         "unfollowing"
                         replies?
                         "replies"
                         bookmarks?
                         "bookmarks"
                         :else
                         "self")
        links (remove nil?
               [(hateoas/link-map collection-rel hateoas/GET collection-url {:accept mt/entry-collection-media-type} {})
                (hateoas/up-link (org-urls/org org) {:accept mt/org-media-type})
                (pagination-link org collection-type params activity)
                (refresh-link org collection-type params activity)])
        base-response (as-> {} b
                        (if (seq (:last-seen-at params))
                          (assoc b :last-seen-at (:last-seen-at params))
                          b)
                        (if (seq (:next-seen-at params))
                          (assoc b :next-seen-at (:next-seen-at params))
                          b)
                        (if contributions?
                          (assoc b :author-uuid (:author-uuid params))
                          b)
                        (if label?
                          (assoc b :label-slug (:label params))
                          b)
                        (if (seq (:container-id params))
                          (assoc b :container-id (:container-id params))
                          b))]
    (json/generate-string
      {:collection (merge base-response
                    {:version hateoas/json-collection-version
                     :href collection-url
                     :links links
                     :direction (:direction params)
                     :start (:start params)
                     :total-count (:total-count activity)
                     :items (map (fn [entry]
                                   (render-activity-for-collection org
                                                                   (get boards-by-uuid (:board-uuid entry))
                                                                   entry
                                                                   (entry-rep/comments entry)
                                                                   (reaction-res/aggregate-reactions (entry-rep/reactions entry))
                                                                   (:user-id user)))
                                 (:activity activity))})}
      {:pretty config/pretty?})))