;; productive set of development namespaces (Clojure API)
(require '[rethinkdb.query :as r])
(require '[open-company.config :as c])
(require '[open-company.resources.company :as company] :reload)
(require '[open-company.resources.report :as report] :reload)

;; productive set of development namespaces (REST API)
(require '[ring.mock.request :refer (request body content-type header)])
(require '[open-company.lib.rest-api-mock :refer (api-request)] :reload)
(require '[open-company.app :refer (app)] :reload-all)

;; Create a company
(company/create-company {:name "Transparency, LLC" :symbol "OPEN" :url "https://opencompany.io"})

;; Get a company
(company/get-company "OPEN")

;; Update a company
(company/update-company "OPEN" {:name "Transparency, LLC" :symbol "TRAN" :url "https://opencompany.io/"})

;; Delete a company
(company/delete-company "TRAN")

;; make a (fake) REST API request
(api-request :get "/v1/companies/OPEN" {:headers {:Accept (company/media-type)}})

;; print last exception
(print-stack-trace *e)