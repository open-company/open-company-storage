(ns oc.storage.util.ziggeo
  "
   Make a simple GET request for video information from Ziggeo.
   Based on Ziggeo SDK at https://github.com/Ziggeo/ZiggeoPythonSdk

   TODO: Maybe in the future we can create a full clojure SDK.
  "
  (:require [org.httpkit.client :as http]
            [taoensso.timbre :as timbre]
            [cheshire.core :as json]
            [oc.storage.config :as config]))

(defonce ziggeo-api-url "https://srvapi.ziggeo.com/v1")

(defonce auth {:username config/ziggeo-api-token
               :password config/ziggeo-api-key})

(defn auth-options [auth]
  {:headers {
             "Content-Type" "application/json"
             }
   :basic-auth [(:username auth) (:password auth)]})

(defn get [token]
  (let [{:keys [status headers body error] :as resp}
          @(http/get (str ziggeo-api-url "/videos/" token) (auth-options auth))]
    (timbre/debug status error)
    (when (and (> status 199) (< status 500))
      (json/parse-string body))))