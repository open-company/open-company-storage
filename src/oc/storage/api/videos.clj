(ns oc.storage.api.videos
  "Liberator API for ziggeo video resources."
  (:require [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (POST)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.async.notification :as notification]
            [oc.storage.resources.entry :as entry-res]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.config :as config]))

(defn- handle-video-webhook [conn ctx]
  (let [body (:data ctx)
        video (get-in body [:data :video])
        token (:token video)
        entry (if token
                (entry-res/get-entry-by-video conn token)
                false)
        video-changed (:video-processed entry)]
    (timbre/debug (:state_string video) video entry)
    (when entry
      (let [error (some #(= (:state_string video) %) ["EMPTY", "ERROR"])
            updated-result (if-not error
                             (entry-res/update-video-data conn video entry)
                             (entry-res/error-video-data conn entry))
            ;; find org
            org (org-res/get-org conn (:org-uuid entry))
            ;; find board
            board (board-res/get-board conn (:board-uuid entry))]
        (when (not= (:video-processed updated-result) video-changed)
          (notification/send-trigger!
           (notification/->trigger :update org board
                                   {:old entry :new updated-result}
                                   nil nil)))))))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; A resource for operations on a ziggeo video
(defresource video [conn]
  (api-common/open-company-anonymous-resource config/passphrase)

  :allowed-methods [:post]
  ;; Media type client accepts
  :available-media-types ["application/json"]
  :handle-not-acceptable (api-common/only-accept 406 "application/json")

  ;; Actions
  :post! (fn [ctx] (handle-video-webhook conn ctx)))
;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
      ;; Secure UUID access
      (POST "/ziggeo/videos"
        []
        (pool/with-pool [conn db-pool] 
          (video conn))))))

