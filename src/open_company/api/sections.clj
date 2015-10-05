(ns open-company.api.sections
  (:require [compojure.core :refer (routes make-route ANY)]
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

; (defroutes section-routes
  ; (ANY (str "/companies/:company-slug/:" section-name) [:company-slug :section-name]
  ;   (section company-slug section-name)))
(def section-routes
  ;; https://www.reddit.com/r/Clojure/comments/2rs3ye/problem_defining_routes_dynamically_with_compojure/
  (apply routes
    (map (fn [section-name] (make-route :get (str "/companies/:company-slug/:" section-name)
      section)) (common-res/sections))))