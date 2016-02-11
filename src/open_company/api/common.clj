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
      [malformed])
    (catch Exception e
      (debug "Request body not processable as JSON: " e)
      [malformed])))

(defn known-content-type?
  [ctx content-type]
  (if-let [request-type (get-in ctx [:request :headers "content-type"])]
    (= (first (s/split content-type #";")) (first (s/split request-type #";")))
    true))

(defn check-input [check]
  (if (= check true) true [false {:reason check}]))

(defn check->liberator
  "Given a desired return value and the result of a schema/check function return
   a vector that can be used as result for Liberators processable/allowed/.. checks"
  [correct check-result]
  (if (nil? check-result)
    [correct]
    [(not correct) {:reason check-result}]))

;; ----- Authentication and Authorization -----

(defn authenticated?
  "Return true if the request contains a valid JWToken"
  [ctx]
  (and (:jwtoken ctx) (:user ctx)))

(defn authorized-to-company?
  "Return true if the user is authorized to this company, false if not."
  [ctx]
  (let [company-org (get-in ctx [:company :org-id])
        user-org (get-in ctx [:user :org-id])]
    (and (not (nil? user-org)) (= company-org user-org))))

(defn- read-token
  "Read supplied jwtoken from headers.

   If a valid token is supplied return a map containing :jwtoken and associated :user.
   If invalid token is supplied return {:jwtoken false}.
   If no Authorization headers are supplied return nil."
  [headers]
  (if-let [authorization (or (get headers "Authorization") (get headers "authorization"))]
    (let [jwtoken (last (s/split authorization #" "))]
      (if (jwt/check-token jwtoken)
        {:jwtoken jwtoken
         :user    (:claims (jwt/decode jwtoken))}
        {:jwtoken false}))))

(defn allow-anonymous
  "Allow unless there is a JWToken provided and it's invalid."
  [ctx]
  (boolean (or (nil? (:jwtoken ctx)) (:jwtoken ctx))))

(defn allow-authenticated
  "Allow unless there is a JWToken provided and it's invalid"
  [ctx]
  (authenticated? ctx))

(defn allow-org-members
  "Allow only if there is no company, or the user's JWToken indicates membership in the company's org."
  [company-slug ctx]
  (let [user    (:user ctx)
        company (company/get-company company-slug)]
    (cond
      (and user company) (authorized-to-company? {:company company :user user})
      (nil? company)     true
      :else              false)))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; verify validity of JWToken if it's provided, but it's not required
(def anonymous-resource {
  :initialize-context (fn [ctx] (read-token (get-in ctx [:request :headers])))
  :authorized? allow-anonymous
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
  :initialize-context (fn [ctx] (read-token (get-in ctx [:request :headers])))
  :authorized? (fn [ctx] (authenticated? ctx))
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
    :post (fn [ctx] (malformed-json? ctx))
    :put (fn [ctx] (malformed-json? ctx))
    :patch (fn [ctx] (malformed-json? ctx))})
  :can-put-to-missing? (fn [_] false)
  :conflict? (fn [_] false)})

(def open-company-anonymous-resource (merge open-company-resource anonymous-resource))

(def open-company-authenticated-resource (merge open-company-resource authenticated-resource))
