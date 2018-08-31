(ns oc.storage.api.videos
  "Liberator API for ziggeo video resources."
  (:require [taoensso.timbre :as timbre]
            [compojure.core :as compojure :refer (POST)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.auth :as auth]
            [oc.storage.api.entries :as entries-api]
            [oc.storage.async.notification :as notification]
            [oc.storage.resources.entry :as entry-res]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]
            [oc.storage.config :as config]))

(defn- update-video-data
  [conn org board video entry]
  (if-not (= (:state_string video) "ERROR")
    (let [user (auth/user-data (:user-id (first (:author entry))))
          ctx {:user (assoc user :name (str (:first-name user) " "
                                            (:last-name user)))
               :existing-org org
               :existing-board board
               :existing-entry entry}
          updated-entry (entry-res/update-video-data conn video entry)]
      (when (= (:status updated-entry) "published")
        (try
          (entries-api/auto-share-on-publish conn ctx updated-entry)
          (catch Exception e (timbre/error "caught exception: " (.getMessage e)))))
      updated-entry)
    (entry-res/error-video-data conn entry)))

(defn- handle-video-webhook [conn ctx]
  (let [body (:data ctx)
        video (get-in body [:data :video])
        token (:token video)
        entry (if token
                (entry-res/get-entry-by-video conn token)
                false)
        video-changed (:video-processed entry)]
    (when entry
      (let [;; find org
            org (org-res/get-org conn (:org-uuid entry))
            ;; find board
            board (board-res/get-board conn (:board-uuid entry))
            updated-result (update-video-data conn org board video entry)]
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

