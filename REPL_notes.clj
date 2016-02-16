;; productive set of development namespaces (Clojure API)
(require '[rethinkdb.query :as r])
(require '[schema.core :as schema])
(require '[open-company.config :as c])
(require '[open-company.db.init :as db] :reload)
(require '[open-company.db.pool :as pool] :reload)
(require '[open-company.lib.slugify :as slug] :reload)
(require '[open-company.resources.common :as common] :reload)
(require '[open-company.resources.company :as company] :reload)
(require '[open-company.resources.section :as section] :reload)

;; productive set of development namespaces (REST API)
(require '[cheshire.core :as json])
(require '[open-company.representations.company :as company-rep] :reload)
(require '[open-company.representations.section :as section-rep] :reload)
(require '[ring.mock.request :refer (request body content-type header)])
(require '[open-company.lib.rest-api-mock :refer (api-request)] :reload)
(require '[open-company.app :refer (app)] :reload)

;; make a (fake) REST API request
(api-request :get "/companies/buffer" {:headers {:Accept (company-rep/media-type)}})

;; print last exception
(print-stack-trace *e)

;; Validate sections in sections.json against schema (should be all nils)
(def sections*
  (->> common/sections (map #(assoc % :company-slug "x"))))
(map #(schema/check common/Section %) sections*)