(ns open-company.lib.slugify
  (:require [clojure.string :as s])
  (:import [java.text Normalizer Normalizer$Form]))

(def max-slug-length 128)

(defn- replace-whitespace [slug]
  (s/join "-" (s/split slug #"[\p{Space}]+")))

(defn- normalize-characters [slug]
  (Normalizer/normalize slug Normalizer$Form/NFD))

(defn- replace-punctuation [slug]
  (s/replace slug #"[\p{Punct}]" "-"))

(defn- remove-non-alpha-numeric [slug]
  (s/replace slug #"[^\w^\-]+" ""))

(defn- normalize-dashes [slug]
  (s/replace (s/join "-" (s/split slug #"\-+")) #"^-+" ""))

(defn- truncate [slug n]
  (s/join (take n slug)))

;; Slugify Rules:
;; trim prefixed and trailing white space
;; replace internal white space with dash
;; replace accented characters with normalized characters
;; replace any punctuation with dash
;; remove any remaining non-alpha-numberic characters
;; replace A-Z with a-z
;; replace multiple dashes with dash and dash at the beginning and end with nothing
;; truncate
;; replace dash at the end with nothing (in case we left a - at the end by truncating)
(defn slugify
  ([resource-name] (slugify resource-name max-slug-length))
  ([resource-name max-length]
    (-> resource-name
      s/trim
      replace-whitespace
      normalize-characters
      replace-punctuation
      remove-non-alpha-numeric
      s/lower-case
      normalize-dashes
      (truncate max-length)
      normalize-dashes)))

(defn valid-slug?
  "Return `true` if the specified slug is potentially a valid slug (follows the rules), otherwise return `false`."
  [slug]
  (try
    (= slug (slugify slug)) ; a valid slug slugifys to itself
    (catch Exception e
      false))) ; must not have been a string

(defn ^:private positive-numbers
  ([] (positive-numbers 1))
  ([n] (lazy-seq (cons n (positive-numbers (inc n))))))

(defn find-available-slug
  "Create a slug from `name` but find alternatives
   if the resulting slug is part of the set given as `taken`"
  [name taken]
  {:pre [(set? taken)]}
  (let [base-slug (slugify name)]
    (if (taken base-slug)
      (->> (map #(str base-slug "-" %) (positive-numbers))
           (filter (fn [candidate] (not (contains? taken candidate))))
           first)
      base-slug)))
