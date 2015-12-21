(ns open-company.api.common
  (:require [taoensso.timbre :refer (debug info warn error fatal spy)]
            [clojure.string :as s]
            [cheshire.core :as json]
            [liberator.representation :refer (ring-response)]
            [liberator.core :refer (by-method)]
            [open-company.lib.jwt :as jwt]
            [open-company.resources.company :as company]))

(def UTF8 "utf-8")

(def malformed true)
(def good-json false)

;; ----- Utility functions -----

(defn- name-for
  "Replace :name in the map with :real-name if it's not blank."
  [user]
  (if (s/blank? (:real-name user))
    user
    (assoc user :name (:real-name user))))

(defn- author-for
  "Extract the :avatar/:image, :user-id and :name (the author fields) from the JWToken claims."
  [user]
  (-> user
    (name-for)
    (select-keys [:avatar :user-id :name])
    (clojure.set/rename-keys {:avatar :image})))

;; ----- Responses -----

(defn options-response [methods]
  (ring-response {
    :status 204
    :headers {"Allow" (s/join ", " (map s/upper-case (map name methods)))}}))

(defn missing-response
  ([]
  (ring-response {
    :status 404
    :body ""
    :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}}))
  ([reason]
  (ring-response {
    :status 404
    :body reason
    :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}})))

(def unauthorized "Not authorized. Provide a Bearer JWToken in the Authorization header.")
(defn unauthorized-response []
  (ring-response {
    :status 401
    :body unauthorized
    :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}}))

(def forbidden "Forbidden. Provide a Bearer JWToken in the Authorization header that is allowed to do this operation.")
(defn forbidden-response []
  (ring-response {
    :status 403
    :body forbidden
    :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}}))

(defn unprocessable-entity-response [reason]
  (ring-response
    {:status 422
      :body reason
      :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}}))

(defn only-accept [status media-type]
  (ring-response
    {:status status
     :body (format "Acceptable media type: %s\nAcceptable charset: %s" media-type UTF8)
     :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}}))

(defn location-response [path-parts body media-type]
  (ring-response
    {:body body
     :headers {"Location" (format "/%s" (s/join "/" path-parts))
               "Content-Type" (format "%s;charset=%s" media-type UTF8)}}))

;; ----- Validations -----

(defn malformed-json?
  "Read in the body param from the request as a string, parse it into JSON, make sure all the
  keys are keywords, and then return it, mapped to :data as the 2nd value in a vector,
  with the first value indicating it's not malformed. Otherwise just indicate it's malformed."
  [ctx]
  (try
    (if-let [data (-> (get-in ctx [:request :body]) slurp (json/parse-string true))]
      ; handle case of a string which is valid JSON, but still malformed for us
      (do (when-not (map? data) (throw (Exception.)))
        [good-json {:data data}])
      malformed)
    (catch Exception e
      (debug "Request body not processable as JSON: " e)
      malformed)))

(defn known-content-type?
  [ctx content-type]
  (if-let [request-type (get-in ctx [:request :headers "content-type"])]
    (= (first (s/split content-type #";")) (first (s/split request-type #";")))
    true))

(defn check-input [check]
  (if (= check true) true [false {:reason check}]))

;; ----- Authentication and Authorization -----

(defn authenticated?
  "Check for the presence and validity of a JWToken in the Authorization header."
  ([headers] (authenticated? headers false)) ; false to require a JWToken
  ([headers default-response]
  (if-let [authorization (or (headers "Authorization") (headers "authorization"))]
    (let [jwtoken (last (s/split authorization #" "))]
      (if (jwt/check-token jwtoken) {:jwtoken jwtoken} false))
    default-response)))

(defn authorized-to-company?
  "Return true if the user is authorized to this company, false if not."
  [ctx]
  (let [company-org (get-in ctx [:company :org-id])
        user-org (get-in ctx [:user :org-id])]
    (and (not (nil? user-org)) (= company-org user-org))))

(defn- authorize
  "
  If a user is authorized to this company, or the request is for anonymous access,
  add the user's details to the Liberator context at :user and authorship properties at :author,
  otherwise return false if the user isn't authorized to the company org and it's not anonymous.
  "

  ([company jwtoken] (authorize company jwtoken false))

  ([company jwtoken allow-anonymous]
  (let [decoded (jwt/decode jwtoken)
        user (:claims decoded)
        author (author-for user)]
    ;; company organization and user organization are allowed not to match if:
    ;; - anonymous access is allowed for this operation
    ;; - there is no company for this operation (meaning usually a 404)
    (if (or allow-anonymous (nil? company) (authorized-to-company? {:company company :user user}))
      {:user user :author author}
      false))))

(defn allow-anonymous
  "Allow unless there is a JWToken provided and it's invalid"
  [company-slug ctx]
  (if-let [jwtoken (:jwtoken ctx)]
    (authorize (company/get-company company-slug) jwtoken true)
    true))

(defn allow-org-members
  "Allow only if the user's JWToken indicates membership in the company's org"
  [company-slug ctx]
  (if-let [jwtoken (:jwtoken ctx)]
    (authorize (company/get-company company-slug) jwtoken)
    false))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; verify validity of JWToken if it's provided, but it's not required
(def anonymous-resource {
  :authorized? (fn [ctx] (authenticated? (get-in ctx [:request :headers]) true))
  :handle-unauthorized (fn [_] (unauthorized-response))
  :handle-forbidden (by-method {
    :options (forbidden-response)
    :get (forbidden-response)
    :post (fn [ctx] (if (:jwtoken ctx) (forbidden-response) (unauthorized-response)))
    :put (fn [ctx] (if (:jwtoken ctx) (forbidden-response) (unauthorized-response)))
    :patch (fn [ctx] (if (:jwtoken ctx) (forbidden-response) (unauthorized-response)))
    :delete (fn [ctx] (if (:jwtoken ctx) (forbidden-response) (unauthorized-response)))})})

;; verify validity and presence of required JWToken
(def authenticated-resource {
  :authorized? (fn [ctx] (authenticated? (get-in ctx [:request :headers])))
  :handle-not-found (fn [_] (missing-response))
  :handle-unauthorized (fn [_] (unauthorized-response))
  :handle-forbidden (fn [_] (forbidden-response))})

(def open-company-resource {
  :available-charsets [UTF8]
  :handle-not-found (fn [_] (missing-response))
  :handle-not-implemented (fn [_] (missing-response))
  :allowed-methods [:options :get :put :patch :delete]
  :respond-with-entity? (by-method {
    :options false
    :get true
    :put true
    :patch true
    :delete false})
  :malformed? (by-method {
    :options false
    :get false
    :delete false
    :put (fn [ctx] (malformed-json? ctx))
    :patch (fn [ctx] (malformed-json? ctx))})
  :can-put-to-missing? (fn [_] false)
  :conflict? (fn [_] false)})

(def open-company-anonymous-resource (merge open-company-resource anonymous-resource))

(def open-company-authenticated-resource (merge open-company-resource authenticated-resource))