;; productive set of development namespaces (Clojure API)
(require '[rethinkdb.query :as r])
(require '[open-company.config :as c])
(require '[open-company.db.init :as db] :reload)
(require '[open-company.lib.slugify :as slug] :reload)
(require '[open-company.resources.common :as common] :reload)
(require '[open-company.resources.company :as company] :reload)
(require '[open-company.resources.section :as section] :reload)

;; productive set of development namespaces (REST API)
(require '[open-company.representations.company :as company-rep] :reload)
(require '[open-company.representations.section :as section-rep] :reload)
(require '[ring.mock.request :refer (request body content-type header)])
(require '[open-company.lib.rest-api-mock :refer (api-request)] :reload)
(require '[open-company.app :refer (app)] :reload)

;; Init a DB
(db/init)

;; Create a company
(company/create-company {
  :name "Blank Inc."
  :currency "GBP"})

(company/create-company {
  :name "Transparency, LLC"
  :slug "transparency"
  :currency "FKP"
  :finances {
    :data [
      {:period "2015-09" :cash 66981 :revenue 0 :costs 8019}
    ]
  }})

(company/create-company {
  :name "Buffer"
  :currency "USD"
  :update {
    :title "Founder's Update"
    :body "It's all good!"
  }
  :finances {
    :data [
      {:period "2015-09" :cash 1182329 :revenue 1215 :costs 28019}
      {:period "2015-09" :cash 1209133 :revenue 977 :costs 27155}
    ]
    :commentary {
      :body "Good stuff! Revenue is up."
    }
  }})

;; List companies
(company/list-companies)

;; Get a company
(company/get-company "blank-inc")
(company/get-company "transparency")
(company/get-company "buffer")

;; Update a company


;; Create a section
(section/create-section "blank-inc" "finances" (common/current-timestamp) {:data [{:period "2015-09" :cash 66981 :revenue 0 :costs 8019}]})

;; List sections
(section/list-sections "blank-inc")

(section/list-sections "transparency")

(section/list-sections "buffer" "updates")
(section/list-sections "buffer" "finances")

;; Delete a company
(company/delete-company "transparency")

(company/delete-all-companies!)

;; make a (fake) REST API request
(api-request :get "/companies/buffer" {:headers {:Accept (company-rep/media-type)}})

;; print last exception
(print-stack-trace *e)