(ns oc.storage.auth
  "Uses a magic token to get a valid user token from the auth service"
  (:require [org.httpkit.client :as http]
            [oc.lib.jwt :as jwt]
            [clojure.walk :refer (keywordize-keys)]
            [cheshire.core :as json]
            [oc.storage.config :as config]))

(defn- magic-token
  [user]
  (jwt/generate {:user {:user-id user}
                 :user-id user
                 :super-user true
                 :name "Storage Service"
                 :auth-source :services
                 } config/passphrase))

(def request-user-url
  (str config/auth-server-url "/users/"))

(defn get-options
  [token]
  {:headers {"Content-Type" "application/vnd.open-company.auth.v1+json"
             "Authorization" (str "Bearer " token)}})

(defn user-data [user]
  (let [user-request
        @(http/get (str request-user-url user)
                   (get-options (magic-token user)))]
    (when (= 200 (:status user-request))
      (dissoc (keywordize-keys (json/parse-string (:body user-request))) :links))))
