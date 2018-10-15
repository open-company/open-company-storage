(ns oc.storage.util.strings
  "
  Utilities for processing strings.
  "
  (:require [cuerdas.core :as str]))

(defn strip-tags [data]
  (str/strip-tags data ["script" "style" "input"]))