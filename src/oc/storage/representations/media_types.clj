(ns oc.storage.representations.media-types)

;; Org media types
(def org-media-type "application/vnd.open-company.org.v1+json")
(def org-collection-media-type "application/vnd.collection+vnd.open-company.org+json;version=1")
(def org-author-media-type "application/vnd.open-company.org.author.v1")

;; Board media types
(def board-media-type "application/vnd.open-company.board.v1+json")
(def board-collection-media-type "application/vnd.collection+vnd.open-company.board+json;version=1")
(def board-author-media-type "application/vnd.open-company.board.author.v1")
(def board-viewer-media-type "application/vnd.open-company.board.viewer.v1")
(def topic-list-media-type "application/vnd.open-company.topic-list.v1+json")

;; Entry media types
(def entry-media-type "application/vnd.open-company.entry.v1+json")
(def entry-collection-media-type "application/vnd.collection+vnd.open-company.entry+json;version=1")

;; Update media types
(def update-media-type "application/vnd.open-company.update.v1+json")
(def update-collection-media-type "application/vnd.collection+vnd.open-company.update+json;version=1")
(def share-request-media-type "application/vnd.open-company.share-request.v1+json")