(ns open-company.api.common
  (:require [taoensso.timbre :refer (debug info warn error fatal spy)]
            [clojure.string :as s]
            [cheshire.core :as json]
            [liberator.representation :refer (ring-response)]
            [liberator.core :refer (by-method)]
            [open-company.lib.jwt :as jwt]))

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
  "Extract the :avatar/:image, :user-id and :name (author fields) from the JWToken claims."
  [user]
  (-> user
    (name-for)
    (select-keys [:avatar :user-id :name])
    (clojure.set/rename-keys {:avatar :image})))

;; ----- Responses -----

(defn missing-response
  ([]
    (ring-response {
      :status 404
      :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}}))
  ([reason]
    (ring-response {
      :status 404
      :body reason
      :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}})))

(defn unauthorized-response []
  (ring-response {
    :status 401
    :body "Not authorized. Provide a Bearer JWToken in the Authorization header."
    :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}}))

(defn forbidden-response []
  (ring-response {
    :status 403
    :body "Forbidden. Provide a Bearer JWToken in the Authorization header that is allowed to do this operation."
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

(defn authenticate
  "
  Check for the presence and validity of a JWToken in the Authorization header.

  Return false (by default) if the header isn't present or valid, otherwise return a map to
  add the JWToken to the Liberator context.
  "
  ([headers] (authenticate headers false))
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

(defn authorize
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

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; verify validity of JWToken if it's provided, but it's not required
(def anonymous-resource {
  :authorized? (fn [ctx] (authenticate (get-in ctx [:request :headers]) true))
  :handle-unauthorized (fn [_] (unauthorized-response))
  :handle-forbidden (by-method {
    :get (forbidden-response)
    :post (fn [ctx] (if (:jwtoken ctx) (forbidden-response) (unauthorized-response)))
    :put (fn [ctx] (if (:jwtoken ctx) (forbidden-response) (unauthorized-response)))
    :patch (fn [ctx] (if (:jwtoken ctx) (forbidden-response) (unauthorized-response)))
    :delete (fn [ctx] (if (:jwtoken ctx) (forbidden-response) (unauthorized-response)))})})

;; verify validity and presence of required JWToken
(def authenticated-resource {
  :authorized? (fn [ctx] (authenticate (get-in ctx [:request :headers])))
  :handle-unauthorized (fn [_] (unauthorized-response))
  :handle-forbidden (fn [_] (forbidden-response))})

(def open-company-resource {
  :available-charsets [UTF8]
  :handle-not-found (fn [_] (missing-response))
  :allowed-methods [:get :put :delete :patch]
  :respond-with-entity? (by-method {:put true :patch true :delete false})
  :malformed? (by-method {
    :get false
    :delete false
    :put (fn [ctx] (malformed-json? ctx))
    :patch (fn [ctx] (malformed-json? ctx))})
  :can-put-to-missing? (fn [_] true)
  :conflict? (fn [_] false)})

(def open-company-anonymous-resource (merge open-company-resource anonymous-resource))

(def open-company-authenticated-resource (merge open-company-resource authenticated-resource))