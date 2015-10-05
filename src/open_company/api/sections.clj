(ns open-company.api.sections
  (:require [compojure.core :refer (routes ANY)]
            [liberator.core :refer (defresource by-method)]
            [open-company.api.common :as common]
            [open-company.resources.common :as common-res]
            [open-company.resources.section :as section-res]
            [open-company.representations.section :as section-rep]))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

(defresource section [company-slug section-name]
  common/open-company-resource

  :available-media-types [section-rep/media-type]
  :exists? (fn [_] (seq (section-res/list-sections company-slug section-name)))
  :known-content-type? (fn [ctx] (common/known-content-type? ctx section-rep/media-type))

  )

;; ----- Routes -----

(def section-routes
  (apply routes 
    (map #(ANY (str "/companies/:company-slug/" %) [company-slug] (section company-slug %)) common-res/sections)))