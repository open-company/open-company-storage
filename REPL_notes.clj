;; productive set of development namespaces (Clojure API)
(require '[rethinkdb.query :as r])
(require '[open-company.config :as c])
(require '[open-company.resources.common :as common] :reload)
(require '[open-company.resources.company :as company] :reload)
(require '[open-company.resources.report :as report] :reload)

;; productive set of development namespaces (REST API)
(require '[open-company.representations.company :as company-rep] :reload)
(require '[open-company.representations.report :as report-rep] :reload)
(require '[ring.mock.request :refer (request body content-type header)])
(require '[open-company.lib.rest-api-mock :refer (api-request)] :reload)
(require '[open-company.app :refer (app)] :reload)

;; Create a company
(company/create-company {:symbol "OPEN" :name "Transparency, LLC" :url "https://opencompany.io"})
(company/create-company {:symbol "BUFFR" :name "Buffer" :url "https://open.bufferapp.com/"})

;; List companies
(company/list-companies)

;; Get a company
(company/get-company "OPEN")

;; Update a company
(company/update-company {:symbol "OPEN" :name "Transparency, LLC" :url "https://opencompany.io/"})

;; Create a report
(report/create-report {:symbol "OPEN" :year 2015 :period "Q2" :headcount {:founders 2 :contractors 1}})

;; Update a report
(report/update-report {:symbol "OPEN" :year 2015 :period "Q2" :headcount {:founders 2 :contractors 2}})

;; Get a report
(report/get-report "OPEN" 2015 "Q2")

;; Delete a report
(report/delete-report "OPEN" 2015 "Q2")

;; Delete a company
(company/delete-company "OPEN")
(company/delete-company "BUFFR")

;; make a (fake) REST API request
(api-request :get "/v1/companies/OPEN" {:headers {:Accept (company-rep/media-type)}})

;; print last exception
(print-stack-trace *e)