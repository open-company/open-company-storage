(ns oc.storage.representations.org
  "Resource representations for OpenCompany orgs."
  (:require [defun.core :refer (defun)]
            [cheshire.core :as json]
            [oc.lib.hateoas :as hateoas]
            [oc.storage.config :as config]
            [oc.storage.representations.media-types :as mt]))

(def public-representation-props [:uuid :slug :name :team-id :logo-url :logo-width :logo-height
                                  :boards :created-at :updated-at])
(def representation-props (concat public-representation-props [:author :authors :must-see-count :bookmarks-count
                                                               :content-visibility]))

(defun url
  ([slug :guard string?] (str "/orgs/" slug))
  ([org :guard map?] (url (:slug org))))

(defn- self-link [org] (hateoas/self-link (url org) {:accept mt/org-media-type}))

(defn- item-link [org] (hateoas/item-link (url org) {:accept mt/org-media-type}))

(defn partial-update-link [org] (hateoas/partial-update-link (url org) {:content-type mt/org-media-type
                                                                        :accept mt/org-media-type}))

(defn- board-create-link [org] (hateoas/create-link (str (url org) "/boards/") {:content-type mt/board-media-type
                                                                                :accept mt/board-media-type}))

(defn- delete-samples-link [org]
  (hateoas/link-map "delete-samples" hateoas/DELETE (str (url org) "/entries/samples") {:content-type mt/entry-collection-media-type}))

(defn- board-pre-flight-create-link [org] (dissoc (assoc (board-create-link org) :rel "pre-flight-create") :accept))

(defn- add-author-link [org] 
  (hateoas/add-link hateoas/POST (str (url org) "/authors/") {:content-type mt/org-author-media-type}))

(defn- remove-author-link [org user-id]
  (hateoas/remove-link (str (url org) "/authors/" user-id)))

(defn- org-collection-links [org]
  (assoc org :links [(item-link org)]))

(defn- activity-link [org]
  (hateoas/link-map "entries" hateoas/GET (str (url org) "/entries") {:accept mt/activity-collection-media-type}))

(defn- recent-activity-link [org]
  (hateoas/link-map "activity" hateoas/GET (str (url org) "/entries?sort=activity") {:accept mt/activity-collection-media-type}))

(defn- change-link [org access-level user]
  (if (or (= access-level :author) (= access-level :viewer))
    (update-in org [:links] conj
      (hateoas/link-map
        "changes"
        hateoas/GET
        (str config/change-server-ws-url "/change-socket/user/" (:user-id user))
        nil))
    org))

(defn- notify-link [org access-level user]
  (if (and (not (:id-token user)) (or (= access-level :author) (= access-level :viewer)))
    (update-in org [:links] conj
      (hateoas/link-map
        "notifications"
        hateoas/GET
        (str config/notify-server-ws-url "/notify-socket/user/" (:user-id user))
        nil))
    org))

(defn- interactions-link [org access-level user]
  (if (and (not (:id-token user)) (or (= access-level :author) (= access-level :viewer)))
    (update-in org [:links] conj
      (hateoas/link-map
        "interactions"
        hateoas/GET
        (str config/interaction-server-ws-url
             "/interaction-socket/user/"
             (:user-id user))
        nil))
    org))

(defn- reminders-link [org access-level user]
  (if (and (not (:id-token user)) (or (= access-level :author) (= access-level :viewer)))
    (update-in org [:links] conj
      (hateoas/link-map
        "reminders"
        hateoas/GET
        (str config/reminder-server-url
             "/orgs/"
             (:uuid org)
             "/reminders")
        {:accept mt/reminders-list-media-type}))
    org))

(defn- bookmarks-link [org access-level user]
  (if (and (not (:id-token user)) (or (= access-level :author) (= access-level :viewer)))
    (update-in org [:links] conj
      (hateoas/link-map
        "bookmarks"
        hateoas/GET
        (str (url org) "/bookmarks")
        {:accept mt/activity-collection-media-type}))
    org))

(defn- recent-bookmarks-link [org access-level user]
  (if (and (not (:id-token user)) (or (= access-level :author) (= access-level :viewer)))
    (update-in org [:links] conj
      (hateoas/link-map
        "bookmarks-activity"
        hateoas/GET
        (str (url org) "/bookmarks?sort=activity")
        {:accept mt/activity-collection-media-type}))
    org))

(defn- payments-link [{:keys [team-id]}]
  (hateoas/link-map 
    "payments"
    hateoas/GET
    (str config/payments-server-url "/teams/" team-id "/customer")
    {:accept mt/payments-customer-media-type}))

(defn- org-links [org access-level user sample-content?]
  (let [links [(self-link org)]
        id-token (:id-token user)
        activity-links (if (and (not id-token) (or (= access-level :author) (= access-level :viewer)))
                          (concat links [(activity-link org) (recent-activity-link org)]) ; (calendar-link org) - not currently used
                          links)
        full-links (if (and (not id-token) (= access-level :author) )
                      (concat activity-links [(board-create-link org)
                                              (board-pre-flight-create-link org)
                                              (partial-update-link org)
                                              (add-author-link org)])
                      activity-links)
        payments-links (if (and config/payments-enabled?
                                (#{:viewer :author} access-level)) ; Only for members of current org
                         (concat full-links [(payments-link org)])
                         full-links)
        with-remove-samples-link (if sample-content?
                                   (concat payments-links [(delete-samples-link org)])
                                   payments-links)]
    (assoc org :links with-remove-samples-link)))

(def auth-link (hateoas/link-map "authenticate" hateoas/GET config/auth-server-url {:accept "application/json"}))

(defn render-author-for-collection
  "Create a map of the org author for use in a collection in the REST API"
  [org user-id access-level]
  {:user-id user-id
   :links (if (= access-level :author) [(remove-author-link org user-id)] [])})

(defn render-org
  "Given an org, create a JSON representation of the org for the REST API."
  [org access-level user sample-content?]
  (let [slug (:slug org)
        rep-props (if (or (= :author access-level) (= :viewer access-level))
                    representation-props
                    public-representation-props)
        user-id (:user-id user)]
    (json/generate-string
      (-> org
        (org-links access-level user sample-content?)
        (change-link access-level user)
        (notify-link access-level user)
        (interactions-link access-level user)
        (reminders-link access-level user)
        (bookmarks-link access-level user)
        (recent-bookmarks-link access-level user)
        (select-keys (conj rep-props :links)))
      {:pretty config/pretty?})))

(defn render-org-list
  "Given a sequence of org maps, create a JSON representation of a list of orgs for the REST API."
  [orgs authed?]
  (let [links [(hateoas/self-link "/" {:accept mt/org-collection-media-type}) auth-link]
        full-links (if authed?
                      (conj links (hateoas/create-link "/orgs/" {:content-type mt/org-media-type
                                                                 :accept mt/org-media-type}))
                      links)]
    (json/generate-string
      {:collection {:version hateoas/json-collection-version
                    :href "/"
                    :links full-links
                    :items (map org-collection-links orgs)}}
      {:pretty config/pretty?})))