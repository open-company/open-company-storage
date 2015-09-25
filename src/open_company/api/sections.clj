(ns open-company.api.sections
  (:require [compojure.core :refer (defroutes ANY)]
            [liberator.core :refer (defresource by-method)]
            [open-company.api.common :as common]
            [open-company.resources.section :as section]
            [open-company.representations.section :as section-rep]))

;; ----- Routes -----

(defroutes section-routes)