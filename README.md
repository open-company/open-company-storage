# [OpenCompany.io](https://opencompany.io) Platform API

[![MPL License](http://img.shields.io/badge/license-MPL-blue.svg?style=flat)](https://www.mozilla.org/MPL/2.0/)
[![Build Status](http://img.shields.io/travis/open-company/open-company-api.svg?style=flat)](https://travis-ci.org/open-company/open-company-api)
[![Roadmap on Trello](http://img.shields.io/badge/roadmap-trello-blue.svg?style=flat)](https://trello.com/b/3naVWHgZ/open-company-development)
[![Dependency Status](https://www.versioneye.com/user/projects/55e9bb72211c6b0019000f1f/badge.svg?style=flat)](https://www.versioneye.com/user/projects/55e9bb72211c6b0019000f1f)


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
* [RethinkDB](http://rethinkdb.com/) v2.0.4+ - a multi-modal (document, key/value, relational) open source NoSQL database

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
(require '[open-company.resources.report :as report])

;; Create DB and tables and indexes
(db/init)

;; Create some companies
(company/create-company {:symbol "OPEN" :name "Transparency, LLC" :currency "USD" :web {:company "https://opencompany.io/"}})
(company/create-company {:symbol "BUFFR" :name "Buffer" :currency "USD" :web {:company "https://open.bufferapp.com/"}})

;; List the companies
(company/list-companies)

;; Get a company
(company/get-company "OPEN")

;; Update a company
(company/update-company {:symbol "OPEN" :name "Transparency Inc." :currency "USD" :web {:company "https://opencompany.io/"}})

;; Create some reports
(report/create-report {:symbol "OPEN" :year 2015 :period "Q2" :headcount {:founders 2 :contractors 1}})
(report/create-report {:symbol "BUFFR" :year 2015 :period "M6" :finances {:cash 2578881 :revenue 550529}})

;; List reports
(report/list-reports "OPEN")

;; Get a report
(report/get-report "OPEN" 2015 "Q2")

;; Update a report
(report/update-report {:symbol "BUFFR" :year 2015 :period "M6" :finances {:cash 2578881 :revenue 550529} :headcount {:comment "We’re hiring for 14 (!) different positions"}})

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

```console
curl -i -X PUT \
-d '{"name": "Transparency, LLC", "currency": "USD", "web": {"company": "https://opencompany.io"}}' \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.company.v1+json" \
http://localhost:3000/companies/OPEN
```

List the companies with cURL:

```console
curl -i -X GET \
--header "Accept-Charset: utf-8" \
http://localhost:3000/companies
```

Request the company with cURL:

```console
curl -i -X GET \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
http://localhost:3000/companies/OPEN
```

Update a company with cURL:

```console
curl -i -X PUT \
-d '{"name": "Transparency, LLC", "currency": "USD", "web": {"about": "https://opencompany.io/about"}}' \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.company.v1+json" \
http://localhost:3000/companies/OPEN
```

Create a report for the company with cURL:

```console
curl -i -X PUT \
-d '{"headcount": {"founders": 2, "contractors": 1}}' \
--header "Accept: application/vnd.open-company.report.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.report.v1+json" \
http://localhost:3000/companies/OPEN/reports/2015/Q2
```

Request the report with cURL:

```console
curl -i -X GET \
--header "Accept: application/vnd.open-company.report.v1+json" \
--header "Accept-Charset: utf-8" \
http://localhost:3000/companies/OPEN/reports/2015/Q2
```

Update the report with cURL:

```console
curl -i -X PUT \
-d '{"headcount": {"founders": 3}}' \
--header "Accept: application/vnd.open-company.report.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.report.v1+json" \
http://localhost:3000/companies/OPEN/reports/2015/Q2
```

Delete the report with cURL:

```console
curl -i -X DELETE http://localhost:3000/companies/OPEN/reports/2015/Q2
```

Delete the company with cURL:

```console
curl -i -X DELETE http://localhost:3000/companies/OPEN
```

Try (and fail) to get the report and the company with cURL:

```console
curl -i -X GET \
--header "Accept: application/vnd.open-company.report.v1+json" \
--header "Accept-Charset: utf-8" \
http://localhost:3000/companies/OPEN/reports/2015/Q2

curl -i -X GET \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
http://localhost:3000/companies/OPEN
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