(ns oc.storage.urls.activity
    (:require [oc.storage.urls.org :as org-urls]
              [cuerdas.core :as string]))

(def allowed-parameter-keys  [:start :direction :following :unfollowing])

(defn parametrize [url parameters-dict]
  (let [concat? (#{"?" "&"} (last url))
        has-param? (string/includes? url "?")
        concat-url (cond concat? url
                         has-param? (str url "&")
                         :else (str url "?"))
        url-params (select-keys parameters-dict allowed-parameter-keys)]
    (str concat-url
         (apply str
                (for [[k v] url-params
                      :when (and v k)]
                  (str (if (keyword? k) (name k) k) "=" (if (keyword? v) (name v) v) "&"))))))

;; Urls

(defn collection

  ([collection-type org]
   (org-urls/org-container org collection-type))

  ([collection-type org url-params]
   (parametrize (collection collection-type org) url-params)))

(defn activity ;; url
  ([collection-type {slug :slug}]
   (collection collection-type slug))

  ([collection-type org url-params]
   (parametrize (activity collection-type org) url-params)))

(defn replies
  ([org] (org-urls/replies org))
  ([org url-params]
   (parametrize (replies org) url-params)))

(defn bookmarks
  ([org] (org-urls/bookmarks org))
  ([org url-params]
   (parametrize (bookmarks org) url-params)))

(defn follow
  ([collection-type org-slug]
   (org-urls/org-container org-slug collection-type))

  ([collection-type org-slug url-params]
   (parametrize (follow collection-type org-slug) url-params)))

(defn following
  ([collection-type {slug :slug}]
   (follow collection-type slug {:following true}))

  ([collection-type {slug :slug} params]
   (follow collection-type slug params)))

(defn unfollowing
  ([collection-type {slug :slug}]
   (follow collection-type slug {:unfollowing true}))

  ([collection-type {slug :slug} params]
   (follow collection-type slug params)))

(defn contributions

  ([{slug :slug} author-uuid]
   (org-urls/contribution slug author-uuid))

  ([org author-uuid url-params]
   (parametrize (contributions org author-uuid) url-params)))