(ns open-company.lib.jwt
  (:require [clj-jwt.core :as jwt]
            [taoensso.timbre :as timbre]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [open-company.config :as config]))

(defn expired?
  [jwt-claims]
  (t/after? (t/now) (tc/from-long (:expire jwt-claims))))

(defn check-token
  "Verify a JSON Web Token"
  [token]
  (try
    (let [jwt (-> token jwt/str->jwt)]
      (when (expired? (:claims jwt))
        (timbre/error "Request made with expired JWToken" (:claims jwt)))
      (do
        (-> token jwt/str->jwt (jwt/verify config/passphrase))
        true))
    (catch Exception e
      false)))

(defn decode
  "Decode a JSON Web Token"
  [token]
  (jwt/str->jwt token))