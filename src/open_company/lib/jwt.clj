(ns open-company.lib.jwt
  (:require [clj-jwt.core :refer :all]
            [open-company.config :as config]))

(defn generate
  "Get a JSON Web Token from a payload"
  [payload]
  (-> payload
      jwt
      (sign :HS256 config/passphrase)
      to-str))

(defn check-token
  "Verify a JSON Web Token"
  [token]
  (try
    (do
      (-> token
        str->jwt
        (verify config/passphrase))
      true)
    (catch Exception e
      false)))

(defn decode
  "Decode a JSON Web Token"
  [token]
  (str->jwt token))