(ns open-company.lib.jwt
  (:require [clj-jwt.core :as jwt]
            [taoensso.timbre :as timbre]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [open-company.config :as config]))

(defn expired?
  [jwt-claims]
  (if-let [expire (:expire jwt-claims)]
    (t/after? (t/now) (tc/from-long expire))
    (timbre/error "No expire field found in JWToken" jwt-claims)))

(defn check-token
  "Verify a JSON Web Token"
  [token]
  (try
    (let [jwt (jwt/str->jwt token)]
      (when (expired? (:claims jwt))
        (timbre/error "Request made with expired JWToken" (:claims jwt)))
      (boolean (jwt/verify jwt config/passphrase)))
    (catch Exception e
      false)))

(defn decode
  "Decode a JSON Web Token"
  [token]
  (jwt/str->jwt token))