(ns oc.storage.urls.activity
    (:require [oc.storage.urls.org :as org-urls]
              [oc.storage.urls.label :as label-urls]
              [cuerdas.core :as string]))

(def allowed-parameter-keys  [:start :direction :following :unfollowing :refresh])

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

  ([org collection-type]
   (org-urls/org-container org collection-type))

  ([org collection-type url-params]
   (parametrize (collection org collection-type) url-params)))

(defn activity ;; url
  ([org collection-type]
   (collection org collection-type))

  ([org collection-type url-params]
   (collection org collection-type url-params)))

(defn replies
  ([org] (org-urls/replies org))
  ([org url-params]
   (parametrize (replies org) url-params)))

(defn bookmarks
  ([org] (org-urls/bookmarks org))
  ([org url-params]
   (parametrize (bookmarks org) url-params)))

(defn follow
  ([org collection-type]
   (org-urls/org-container org collection-type))

  ([org collection-type url-params]
   (parametrize (follow org collection-type) url-params)))

(defn following
  ([org collection-type]
   (follow org collection-type {:following true}))

  ([org collection-type params]
   (follow org collection-type params)))

(defn unfollowing
  ([org collection-type]
   (follow org collection-type {:unfollowing true}))

  ([org collection-type params]
   (follow org collection-type params)))

(defn contributions

  ([org author-uuid]
   (org-urls/contribution org author-uuid))

  ([org author-uuid url-params]
   (parametrize (contributions org author-uuid) url-params)))


(defn label-entries

  ([org label-slug]
   (label-urls/label-entries org label-slug))

  ([org label-slug url-params]
   (parametrize (label-entries org label-slug) url-params)))