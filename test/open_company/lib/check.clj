(ns open-company.lib.check
  "Namespace for utility functions used in tests to check that things are as expected."
  (:require [clojure.test :refer (is)]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [defun.core :refer (defun)]))

(defmacro check [forms]
  `(assert (is ~forms)))

(defn time-for [timestamp]
  (if (instance? org.joda.time.DateTime timestamp)
    timestamp
    (f/parse timestamp)))

(defn timestamp?
  "True if the argument is a joda Date/Time instance or can be parsed into one."
  [timestamp]
  (or (instance? org.joda.time.DateTime timestamp)
      (instance? org.joda.time.DateTime (f/parse timestamp))))

(defn about-now?
  "True if the argument is a joda Date/Time instance for a time within the last 10 seconds."
  [timestamp]
  (if (timestamp? timestamp)
    (let [compare-time (time-for timestamp)]
      (t/within? (-> 10 t/seconds t/ago) (t/now) compare-time))
    false))

(defun before?
  "Simple wrapper around clj-time.core/before? that handles type checking and time parsing.
  True if the first timestamp is before the second."

  ([timestamp1 :guard string? timestamp2] (before? (f/parse timestamp1) timestamp2))

  ([timestamp1 timestamp2 :guard string?] (before? timestamp1 (f/parse timestamp2)))

  ([timestamp1 :guard #(instance? org.joda.time.DateTime %) timestamp2 :guard #(instance? org.joda.time.DateTime %)]
  (if (= timestamp1 timestamp2)
    false
    (t/before? timestamp1 timestamp2))))

(defn delay-secs
  [seconds]
  (Thread/sleep (* seconds 1000)))