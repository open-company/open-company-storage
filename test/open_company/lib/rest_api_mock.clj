(ns open-company.lib.rest-api-mock
  "Utility functions to help with REST API mock testing."
  (:require [clojure.string :as s]
            [defun :refer (defun)]
            [ring.mock.request :refer (request body content-type header)]
            [cheshire.core :as json]
            [open-company.app :refer (app)]
            [open-company.representations.company :as company-rep]))

;; JWToken for use with QA profile
(def jwtoken "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMS00LTE5NjAiLCJuYW1lIjoiY2FtdXMiLCJyZWFsLW5hbWUiOiJBbGJlcnQgQ2FtdXMiLCJhdmF0YXIiOiJodHRwOlwvXC93d3cuYnJlbnRvbmhvbG1lcy5jb21cL3dwLWNvbnRlbnRcL3VwbG9hZHNcLzIwMTBcLzA1XC9hbGJlcnQtY2FtdXMxLmpwZyIsImVtYWlsIjoiYWxiZXJ0QGNvbWJhdC5vcmciLCJvd25lciI6dHJ1ZSwiYWRtaW4iOnRydWV9.hmOdqQ0f-ZWlckVZ0RS2QjL4j1616fHiZL3fzuD5tdI")

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

  ([url] (api-request :get url))

  ([method url] (api-request method url {}))

  ([method url options]
  (let [initial-request (request method url)
        headers (:headers options)
        headers-charset (if (:skip-charset options) headers (merge {:Accept-Charset "utf-8"} headers))
        headers-auth (if (:skip-auth options) headers (merge {:Authorization jwtoken} headers))
        headers-request (apply-headers initial-request headers-auth)
        body-value (:body options)
        body-request (if (:skip-body options) headers-request (body headers-request (json/generate-string body-value)))]
    (app body-request))))

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

(defun put-with-api
  "Makes a mock API request to PUT the resource and returns the response."
  ([url :guard string? media-type body]
     (api-request :put url {
      :headers {
        :Accept-Charset "utf-8"
        :Accept media-type
        :Content-Type media-type}
      :body body}))
  ([headers url body]
     (api-request :put url {
        :headers headers
        :body body})))

(defn put-company-with-api
  "Makes a mock API request to create the company and returns the response."
  ([ticker body]
    (put-with-api (company-rep/url ticker) company-rep/media-type body))
  ([headers ticker body]
    (put-with-api headers (company-rep/url ticker) body)))