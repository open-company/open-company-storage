(ns open-company.lib.rest-api-mock
  "Utility functions to help with REST API mock testing."
  (:require [clojure.string :as s]
            [defun :refer (defun)]
            [ring.mock.request :refer (request body content-type header)]
            [cheshire.core :as json]
            [open-company.app :refer (app)]
            [open-company.lib.test-setup :as ts]
            [open-company.representations.company :as company-rep]))

;; JWToken for use with QA profile - matches open-company.lib.resources/coyote
(def jwtoken-coyote "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoic2xhY2s6MTIzNDU2IiwibmFtZSI6ImNveW90ZSIsInJlYWwtbmFtZSI6IldpbGUgRS4gQ295b3RlIiwiYXZhdGFyIjoiaHR0cDpcL1wvd3d3LmVtb3RpY29uc3dhbGxwYXBlcnMuY29tXC9hdmF0YXJcL2NhcnRvb25zXC9XaWxleS1Db3lvdGUtRGF6ZWQuanBnIiwiZW1haWwiOiJ3aWxlLmUuY295b3RlQGFjbWUuY29tIiwib3duZXIiOmZhbHNlLCJhZG1pbiI6ZmFsc2UsIm9yZy1pZCI6InNsYWNrOjk4NzY1In0.1gQWBUhsfWjmvwWeK_BiyjVLbryKTAVNElj5BJkoH0o")
(def jwtoken jwtoken-coyote)
(def jwtoken-bad (take 30 jwtoken))
;; JWToken for use with QA profile - matches open-company.lib.resources/camus
(def jwtoken-camus "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6ImFsYmVydEBjb21iYXQub3JnIiwiYm90Ijp7ImlkIjoiYWJjIiwidG9rZW4iOiJ4eXoifSwiYWRtaW4iOnRydWUsIm5hbWUiOiJjYW11cyIsIm9yZy1pZCI6InNsYWNrOjk4NzY1IiwidXNlci1pZCI6InNsYWNrOjE5NjAtMDEtMDQiLCJhdmF0YXIiOiJodHRwOlwvXC93d3cuYnJlbnRvbmhvbG1lcy5jb21cL3dwLWNvbnRlbnRcL3VwbG9hZHNcLzIwMTBcLzA1XC9hbGJlcnQtY2FtdXMxLmpwZyIsIm93bmVyIjp0cnVlLCJyZWFsLW5hbWUiOiJBbGJlcnQgQ2FtdXMifQ.-vPPX8iTI5iNzZIXr9HyVqdox5hWrzVfyh0ODNb-xVk")

;; JWToken for use with QA profile - matches open-company.lib.resources/sartre
(def jwtoken-sartre "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoic2xhY2s6MTk4MC0wNi0yMSIsIm5hbWUiOiJzYXJ0cmUiLCJyZWFsLW5hbWUiOiJKZWFuLVBhdWwgU2FydHJlIiwiYXZhdGFyIjoiaHR0cDpcL1wvZXhpc3RlbnRpYWxpc210b2RheS5jb21cL3dwLWNvbnRlbnRcL3VwbG9hZHNcLzIwMTVcLzExXC9zYXJ0cmVfMjIuanBnIiwiZW1haWwiOiJzYXJ0cmVAbHljZWVsYS5vcmciLCJvd25lciI6dHJ1ZSwiYWRtaW4iOnRydWUsIm9yZy1pZCI6InNsYWNrOjg3NjU0In0.Cneyfu5WFHvgSCyV4wn-L-ztZ5q_vu1ElnbShCA8Y9w")

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
        auth (or (:auth options) jwtoken)
        headers (:headers options)
        headers-charset (if (:skip-charset options) headers (assoc headers :Accept-Charset "utf-8"))
        headers-auth (if (:skip-auth options) headers-charset (assoc headers-charset :Authorization auth))
        headers-request (apply-headers initial-request headers-auth)
        body-value (:body options)
        body-request (if (:skip-body options) headers-request (body headers-request (json/generate-string body-value)))]
    ((-> ts/test-system deref :handler :handler) body-request))))

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