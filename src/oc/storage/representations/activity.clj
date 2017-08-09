(ns oc.storage.representations.activity
  "Resource representations for OpenCompany activity."
  (:require [cheshire.core :as json]
            [clj-time.format :as format]
            [clj-time.core :as t]
            [oc.lib.hateoas :as hateoas]
            [oc.lib.db.common :as db-common]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.org :as org-rep]
            [oc.storage.representations.entry :as entry-rep]
            [oc.storage.config :as config]))

(defn url [{slug :slug} {start :start direction :direction}]
  (str "/orgs/" slug "/activity?start=" start "&direction=" (name direction)))

(defn- comments
  "Return a sequence of just the comments for an entry."
  [{interactions :interactions}]
  (filter :body interactions))

(defn- reactions
  "Return a sequence of just the reactions for an entry."
  [{interactions :interactions}]
  (filter :reaction interactions))

(defn- pagination-links
  "Add `next` and/or `prior` links for pagination as needed."
  [org {:keys [start start? direction]} activity]
  (let [activity? (not-empty activity)
        last-activity (when activity? (:created-at (last activity)))
        first-activity (when activity? (:created-at (first activity)))
        next-url (when activity? (url org {:start last-activity :direction :before}))
        next-link (when next-url (hateoas/link-map "next" hateoas/GET next-url {:accept mt/activity-collection-media-type}))
        prior-url (when (and start? activity?) (url org {:start first-activity :direction :after}))
        prior-link (when prior-url (hateoas/link-map "previous" hateoas/GET prior-url {:accept mt/activity-collection-media-type}))]
    (remove nil? [next-link prior-link])))

(defn- calendar-link [start org]
  (hateoas/self-link (url org {:start start :direction :around}) {:accept mt/activity-collection-media-type}))

(defn- last-minute [year month]
  (t/plus (t/last-day-of-the-month year month) (t/hours 23) (t/minutes 59) (t/seconds 59)))

(defn- calendar-month [year month org]
  (let [iso-month (format/unparse db-common/timestamp-format (last-minute year month))]
    {:month month :year year :links [(calendar-link iso-month org)]}))

(defn- calendar-year
  "Create a map for each year in the calendar with an activity feed link for the year, a sequence of months, and
  an activity feed link for each month."
  [year-data org]
  (let [year (Integer. (first year-data))
        iso-year (format/unparse db-common/timestamp-format (last-minute year 12))
        month-data (map #(Integer. (last %)) (last year-data))
        months (map #(calendar-month year % org) month-data)]
    {:year year :links [(calendar-link iso-year org)] :months months}))

(defn render-activity-list
  "
  Given an org and a sequence of entry and story maps, create a JSON representation of a list of
  activity for the API.
  "
  [params org activity access-level user-id]
  (let [collection-url (url org params)
        links [(hateoas/self-link collection-url {:accept mt/activity-collection-media-type})
               (hateoas/up-link (org-rep/url org) {:accept mt/org-media-type})]
        full-links (concat links (pagination-links org params activity))]
    (json/generate-string
      {:collection {:version hateoas/json-collection-version
                    :href collection-url
                    :links full-links
                    :items (map #(entry-rep/render-entry-for-collection (:slug org) (:board-slug %) %
                              (comments %) (reactions %)
                              access-level user-id) activity)}}
      {:pretty config/pretty?})))

(defn render-activity-calendar
  "Render a JSON map of activity calendar links for the API."
  [org calendar-data access-level user-id]
  (json/generate-string (map #(calendar-year % org) calendar-data)))