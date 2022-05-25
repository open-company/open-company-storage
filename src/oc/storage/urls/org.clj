(ns oc.storage.urls.org
  (:require [defun.core :refer (defun)]
            [cuerdas.core :as s]
            [oc.storage.config :as config]))

(def entry-point "/")

(def orgs (str entry-point "orgs"))

(defun org
  ([slug :guard string?] (s/join "/" [orgs slug]))
  ([org-map :guard map?] (org (:slug org-map))))

(defun org-container
  ([o container-slug :guard string?] (s/join "/" [(org o) container-slug]))
  ([o container-slug-kw :guard keyword?] (org-container o (name container-slug-kw)))
  ([o container-map :guard map?] (org-container o (:slug container-map))))

(defn org-authors [org]
  (str (org-container org :authors) "/"))

(defn org-author [org user-id]
  (str (org-authors org) user-id))

(defn replies [org]
  (org-container org :replies))

(defn entries [org]
  (org-container org :entries))

(defn sample-entries [org]
  (s/join "/" [(entries org) "samples"]))

(defn contributions [org]
  (org-container org :contributions))

(defn contribution [org contribution-id]
  (s/join "/" [(org-container org :contributions) contribution-id]))

(defn digest [org]
  (org-container org :digest))

(defn bookmarks [org]
  (org-container org :bookmarks))

(defn inbox [org]
  (org-container org :inbox))

(defn boards [org]
  (org-container org :boards))

(defn active-users
  [{:keys [team-id]}]
   (s/join "/" [config/auth-server-url "teams" team-id "active-users"]))

(defn wrt-csv
  ([org]
   (org-container org :wrt-csv))
  ([org days]
   (str (wrt-csv org) "?days=" days)))