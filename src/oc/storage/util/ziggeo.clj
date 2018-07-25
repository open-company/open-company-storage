(ns oc.storage.util.ziggeo
  "
   Make a simple GET request for video information from Ziggeo.
   Based on Ziggeo SDK at https://github.com/Ziggeo/ZiggeoPythonSdk

   TODO: Maybe in the future we can create a full clojure SDK.
  "
  (:require [org.httpkit.client :as http]
            [oc.storage.config :as config]))

(defonce ziggeo-api-url "https://srvapi.ziggeo.com/v1")

(defonce auth {:username config/ziggeo-api-token
               :password config/ziggeo-api-key})

(defn get [token]
  
  )