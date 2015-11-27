# [OpenCompany](https://opencompany.io) Platform API

[![MPL License](http://img.shields.io/badge/license-MPL-blue.svg?style=flat)](https://www.mozilla.org/MPL/2.0/)
[![Build Status](http://img.shields.io/travis/open-company/open-company-api.svg?style=flat)](https://travis-ci.org/open-company/open-company-api)
[![Dependency Status](https://www.versioneye.com/user/projects/55e5a34c8c0f62001b0003f3/badge.svg?style=flat)](https://www.versioneye.com/user/projects/55e5a34c8c0f62001b0003f3)
[![Roadmap on Trello](http://img.shields.io/badge/roadmap-trello-blue.svg?style=flat)](https://trello.com/b/3naVWHgZ/open-company-development)


## Overview

> A lack of transparency results in distrust and a deep sense of insecurity.

> -- Dalai Lama

Employees and investors, co-founders and execs, they all want more transparency from their startups, but there's no consensus about what it means to be transparent. OpenCompany is a platform that simplifies how key business information is shared with stakeholders.

When information about growth, finances, ownership and challenges is shared transparently, it inspires new ideas and new levels of stakeholder engagement. OpenCompany makes it easy for founders to engage with employees and investors, creating a sense of ownership and urgency for everyone.

[OpenCompany.io](https://opencompany.io) is GitHub for the rest of your company.

To maintain transparency, OpenCompany information is always accessible and easy to find. Being able to search or flip through prior updates empowers everyone. Historical context brings new employees and investors up to speed, refreshes memories, and shows how the company is evolving over time.

Transparency expectations are changing. Startups need to change as well if they are going to attract and retain savvy employees and investors. Just as open source changed the way we build software, transparency changes how we build successful startups with information that is open, interactive, and always accessible. The OpenCompany platform turns transparency into a competitive advantage.

Like the open companies we promote and support, the [OpenCompany.io](https://opencompany.io) platform is completely transparent. The company supporting this effort, Transparency, LLC, is an open company. The [platform](https://github.com/open-company/open-company-web) is open source software, and open company data is [open data](https://en.wikipedia.org/wiki/Open_data) accessible through this platform API.

To get started, head to: [OpenCompany.io](https://opencompany.io)


## Local Setup

Users of the [OpenCompany.io](https://opencompany.io) platform should get started by going to [OpenCompany.io](https://opencompany.io). The following local setup is **for developers** wanting to work on the platform's API software.

Most of the dependencies are internal, meaning [Leiningen](https://github.com/technomancy/leiningen) will handle getting them for you. There are a few exceptions:

* [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) - a Java 8 JRE is needed to run Clojure
* [Leiningen](https://github.com/technomancy/leiningen) 2.5.1+ - Clojure's build and dependency management tool
* [RethinkDB](http://rethinkdb.com/) v2.2.1+ - a multi-modal (document, key/value, relational) open source NoSQL database

#### Java

Chances are your system already has Java 8 installed. You can verify this with:

```console
java -version
```

If you do not have Java 8 [download it](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and follow the installation instructions.

#### Leiningen

Leiningen is easy to install:

1. Download the latest [lein script from the stable branch](https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein).
1. Place it somewhere that's on your $PATH (`env | grep PATH`). `/usr/local/bin` is a good choice if it is on your PATH.
1. Set it to be executable. `chmod 755 /usr/local/bin/lein`
1. Run it: `lein` This will finish the installation.

Then let Leiningen install the rest of the dependencies:

```console
git clone https://github.com/open-company/open-company-api.git
cd open-company-api
lein deps
```

#### RethinkDB

RethinkDB is easy to install with official and community supported packages for most operating systems.

##### RethinkDB for Mac OS X via Brew

Assuming you are running Mac OS X and are a [Homebrew](http://mxcl.github.com/homebrew/) user, use brew to install RethinkDB:

```console
brew update && brew install rethinkdb
```

Follow the instructions provided by brew to run RethinkDB every time at login:

```console
ln -sfv /usr/local/opt/rethinkdb/*.plist ~/Library/LaunchAgents
```

And to run RethinkDB now:

```console
launchctl load ~/Library/LaunchAgents/homebrew.mxcl.rethinkdb.plist
```

Verify you can access the RethinkDB admin console:

```console
open http://localhost:8080/
```

After installing with brew:

* Your RethinkDB binary will be at `/usr/local/bin/rethinkdb`
* Your RethinkDB data directory will be at `/usr/local/var/rethinkdb`
* Your RethinkDB log will be at `/usr/local/var/log/rethinkdb/rethinkdb.log`
* Your RethinkDB launchd file will be at `~/Library/LaunchAgents/homebrew.mxcl.rethinkdb.plist`

##### RethinkDB for Mac OS X (Binary Package)

If you don't use brew, there is a binary package available for Mac OS X from the [Mac download page](http://rethinkdb.com/docs/install/osx/).

After downloading the disk image, mounting it (double click) and running the rethinkdb.pkg installer, you need to manually create the data directory:

```console
sudo mkdir -p /Library/RethinkDB
sudo chown <your-own-user-id> /Library/RethinkDB
mkdir /Library/RethinkDB/data
```

And you will need to manually create the launchd config file to run RethinkDB every time at login. From within this repo run:

```console
cp ./opt/com.rethinkdb.server.plist ~/Library/LaunchAgents/com.rethinkdb.server.plist
```

And to run RethinkDB now:

```console
launchctl load ~/Library/LaunchAgents/com.rethinkdb.server.plist
```

Verify you can access the RethinkDB admin console:

```console
open http://localhost:8080/
```

After installing with the binary package:

* Your RethinkDB binary will be at `/usr/local/bin/rethinkdb`
* Your RethinkDB data directory will be at `/Library/RethinkDB/data`
* Your RethinkDB log will be at `/var/log/rethinkdb.log`
* Your RethinkDB launchd file will be at `~/Library/LaunchAgents/com.rethinkdb.server.plist`


##### RethinkDB for Linux

If you run Linux on your development environment (good for you, hardcore!) you can get a package for you distribution or compile from source. Details are on the [installation page](http://rethinkdb.com/docs/install/).

##### RethinkDB for Windows

RethinkDB [isn't supported on Windows](https://github.com/rethinkdb/rethinkdb/issues/1100) directly. If you are stuck on Windows, you can run Linux in a virtualized environment to host RethinkDB.


## Introduction

You can verify all is well with your RethinkDB instance and get familiar with RethinkDB [ReQL query language](http://rethinkdb.com/docs/introduction-to-reql/) by using the Data Explorer:

```console
open http://localhost:8080/
```

Next, you can try some things with Clojure by running the REPL from within this project:

```console
lein repl
```

Then enter these commands one-by-one, noting the output:

```clojure
(require '[open-company.db.init :as db])
(require '[open-company.resources.company :as company])
(require '[open-company.resources.section :as section])

;; Create DB and tables and indexes
(db/init)

;; Create some companies

(def author
  {
    :user-id "123456"
    :image "http://www.emoticonswallpapers.com/avatar/cartoons/Wiley-Coyote-Dazed.jpg"
    :name "Wile E. Coyote"
  })

(company/create-company
  {
    :name "Blank Inc."
    :currency "GBP"
  } author)

(company/create-company {
  :name "Transparency, LLC"
  :slug "transparency"
  :currency "FKP"
  :finances {
    :title "Finances"
    :data [
      {:period "2015-09" :cash 66981 :revenue 0 :costs 8019}
    ]
  }} author)

(company/create-company
  {
    :name "Buffer"
    :currency "USD"
    :update {
      :title "Founder's Update"
      :body "It's all good!"
    }
    :finances {
      :title "Finances"
      :data [
        {:period "2015-08" :cash 1182329 :revenue 1215 :costs 28019}
        {:period "2015-09" :cash 1209133 :revenue 977 :costs 27155}
      ]
      :notes {
        :body "Good stuff! Revenue is up."
      }
    }
  } author)

;; List companies
(company/list-companies)

;; Get a company
(company/get-company "blank-inc")
(company/get-company "transparency")
(company/get-company "buffer")

;; Create/update a section
(section/put-section "blank-inc" :finances {:data [{:period "2015-09" :cash 66981 :revenue 0 :costs 8019}]} author)
(section/put-section "blank-inc" :finances {:data [
  {:period "2015-09" :cash 66981 :revenue 0 :costs 8019}
  {:period "2015-10" :cash 58987 :revenue 25 :costs 7867}]
  :notes {:body "We got our first customer! Revenue FTW!"}} author)
(section/put-section "blank-inc" :finances {:data [
  {:period "2015-08" :cash 75000 :revenue 0 :costs 6778}
  {:period "2015-09" :cash 66981 :revenue 0 :costs 8019}
  {:period "2015-10" :cash 58987 :revenue 25 :costs 7867}]
  :notes {:body "We got our first customer! Revenue FTW!"}} author)
(section/put-section "blank-inc" :finances {:data [
  {:period "2015-08" :cash 75000 :revenue 0 :costs 6778}
  {:period "2015-09" :cash 66981 :revenue 0 :costs 8019}
  {:period "2015-10" :cash 58987 :revenue 25 :costs 7867}
  {:period "2015-11" :cash 51125 :revenue 50 :costs 7912}]
  :notes {:body "We got our second customer! Revenue FTW!"}} author)

(section/put-section "buffer" :update {:title "Founder's Update" :body "It's all meh."} author)

;; Get a section
(section/get-section "transparency" :finances)
(section/get-section "buffer" :update)
(section/get-section "buffer" :finances)

;; List revisions
(section/list-revisions "transparency" :finances)
(section/list-revisions "buffer" :update)
(section/list-revisions "buffer" :finances)

;; Get revisions
(section/get-revisions "transparency" :finances)
(section/get-revisions "buffer" :update)
(section/get-revisions "buffer" :finances)

;; Delete a company
(company/delete-company "transparency")

;; Cleanup
(company/delete-all-companies!)
```


## Usage

Start a production API server:

```console
lein start!
```

Or start a development API server:

```console
lein start
```

Create a company with cURL:

TODO: there is no UI yet to do this so you need to create a company via the REPL or CLI import tool.

List the companies with cURL:

```console
curl -i -X GET \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMS00LTE5NjAiLCJuYW1lIjoiY2FtdXMiLCJyZWFsLW5hbWUiOiJBbGJlcnQgQ2FtdXMiLCJhdmF0YXIiOiJodHRwOlwvXC93d3cuYnJlbnRvbmhvbG1lcy5jb21cL3dwLWNvbnRlbnRcL3VwbG9hZHNcLzIwMTBcLzA1XC9hbGJlcnQtY2FtdXMxLmpwZyIsImVtYWlsIjoiYWxiZXJ0QGNvbWJhdC5vcmciLCJvd25lciI6dHJ1ZSwiYWRtaW4iOnRydWV9.piEzy4Aty6A8mfhWLMnJmy67WwsChVX87dp4I0CBz70" \
--header "Accept-Charset: utf-8" \
http://localhost:3000/companies
```

Request the company with cURL:

```console
curl -i -X GET \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMS00LTE5NjAiLCJuYW1lIjoiY2FtdXMiLCJyZWFsLW5hbWUiOiJBbGJlcnQgQ2FtdXMiLCJhdmF0YXIiOiJodHRwOlwvXC93d3cuYnJlbnRvbmhvbG1lcy5jb21cL3dwLWNvbnRlbnRcL3VwbG9hZHNcLzIwMTBcLzA1XC9hbGJlcnQtY2FtdXMxLmpwZyIsImVtYWlsIjoiYWxiZXJ0QGNvbWJhdC5vcmciLCJvd25lciI6dHJ1ZSwiYWRtaW4iOnRydWV9.piEzy4Aty6A8mfhWLMnJmy67WwsChVX87dp4I0CBz70" \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
http://localhost:3000/companies/buffer
```

Update a company with cURL:

```console
curl -i -X PATCH \
-d '{"currency": "FKP" }' \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMS00LTE5NjAiLCJuYW1lIjoiY2FtdXMiLCJyZWFsLW5hbWUiOiJBbGJlcnQgQ2FtdXMiLCJhdmF0YXIiOiJodHRwOlwvXC93d3cuYnJlbnRvbmhvbG1lcy5jb21cL3dwLWNvbnRlbnRcL3VwbG9hZHNcLzIwMTBcLzA1XC9hbGJlcnQtY2FtdXMxLmpwZyIsImVtYWlsIjoiYWxiZXJ0QGNvbWJhdC5vcmciLCJvd25lciI6dHJ1ZSwiYWRtaW4iOnRydWV9.piEzy4Aty6A8mfhWLMnJmy67WwsChVX87dp4I0CBz70" \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.company.v1+json" \
http://localhost:3000/companies/buffer
```

Revise a section for the company with cURL:

```console
curl -i -X PUT \
-d '{"body": "It\u0027s all that and a bag of chips.","title": "Founder\u0027s Update"}' \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMS00LTE5NjAiLCJuYW1lIjoiY2FtdXMiLCJyZWFsLW5hbWUiOiJBbGJlcnQgQ2FtdXMiLCJhdmF0YXIiOiJodHRwOlwvXC93d3cuYnJlbnRvbmhvbG1lcy5jb21cL3dwLWNvbnRlbnRcL3VwbG9hZHNcLzIwMTBcLzA1XC9hbGJlcnQtY2FtdXMxLmpwZyIsImVtYWlsIjoiYWxiZXJ0QGNvbWJhdC5vcmciLCJvd25lciI6dHJ1ZSwiYWRtaW4iOnRydWV9.piEzy4Aty6A8mfhWLMnJmy67WwsChVX87dp4I0CBz70" \
--header "Accept: application/vnd.open-company.section.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.section.v1+json" \
http://localhost:3000/companies/buffer/update
```

Reorder a company's sections with cURL:

```console
curl -i -X PATCH \
-d '{"sections": {"progress": ["finances", "growth", "team", "product", "marketing", "customer-service", "help", "update"], "company": ["values", "diversity"]}}' \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMS00LTE5NjAiLCJuYW1lIjoiY2FtdXMiLCJyZWFsLW5hbWUiOiJBbGJlcnQgQ2FtdXMiLCJhdmF0YXIiOiJodHRwOlwvXC93d3cuYnJlbnRvbmhvbG1lcy5jb21cL3dwLWNvbnRlbnRcL3VwbG9hZHNcLzIwMTBcLzA1XC9hbGJlcnQtY2FtdXMxLmpwZyIsImVtYWlsIjoiYWxiZXJ0QGNvbWJhdC5vcmciLCJvd25lciI6dHJ1ZSwiYWRtaW4iOnRydWV9.piEzy4Aty6A8mfhWLMnJmy67WwsChVX87dp4I0CBz70" \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.company.v1+json" \
http://localhost:3000/companies/buffer
```

Remove a section from a company sections with cURL:

```console
curl -i -X PATCH \
-d '{"sections": {"progress": ["finances", "growth", "team", "product", "customer-service", "help", "update"], "company": ["values", "diversity"]}}' \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMS00LTE5NjAiLCJuYW1lIjoiY2FtdXMiLCJyZWFsLW5hbWUiOiJBbGJlcnQgQ2FtdXMiLCJhdmF0YXIiOiJodHRwOlwvXC93d3cuYnJlbnRvbmhvbG1lcy5jb21cL3dwLWNvbnRlbnRcL3VwbG9hZHNcLzIwMTBcLzA1XC9hbGJlcnQtY2FtdXMxLmpwZyIsImVtYWlsIjoiYWxiZXJ0QGNvbWJhdC5vcmciLCJvd25lciI6dHJ1ZSwiYWRtaW4iOnRydWV9.piEzy4Aty6A8mfhWLMnJmy67WwsChVX87dp4I0CBz70" \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.company.v1+json" \
http://localhost:3000/companies/buffer
```

Add a section to the company with cURL:

```console
curl -i -X PATCH \
-d '{"sections": {"progress": ["finances", "growth", "team", "product", "fund-raising", "customer-service", "help", "update"], "company": ["values", "diversity"]}, "fund-raising": {"title": "Fundraising", "body": "No plans. We have enough monies."}}' \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMS00LTE5NjAiLCJuYW1lIjoiY2FtdXMiLCJyZWFsLW5hbWUiOiJBbGJlcnQgQ2FtdXMiLCJhdmF0YXIiOiJodHRwOlwvXC93d3cuYnJlbnRvbmhvbG1lcy5jb21cL3dwLWNvbnRlbnRcL3VwbG9hZHNcLzIwMTBcLzA1XC9hbGJlcnQtY2FtdXMxLmpwZyIsImVtYWlsIjoiYWxiZXJ0QGNvbWJhdC5vcmciLCJvd25lciI6dHJ1ZSwiYWRtaW4iOnRydWV9.piEzy4Aty6A8mfhWLMnJmy67WwsChVX87dp4I0CBz70" \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.company.v1+json" \
http://localhost:3000/companies/buffer
```

Delete the company with cURL:

```console
curl -i -X DELETE 
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMS00LTE5NjAiLCJuYW1lIjoiY2FtdXMiLCJyZWFsLW5hbWUiOiJBbGJlcnQgQ2FtdXMiLCJhdmF0YXIiOiJodHRwOlwvXC93d3cuYnJlbnRvbmhvbG1lcy5jb21cL3dwLWNvbnRlbnRcL3VwbG9hZHNcLzIwMTBcLzA1XC9hbGJlcnQtY2FtdXMxLmpwZyIsImVtYWlsIjoiYWxiZXJ0QGNvbWJhdC5vcmciLCJvd25lciI6dHJ1ZSwiYWRtaW4iOnRydWV9.piEzy4Aty6A8mfhWLMnJmy67WwsChVX87dp4I0CBz70" \
http://localhost:3000/companies/transparency
```

Try (and fail) to get the section and the company with cURL:

```console
curl -i -X GET \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMS00LTE5NjAiLCJuYW1lIjoiY2FtdXMiLCJyZWFsLW5hbWUiOiJBbGJlcnQgQ2FtdXMiLCJhdmF0YXIiOiJodHRwOlwvXC93d3cuYnJlbnRvbmhvbG1lcy5jb21cL3dwLWNvbnRlbnRcL3VwbG9hZHNcLzIwMTBcLzA1XC9hbGJlcnQtY2FtdXMxLmpwZyIsImVtYWlsIjoiYWxiZXJ0QGNvbWJhdC5vcmciLCJvd25lciI6dHJ1ZSwiYWRtaW4iOnRydWV9.piEzy4Aty6A8mfhWLMnJmy67WwsChVX87dp4I0CBz70" \
--header "Accept: application/vnd.open-company.section.v1+json" \
--header "Accept-Charset: utf-8" \
http://localhost:3000/companies/transparency/update

curl -i -X GET \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMS00LTE5NjAiLCJuYW1lIjoiY2FtdXMiLCJyZWFsLW5hbWUiOiJBbGJlcnQgQ2FtdXMiLCJhdmF0YXIiOiJodHRwOlwvXC93d3cuYnJlbnRvbmhvbG1lcy5jb21cL3dwLWNvbnRlbnRcL3VwbG9hZHNcLzIwMTBcLzA1XC9hbGJlcnQtY2FtdXMxLmpwZyIsImVtYWlsIjoiYWxiZXJ0QGNvbWJhdC5vcmciLCJvd25lciI6dHJ1ZSwiYWRtaW4iOnRydWV9.piEzy4Aty6A8mfhWLMnJmy67WwsChVX87dp4I0CBz70" \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
http://localhost:3000/companies/transparency
```

## Import sample data

To import sample data from an edn file run:
```console
lein run -m open-company.util.sample-data -- ./opt/samples/buffer.edn
```

use `-d` to erase the company before starting the process like this:
```console
lein run -m open-company.util.sample-data -- -d ./opt/samples/buffer.edn
```

To add sample data on a production environment, specify the production database name:

```console
DB_NAME="open_company" lein run -m open-company.util.sample-data -- -d ./opt/samples/buffer.edn
```

or

```console
lein with-profile prod run -m open-company.util.sample-data -- -d ./opt/samples/buffer.edn
```


## Testing

Tests are run in continuous integration of the `master` and `mainline` branches on [Travis CI](https://travis-ci.org/open-company/open-company-api):

[![Build Status](http://img.shields.io/travis/open-company/open-company-api.svg?style=flat)](https://travis-ci.org/open-company/open-company-api)

To run the tests locally:

```console
lein test!
```

## Participation

Please note that this project is released with a [Contributor Code of Conduct](https://github.com/open-company/open-company-api/blob/mainline/CODE-OF-CONDUCT.md). By participating in this project you agree to abide by its terms.


## License

Distributed under the [Mozilla Public License v2.0](http://www.mozilla.org/MPL/2.0/).

Copyright © 2015 Transparency, LLC
