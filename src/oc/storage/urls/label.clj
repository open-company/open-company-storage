(ns oc.storage.urls.label
  (:require [oc.storage.urls.org :as org-urls]
            [clojure.string :as string]
            [defun.core :refer (defun)]))

;; Pins

(defn labels [org]
  (org-urls/org-container org :labels))

(defun label
  ([org label-map :guard map?]
   (label org (:uuid label-map)))
  ([org label-uuid :guard string?]
   (string/join "/" [(labels org) label-uuid])))

(defun label-entries
  ([org label-map :guard map?]
   (label-entries org (:slug label-map)))
  ([org label-slug :guard string?]
   (string/join "/" [(label org label-slug) "entries"])))