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
(require '[open-company.resources.stakeholder-update :as su] :reload)

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

;; RethinkDB usage
(def conn2 [:host "127.0.0.1" :port 28015 :db "open_company"])

(aprint (with-open [c (apply r/connect conn2)]
  (-> (r/table "companies")
      (r/get "buffer")
      (r/run c))))

(with-open [c (apply r/connect conn2)]
  (-> (r/table "companies")
      (r/get "open")
      (r/update {:categories ["progress" "company"]})
      (r/run c)))

(with-open [c (apply r/connect conn2)]
  (-> (r/table "companies")
      (r/get "open")
      (r/update {:stakeholder-update (r/literal {:title "" :sections []})})
      (r/run c)))

(with-open [c (apply r/connect conn2)]
  (-> (r/table "companies")
      (r/get "startup-city")
      (r/update {:public true})
      (r/run c)))

(with-open [c (apply r/connect conn2)]
  (-> (r/table "companies")
      (r/get "open")
      (r/update {:sections (r/literal 
        {:company ["competition" "mission" "values"], :progress ["update" "challenges" "growth" "fundraising" "finances"]})})
      (r/run c)))

(with-open [c (apply r/connect conn2)]
  (-> (r/table "companies")
      (r/get "open")
      (r/replace (r/fn [company]
        (r/without company [:mission])))
      (r/run c)))

(aprint (with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
  (r/get-all ["buffer"] {:index "company-slug"})
  (r/run c))))

(aprint (with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
  (r/get-all [["green-labs" "update"]] {:index "company-slug-section-name"})
  (r/run c))))

(aprint (with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
  (r/get-all ["21c9ddd4-6d1c-47a5-b6c1-1308fed08523"] {:index "id"})
  (r/run c))))

(with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
  (r/get-all ["70733852-21f7-4aba-bff6-de93ce699be2"] {:index "id"})
  (r/update {:image "https://open-company.s3.amazonaws.com/diversity.svg"})
  (r/run c)))

;; for more RethinkDB help, see:
;; https://github.com/apa512/clj-rethinkdb
;; http://apa512.github.io/clj-rethinkdb/
;; https://rethinkdb.com/api/python/