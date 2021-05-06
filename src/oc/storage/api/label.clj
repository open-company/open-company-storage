(ns oc.storage.api.label
  "Liberator API for entry resources."
  (:require [if-let.core :refer (if-let*)]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [compojure.core :as compojure :refer (ANY)]
            [liberator.core :refer (defresource by-method)]
            [oc.lib.db.pool :as pool]
            [oc.lib.api.common :as api-common]
            [oc.storage.config :as config]
            [oc.storage.api.access :as access]
            [oc.storage.representations.media-types :as mt]
            [oc.storage.representations.label :as label-rep]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.label :as label-res]
            [oc.storage.resources.common :as common-res]
            [oc.storage.urls.label :as label-urls]))

;; Validations

(defn- duplicated-label-name? [conn existing-org label-name exclude-label-uuids]
  (let [existing-labels (label-res/list-labels-by-org conn (:uuid existing-org))
        include-labels (filter #(not ((set exclude-label-uuids) (:uuid %))) existing-labels)
        duplicated-label (some #(when (re-matches (re-pattern (str "(?i)^" (:name %) "$")) label-name) %) include-labels)]
    (when duplicated-label
      (timbre/errorf "Not valid label name %s (excluding %s), duplicate of: %s" label-name exclude-label-uuids duplicated-label)
      {:duplicated-label duplicated-label})))

(defn- valid-new-label? [conn org-slug label-props user]
  (try
    (if-let* [existing-org (org-res/get-org conn org-slug)
              new-label (label-res/->label (:name label-props) (:uuid existing-org) (lib-schema/author-for-user user))]
      {:existing-org (api-common/rep existing-org)
       :new-label (api-common/rep new-label)}
      (do
        (timbre/error "Failed checking new label props.")
        [false {:reason "Error checking new label props"}]))
    (catch Exception e
      (timbre/errorf "Error creating label with name %s" (:name label-props))
      (timbre/error e)
      [false {:reason (format "Error creating label with name %s" (:name label-props))
              :throwable e}])))

(defn- valid-label-update? [conn org-slug label-uuid label-props]
  (try
    (if-let* [existing-org (org-res/get-org conn org-slug)
              existing-label (label-res/get-label conn label-uuid)]
      (let [updated-label (merge existing-label (label-res/ignore-props label-props))
            updating-label-name? (and (contains? label-props :name)
                                      (not= (:name label-props) (:name existing-label)))
            duplicated-label (when updating-label-name?
                               (duplicated-label-name? conn existing-org (:name label-props) [(:uuid existing-label)]))
            not-valid-update? (schema/check common-res/Label updated-label)]
        (cond duplicated-label
              (let [err-msg (format "Not valid label %s update name %s, is a duplicate of %s (%s)" (:uuid existing-label) (:name label-props) (:name duplicated-label) (:uuid duplicated-label))]
                (timbre/error err-msg)
                [false (merge duplicated-label
                              {:reason err-msg
                               :updated-label updated-label})])
              not-valid-update?
              (do
                (timbre/error "Not valid label update")
                (timbre/error not-valid-update?)
                [false {:reason not-valid-update?
                        :existing-label (api-common/rep existing-label)
                        :updated-label updated-label}])
              :else
              {:existing-org (api-common/rep existing-org)
               :existing-label (api-common/rep existing-label)
               :updated-label (api-common/rep updated-label)}))
      true) ; No org or label for this update, so this will fail existence check later
    (catch clojure.lang.ExceptionInfo e
      [false {:reason (.getMessage e)}])))

;; Actions

(defn create-label [conn new-label]
  (timbre/infof "Creating label %s for org %s" (:name new-label) (:org-uuid new-label))
  (if-let* [label-result (label-res/create-label! conn new-label)
            updated-labels-list (label-res/list-labels-by-org conn (:org-uuid new-label))]
    ;; Label creation succeeded, so create the default boards
    (do
      (timbre/infof "Created label %s, org %s now has %d labels" (:uuid label-result) (:org-uuid new-label) (count updated-labels-list))
      {:created-label (api-common/rep label-result)
       :updated-labels (api-common/rep updated-labels-list)})
    (do
      (timbre/error "Failed creating label %s for org %s" (:name new-label) (:org-uuid new-label))
      false)))

(defn- update-label [conn updated-label]
  (timbre/infof "Updating label %s for org %s" (:uuid updated-label) (:org-uuid updated-label))
  (if-let [update-result (label-res/update-label! conn (:uuid updated-label) updated-label)]
    (do
      (timbre/infof "Updated label %s for org %s" (:uuid update-result) (:org-uuid updated-label))
      {:updated-label (api-common/rep update-result)})

    (do
      (timbre/errorf "Failed updating label %s for org %s" (:uuid updated-label) (:org-uuid updated-label))
      false)))

(defn- delete-org-labels [conn existing-org]
  (timbre/infof "Deleting all labels for org %s" (:org-uuid existing-org))
  (try
    (label-res/delete-org-labels! conn (:uuid existing-org))
    (catch Exception e
      (timbre/errorf "Error deleting all labels for org %s" (:uuid existing-org))
      (timbre/error e)
      [false {:reason (str "Error deleting all labels for org " (:uuid existing-org))
              :error e}])))

(defn- delete-label [conn existing-label]
  (timbre/infof "Deleting label %s for org %s" (:uuid existing-label) (:org-uuid existing-label))
  (if (label-res/delete-label! conn (:uuid existing-label))
    (do
      (timbre/infof "Deleted label %s for org %s" (:uuid existing-label) (:org-uuid existing-label))
      true)
    (do
      (timbre/errorf "Failed deleting label %s for org %s" (:uuid existing-label) (:org-uuid existing-label))
      false)))

;; A resource for adding replies to a poll
(defresource labels [conn org-slug]
  (api-common/open-company-authenticated-resource config/passphrase) ; verify validity and presence of required JWToken

  :allowed-methods [:options :get :post :delete]

  ;; Media type client accepts
  :available-media-types [mt/label-collection-media-type]
  :handle-not-acceptable (by-method {
                          :get (api-common/only-accept 406 mt/label-collection-media-type)
                          :options false
                          :post (api-common/only-accept 406 mt/label-collection-media-type)
                          :delete false})
  
  ;; Media type client sends
  :known-content-type? (by-method {
                          :options true
                          :get true
                          :post (fn [ctx] (api-common/known-content-type? ctx mt/label-media-type))
                          :delete true})

  ;; Authorization
  :allowed? (by-method {
    :options true
    :get (fn [ctx] (access/allow-members conn org-slug (:user ctx)))
    :post (fn [ctx] (access/allow-members conn org-slug (:user ctx)))
    :delete (fn [ctx] (access/allow-admins conn org-slug (:user ctx)))})

  :processable? (by-method {
    :options true
    :get true
    :post (fn [ctx] (valid-new-label? conn org-slug (:data ctx) (:user ctx)))
    :delete true})

  :conflict? (fn [ctx] (duplicated-label-name? conn (:existing-org ctx) (:name (:data ctx)) []))

  ;; Existentialism
  :exists? (by-method {
             :get (fn [ctx] (if-let* [existing-org (or (:existing-org ctx) (org-res/get-org conn org-slug))
                                      existing-labels (or (:existing-labels ctx) (label-res/list-labels-by-org conn (:uuid existing-org)))]
                              {:existing-org (api-common/rep existing-org)
                               :existing-labels (api-common/rep existing-labels)}
                              false))})

  ;; Actions
  :post! (fn [ctx] (create-label conn (:new-label ctx)))

  :delete! (fn [ctx] (delete-org-labels conn (:existing-org ctx)))

  ;; Responses
  :handle-created (fn [ctx] (let [user (:user ctx)
                                  existing-org (:existing-org ctx)
                                  new-label (:created-label ctx)
                                  updated-labels (:updated-labels ctx)]
                              (api-common/location-response (:uuid new-label)
                                                            (label-rep/render-label-list existing-org updated-labels user)
                                                            mt/label-media-type)))
  :handle-ok (fn [ctx] (let [existing-org (:existing-org ctx)
                             existing-labels (:existing-labels ctx)]
                         (label-rep/render-label-list existing-org existing-labels (:user ctx)))))

(defresource label [conn org-slug label-uuid]
  (api-common/open-company-anonymous-resource config/passphrase) ; verify validity of optional JWToken

  :allowed-methods [:options :delete :get :patch]

  ;; Media type client accepts
  :available-media-types [mt/label-media-type]
  :handle-not-acceptable (api-common/only-accept 406 mt/label-media-type)

  ;; Media type client sends
  :known-content-type? (by-method {:options true
                                   :get true
                                   :patch (fn [ctx] (api-common/known-content-type? ctx mt/label-media-type))
                                   :delete true})

  ;; Authorization
  :allowed? (by-method {:options true
                        :get (fn [ctx] (access/allow-members conn org-slug (:user ctx)))
                        :patch (fn [ctx] (access/allow-members conn org-slug (:user ctx)))
                        :delete (fn [ctx] (access/allow-members conn org-slug (:user ctx)))})

  ;; Validations
  :processable? (by-method {:options true
                            :get true
                            :patch (fn [ctx] (valid-label-update? conn org-slug label-uuid (:data ctx)))
                            :delete true})

  ;; Existentialism
  :exists? (fn [ctx] (if-let* [user (:user ctx)
                               existing-org (or (:existing-org ctx) (org-res/get-org conn org-slug))
                               existing-user-labels (or (:existing-user-labels ctx) (label-res/list-labels-by-org-user conn (:uuid existing-org) (:user-id user)))
                               existing-label (or (:existing-label ctx) (label-res/get-label conn label-uuid))]
                              {:existing-org (api-common/rep existing-org)
                               :existing-user-labels (api-common/rep existing-user-labels)
                               :existing-label (api-common/rep existing-label)}
                              false))

  ;; Actions
  :patch! (fn [ctx] (update-label conn (:updated-label ctx)))
  :delete! (fn [ctx] (delete-label conn (:existing-label ctx)))

  ;; Responses
  :handle-ok (fn [ctx] (let [user (:user ctx)
                             existing-org (:existing-org ctx)
                             return-label (or (:updated-label ctx) (:existing-label ctx))]
                         (label-rep/render-label existing-org return-label user))))

;; ----- Routes -----

(defn routes [sys]
  (let [db-pool (-> sys :db-pool :pool)]
    (compojure/routes
     (ANY (label-urls/labels ":org-slug")
       [org-slug]
       (pool/with-pool [conn db-pool]
         (labels conn org-slug)))
     (ANY (str (label-urls/labels ":org-slug") "/")
       [org-slug]
       (pool/with-pool [conn db-pool]
         (labels conn org-slug)))
     (ANY (label-urls/label ":org-slug" ":label-uuid")
       [org-slug label-uuid]
       (pool/with-pool [conn db-pool]
         (label conn org-slug label-uuid)))
     (ANY (str (label-urls/label ":org-slug" ":label-uuid") "/")
       [org-slug label-uuid]
       (pool/with-pool [conn db-pool]
         (label conn org-slug label-uuid))))))
