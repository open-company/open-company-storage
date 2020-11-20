(ns oc.storage.urls.org
  (:require [defun.core :refer (defun)]
            [oc.storage.config :as config]))

(def entry-point "/")

(def orgs (str entry-point "orgs"))

(defun org
  ([slug :guard string?] (str orgs "/" slug))
  ([org-map :guard map?] (org (:slug org-map))))

(defun org-container
  ([slug :guard string? container-slug :guard string?] (str (org slug) "/" container-slug))
  ([slug :guard string? container-slug-kw :guard keyword?] (org-container slug (name container-slug-kw)))
  ([org-map :guard map? container-slug] (org-container (:slug org-map) container-slug)))

(defun org-authors
  ([slug :guard string?] (str (org-container slug :authors) "/"))
  ([org-map :guard map?] (org-authors (:slug org-map))))

(defun org-author
  ([slug :guard string? user-id :guard string?] (str (org-authors slug) user-id))
  ([org-map :guard map? user-id :guard string?] (org-author (:slug org-map) user-id)))

(defun replies
  ([slug :guard string?] (org-container slug :replies))
  ([org-map :guard map?] (replies (:slug org-map))))

(defun entries
  ([slug :guard string?] (org-container slug :entries))
  ([org-map :guard map?] (entries (:slug org-map))))

(defun sample-entries
  ([slug :guard string?] (str (entries slug) "/samples"))
  ([org-map :guard map?] (sample-entries (:slug org-map))))

(defun contributions
  ([slug :guard string?] (org-container slug :contributions))
  ([org-map :guard map?] (contributions (:slug org-map))))

(defun contribution
  ([slug :guard string? contribution-id :guard string?] (str (org-container slug :contributions) "/" contribution-id))
  ([org-map :guard map? contribution-id :guard string?] (contribution (:slug org-map) contribution-id)))

(defun digest
  ([slug :guard string?] (org-container slug :digest))
  ([org-map :guard map?] (digest (:slug org-map))))

(defun bookmarks
  ([slug :guard string?] (org-container slug :bookmarks))
  ([org-map :guard map?] (bookmarks (:slug org-map))))

(defun inbox
  ([slug :guard string?] (org-container slug :inbox))
  ([org-map :guard map?] (inbox (:slug org-map))))

(defun boards
  ([slug :guard string?] (org-container slug :boards))
  ([org-map :guard map?] (boards (:slug org-map))))

(defn active-users
  [{:keys [team-id]}] (str config/auth-server-url "/teams/" team-id "/active-users"))