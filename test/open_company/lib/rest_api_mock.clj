(ns open-company.lib.rest-api-mock
  "Utility functions to help with REST API mock testing."
  (:require [clojure.string :as s]
            [open-company.app :refer (app)]
            [ring.mock.request :refer (request body content-type header)]
            [cheshire.core :as json]))

(defn base-mime-type [full-mime-type]
  (first (s/split full-mime-type #";")))

(defn response-mime-type [response]
  (base-mime-type (get-in response [:headers "Content-Type"])))

(defn response-location [response]
  (get-in response [:headers "Location"]))

(defn- apply-headers
  "Add the map of headers to the ring mock request."
  [request headers]
  (if (= headers {})
    request
    (let [key (first (keys headers))]
      (recur (header request key (get headers key)) (dissoc headers key)))))

(defn api-request
  "Pretends to execute a REST API request using ring mock."
  [method url options]
  (let [initial-request (request method url)
        headers (:headers options)
        headers-charset (if (:skip-charset options) headers (merge {:Accept-Charset "utf-8"} headers))
        headers-request (apply-headers initial-request headers-charset)
        body-value (:body options)
        body-request (if (:skip-body options) headers-request (body headers-request (json/generate-string body-value)))]
    (app body-request)))

(defn body-from-response
  "Return just the parsed JSON body from an API REST response, or return the raw result
  if it can't be parsed as JSON."
  [resp-map]
  (try
    ;; treat the body as JSON
    (json/parse-string (:body resp-map) true)
    (catch Exception e
      ;; must not be valid JSON
      (:body resp-map))))

(defn json?
  "True if the body of the response contains JSON."
  [resp]
  (map? (body-from-response resp)))

(defn body?
  "True if the body of the response contains anything."
  [resp-map]
  (not (s/blank? (:body resp-map))))