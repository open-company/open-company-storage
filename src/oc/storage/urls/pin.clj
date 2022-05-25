(ns oc.storage.urls.pin
  (:require [oc.storage.urls.entry :as entry-urls]))

;; Pins

(defn pins [org-slug board-slug entry-uuid]
  (str (entry-urls/entry org-slug board-slug entry-uuid) "/pins"))

(defn pin [org-slug board-slug entry-uuid pin-container-uuid]
  (str (pins org-slug board-slug entry-uuid) "/" pin-container-uuid))
