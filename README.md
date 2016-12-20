# [OpenCompany](https://opencompany.com/) Platform API

[![MPL License](http://img.shields.io/badge/license-MPL-blue.svg?style=flat)](https://www.mozilla.org/MPL/2.0/)
[![Build Status](http://img.shields.io/travis/open-company/open-company-api.svg?style=flat)](https://travis-ci.org/open-company/open-company-api)
[![Dependency Status](https://www.versioneye.com/user/projects/55e5a34c8c0f62001b0003f3/badge.svg?style=flat)](https://www.versioneye.com/user/projects/55e5a34c8c0f62001b0003f3)
[![Roadmap on Trello](http://img.shields.io/badge/roadmap-trello-blue.svg?style=flat)](https://trello.com/b/3naVWHgZ/open-company-development)


## Background

> A lack of transparency results in distrust and a deep sense of insecurity.

> -- Dalai Lama

Employees and investors, co-founders and execs, they all want more transparency from their startups, but there's no consensus about what it means to be transparent. OpenCompany is a platform that simplifies how key business information is shared with stakeholders.

When information about growth, finances, ownership and challenges is shared transparently, it inspires trust, new ideas and new levels of stakeholder engagement. OpenCompany makes it easy for founders to engage with employees and investors, creating a sense of ownership and urgency for everyone.

[OpenCompany](https://opencompany.com/) is GitHub for the rest of your company.

To maintain transparency, OpenCompany information is always accessible and easy to find. Being able to search or flip through prior updates empowers everyone. Historical context brings new employees and investors up to speed, refreshes memories, and shows how the company is evolving over time.

Transparency expectations are changing. Startups need to change as well if they are going to attract and retain savvy employees and investors. Just as open source changed the way we build software, transparency changes how we build successful startups with information that is open, interactive, and always accessible. The OpenCompany platform turns transparency into a competitive advantage.

Like the open companies we promote and support, the [OpenCompany](https://opencompany.com/) platform is completely transparent. The company supporting this effort, OpenCompany, LLC, is an open company. The [platform](https://github.com/open-company/open-company-web) is open source software, and open company data is [open data](https://en.wikipedia.org/wiki/Open_data) accessible through the [platform API](https://github.com/open-company/open-company-api).

To get started, head to: [OpenCompany](https://opencompany.com/)


## Overview

The OpenCompany Platform API handles data access and data management of open company content and data. It supports other OpenCompany services, such as the Web application and OpenCompany Bot, as well as open API access.


## Local Setup

Users of the [OpenCompany](https://opencompany.com/) platform should get started by going to [OpenCompany](https://opencompany.com/). The following local setup is **for developers** wanting to work on the platform's API software.

Most of the dependencies are internal, meaning [Leiningen](https://github.com/technomancy/leiningen) will handle getting them for you. There are a few exceptions:

* [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) - a Java 8+ JRE is needed to run Clojure
* [Leiningen](https://github.com/technomancy/leiningen) 2.5.1+ - Clojure's build and dependency management tool
* [RethinkDB](http://rethinkdb.com/) v2.3.5+ - a multi-modal (document, key/value, relational) open source NoSQL database

#### Java

Chances are your system already has Java 8+ installed. You can verify this with:

```console
java -version
```

If you do not have Java 8+ [download it](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and follow the installation instructions.

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

If you already have RethinkDB installed via brew, check the version:

```console
rethinkdb -v
```

If it's older, then upgrade it with:

```console
brew update && brew upgrade rethinkdb && brew services restart rethinkdb
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

If you don't use brew, there is a binary installer package available for Mac OS X from the [Mac download page](http://rethinkdb.com/docs/install/osx/).

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

#### Required Secrets

A secret is shared between the [OpenCompany Authentication Service](https://github.com/open-company/open-company-auth) and the API for creating and validating [JSON Web Tokens](https://jwt.io/).

An [AWS SQS queue](https://aws.amazon.com/sqs/) is used to pass messages from the API to other OpenCompany services. Setup an SQS Queue and key/secret access to the queue using the AWS Web Console or API.

Make sure you update the section in `project.clj` that looks like this to contain your actual JWT and AWS SQS secrets:

```clojure
:dev [:qa {
  :env ^:replace {
    :open-company-auth-passphrase "this_is_a_dev_secret" ; JWT secret
    :aws-access-key-id "CHANGE-ME"
    :aws-secret-access-key "CHANGE-ME"
    :aws-sqs-bot-queue "https://sqs.REGION.amazonaws.com/CHANGE/ME" 
    :aws-sqs-email-queue "https://sqs.REGION.amazonaws.com/CHANGE/ME" 
  }
```

You can also override these settings with environmental variables in the form of `OPEN_COMPANY_AUTH_PASSPHRASE` and
`AWS_ACCESS_KEY_ID`, etc. Use environmental variables to provide production secrets when running in production.


## Usage

You can verify all is well with your RethinkDB instance and get familiar with RethinkDB [ReQL query language](http://rethinkdb.com/docs/introduction-to-reql/) by using the Data Explorer:

```console
open http://localhost:8080/
```

#### REPL

Next, you can try some things with Clojure by running the REPL from within this project:

```console
lein repl
```

Then enter these commands one-by-one, noting the output:

```clojure
;; start the development system
(go) ; NOTE: if you are already running the API externally to the REPL, use `(go 3737)` to change the port

;; create db and tables and indexes
(db/init)

;; create some companies

(def author {
  :user-id "slack:123456"
  :name "coyote"
  :real-name "Wile E. Coyote"
  :avatar "http://www.emoticonswallpapers.com/avatar/cartoons/Wiley-Coyote-Dazed.jpg"
  :email "wile.e.coyote@acme.com"
  :owner false
  :admin false
  :org-id "slack:98765"
})

(company/create-company!
  conn
  (company/->company {:name "Blank.com"
                      :slug "blank"
                      :currency "GBP"}
                    author))

(company/create-company!
  conn
  (company/->company {:name "OpenCompany"
                      :slug "open"
                      :logo "https://open-company-assets.s3.amazonaws.com/open-company.png"
                      :currency "USD"
                      :finances {:data [{:period "2015-09" :cash 66981 :revenue 0 :costs 8019}]}}
                    author))

(company/create-company!
  conn
  (company/->company {:name "Buffer"
                      :slug (slug/find-available-slug "Buffer" (company/taken-slugs conn))
                      :currency "USD"
                      :logo "https://open-company-assets.s3.amazonaws.com/buffer.png"
                      :update {:title "Founder's Update"
                               :headline "Buffer in October."
                               :body "October was an unusual month for us, numbers-wise, as a result of us moving from 7-day to 30- day trials of Buffer for Business."}
                      :finances {:body "Good stuff! Revenue is up."
                                 :data [{:period "2015-08" :cash 1182329 :revenue 1215 :costs 28019}
                                        {:period "2015-09" :cash 1209133 :revenue 977 :costs 27155}]}}
                    author))

;; list companies
(company/list-companies conn)

;; get a company
(aprint (company/get-company conn "blank"))
(aprint (company/get-company conn "open"))
(aprint (company/get-company conn "buffer"))

;; create a section
(section/put-section conn "blank" :finances {:data [{:period "2015-09" :cash 66981 :revenue 0 :costs 8019}]} author)

;; add additional entries to the section
(section/put-section conn "blank" :finances {:body "we got our first customer! revenue ftw!"
                                                 :data [{:period "2015-09" :cash 66981 :revenue 0 :costs 8019}
                                                        {:period "2015-10" :cash 58987 :revenue 25 :costs 7867}]} author)
(section/put-section conn "blank" :finances {:data [{:period "2015-08" :cash 75000 :revenue 0 :costs 6778}
                                                        {:period "2015-09" :cash 66981 :revenue 0 :costs 8019}
                                                        {:period "2015-10" :cash 58987 :revenue 25 :costs 7867}]} author)
;; update existing entry
(section/patch-revision conn "blank" :finances {:body "we got our second customer! more revenue ftw!"
                                                 :data [{:period "2015-08" :cash 75000 :revenue 0 :costs 6778}
                                                        {:period "2015-09" :cash 66981 :revenue 0 :costs 8019}
                                                        {:period "2015-10" :cash 58987 :revenue 25 :costs 7867}
                                                        {:period "2015-11" :cash 51125 :revenue 50 :costs 7912}]} author)
(aprint (company/get-company conn "blank-inc"))

;; 3 entries, not 4, the last entry has been edited once
(count (section/list-revisions conn "blank" :finances))

;; create a section
(section/put-section conn "buffer" :update {:headline "it's all meh."} author)
(aprint (company/get-company conn "buffer"))

;; get a section
(aprint (section/get-section conn "blank" :finances))
(aprint (section/get-section conn "buffer" :update))
(aprint (section/get-section conn "buffer" :finances))

;; list revisions
(aprint (section/list-revisions conn "blank" :finances))
(aprint (section/list-revisions conn "buffer" :update))
(aprint (section/list-revisions conn "buffer" :finances))

;; get revisions
(aprint (section/get-revisions conn "blank" :finances))
(aprint (section/get-revisions conn "buffer" :update))
(aprint (section/get-revisions conn "buffer" :finances))

;; create a stakeholder update
(su/create-stakeholder-update!
  conn
  (su/->stakeholder-update
    conn
    (company/get-company conn "open")
    {:title "OpenCompany Update"
     :medium :link
     :sections ["finances"]}
    author))

;; delete a company
(company/delete-company! conn "blank")

;; cleanup
(company/delete-all-companies! conn)
```


#### Server

Start a production API server:

```console
lein start!
```

Or start a development API server:

```console
lein start
```

#### API Requests

You'll need a JWToken to use the REST API via cURL as an authenticated user. The token is passed in the `Authorization`
header with each request. You can either extract your own token from the cookie in your web browser, to make requests
against your own services or our servers, or you can also use a
[sample token](https://github.com/open-company/open-company-auth#sample-jwtoken)
from the OpenCompany Authentication service if you are only making requests against your local services.

Create a company with cURL:

```console
echo "{\"currency\": \"EUR\", \"name\": \"Hotel Procrastination\", \
      \"diversity\": {\"headline\": \"We are all guilty.\", \"pin\": true}, \
      \"update\": {\"headline\": \"Our Food is Bad\", \"body\": \"Hotel guests rate it 1 of 10.\", \"pin\": true}, \
      \"mission\": {\"headline\": \"Better Food\", \"body\": \"That's the goal.\"}, \
      \"team\": {\"headline\": \"New Head Chef\", \"body\": \"Welcome Bobby Flay to the team.\"}}" \
      > ./hotel.json
curl -i -X POST \
-d @./hotel.json \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMTIzNDU2IiwibmFtZSI6ImNveW90ZSIsInJlYWwtbmFtZSI6IldpbGUgRS4gQ295b3RlIiwiYXZhdGFyIjoiaHR0cDpcL1wvd3d3LmVtb3RpY29uc3dhbGxwYXBlcnMuY29tXC9hdmF0YXJcL2NhcnRvb25zXC9XaWxleS1Db3lvdGUtRGF6ZWQuanBnIiwiZW1haWwiOiJ3aWxlLmUuY295b3RlQGFjbWUuY29tIiwib3duZXIiOmZhbHNlLCJhZG1pbiI6ZmFsc2UsIm9yZy1pZCI6Ijk4NzY1In0.HwqwEijPYDXTLdnL0peO8_KEtj379s4P5oJyv06yhfU" \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.company.v1+json" \
http://localhost:3000/companies/
```

List the companies with cURL:

```console
curl -i -X GET \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMTIzNDU2IiwibmFtZSI6ImNveW90ZSIsInJlYWwtbmFtZSI6IldpbGUgRS4gQ295b3RlIiwiYXZhdGFyIjoiaHR0cDpcL1wvd3d3LmVtb3RpY29uc3dhbGxwYXBlcnMuY29tXC9hdmF0YXJcL2NhcnRvb25zXC9XaWxleS1Db3lvdGUtRGF6ZWQuanBnIiwiZW1haWwiOiJ3aWxlLmUuY295b3RlQGFjbWUuY29tIiwib3duZXIiOmZhbHNlLCJhZG1pbiI6ZmFsc2UsIm9yZy1pZCI6Ijk4NzY1In0.HwqwEijPYDXTLdnL0peO8_KEtj379s4P5oJyv06yhfU" \
--header "Accept-Charset: utf-8" \
http://localhost:3000/companies
```

Request the company with cURL:

```console
curl -i -X GET \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMTIzNDU2IiwibmFtZSI6ImNveW90ZSIsInJlYWwtbmFtZSI6IldpbGUgRS4gQ295b3RlIiwiYXZhdGFyIjoiaHR0cDpcL1wvd3d3LmVtb3RpY29uc3dhbGxwYXBlcnMuY29tXC9hdmF0YXJcL2NhcnRvb25zXC9XaWxleS1Db3lvdGUtRGF6ZWQuanBnIiwiZW1haWwiOiJ3aWxlLmUuY295b3RlQGFjbWUuY29tIiwib3duZXIiOmZhbHNlLCJhZG1pbiI6ZmFsc2UsIm9yZy1pZCI6Ijk4NzY1In0.HwqwEijPYDXTLdnL0peO8_KEtj379s4P5oJyv06yhfU" \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
http://localhost:3000/companies/hotel-procrastination
```

Update a company with cURL:

```console
curl -i -X PATCH \
-d '{"currency": "FKP" }' \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMTIzNDU2IiwibmFtZSI6ImNveW90ZSIsInJlYWwtbmFtZSI6IldpbGUgRS4gQ295b3RlIiwiYXZhdGFyIjoiaHR0cDpcL1wvd3d3LmVtb3RpY29uc3dhbGxwYXBlcnMuY29tXC9hdmF0YXJcL2NhcnRvb25zXC9XaWxleS1Db3lvdGUtRGF6ZWQuanBnIiwiZW1haWwiOiJ3aWxlLmUuY295b3RlQGFjbWUuY29tIiwib3duZXIiOmZhbHNlLCJhZG1pbiI6ZmFsc2UsIm9yZy1pZCI6Ijk4NzY1In0.HwqwEijPYDXTLdnL0peO8_KEtj379s4P5oJyv06yhfU" \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.company.v1+json" \
http://localhost:3000/companies/hotel-procrastination
```

Create a new section entry with cURL:

```console
curl -i -X PUT \
-d '{"body": "It\u0027s all that and a bag of chips.","title": "Founder\u0027s Update", "headline": "Make it rain!"}' \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMTIzNDU2IiwibmFtZSI6ImNveW90ZSIsInJlYWwtbmFtZSI6IldpbGUgRS4gQ295b3RlIiwiYXZhdGFyIjoiaHR0cDpcL1wvd3d3LmVtb3RpY29uc3dhbGxwYXBlcnMuY29tXC9hdmF0YXJcL2NhcnRvb25zXC9XaWxleS1Db3lvdGUtRGF6ZWQuanBnIiwiZW1haWwiOiJ3aWxlLmUuY295b3RlQGFjbWUuY29tIiwib3duZXIiOmZhbHNlLCJhZG1pbiI6ZmFsc2UsIm9yZy1pZCI6Ijk4NzY1In0.HwqwEijPYDXTLdnL0peO8_KEtj379s4P5oJyv06yhfU" \
--header "Accept: application/vnd.open-company.section.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.section.v1+json" \
http://localhost:3000/companies/hotel-procrastination/update
```

Reorder a company's sections with cURL:

```console
curl -i -X PATCH \
-d '{"sections": ["update", "diversity", "mission", "team"]}' \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMTIzNDU2IiwibmFtZSI6ImNveW90ZSIsInJlYWwtbmFtZSI6IldpbGUgRS4gQ295b3RlIiwiYXZhdGFyIjoiaHR0cDpcL1wvd3d3LmVtb3RpY29uc3dhbGxwYXBlcnMuY29tXC9hdmF0YXJcL2NhcnRvb25zXC9XaWxleS1Db3lvdGUtRGF6ZWQuanBnIiwiZW1haWwiOiJ3aWxlLmUuY295b3RlQGFjbWUuY29tIiwib3duZXIiOmZhbHNlLCJhZG1pbiI6ZmFsc2UsIm9yZy1pZCI6Ijk4NzY1In0.HwqwEijPYDXTLdnL0peO8_KEtj379s4P5oJyv06yhfU" \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.company.v1+json" \
http://localhost:3000/companies/hotel-procrastination
```

Archive a section from a company with cURL:

```console
curl -i -X PATCH \
-d '{"sections": ["update", diversity", "team"]}' \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMTIzNDU2IiwibmFtZSI6ImNveW90ZSIsInJlYWwtbmFtZSI6IldpbGUgRS4gQ295b3RlIiwiYXZhdGFyIjoiaHR0cDpcL1wvd3d3LmVtb3RpY29uc3dhbGxwYXBlcnMuY29tXC9hdmF0YXJcL2NhcnRvb25zXC9XaWxleS1Db3lvdGUtRGF6ZWQuanBnIiwiZW1haWwiOiJ3aWxlLmUuY295b3RlQGFjbWUuY29tIiwib3duZXIiOmZhbHNlLCJhZG1pbiI6ZmFsc2UsIm9yZy1pZCI6Ijk4NzY1In0.HwqwEijPYDXTLdnL0peO8_KEtj379s4P5oJyv06yhfU" \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.company.v1+json" \
http://localhost:3000/companies/hotel-procrastination
```

Add an archived section back to the company with cURL:

```console
curl -i -X PATCH \
-d '{"sections": ["update", "diversity", "team", "mission"]}' \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMTIzNDU2IiwibmFtZSI6ImNveW90ZSIsInJlYWwtbmFtZSI6IldpbGUgRS4gQ295b3RlIiwiYXZhdGFyIjoiaHR0cDpcL1wvd3d3LmVtb3RpY29uc3dhbGxwYXBlcnMuY29tXC9hdmF0YXJcL2NhcnRvb25zXC9XaWxleS1Db3lvdGUtRGF6ZWQuanBnIiwiZW1haWwiOiJ3aWxlLmUuY295b3RlQGFjbWUuY29tIiwib3duZXIiOmZhbHNlLCJhZG1pbiI6ZmFsc2UsIm9yZy1pZCI6Ijk4NzY1In0.HwqwEijPYDXTLdnL0peO8_KEtj379s4P5oJyv06yhfU" \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.company.v1+json" \
http://localhost:3000/companies/hotel-procrastination
```

Add new section to the company with cURL:

```console
curl -i -X PUT \
-d '{"headline": "Fred is killing it"}' \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMTIzNDU2IiwibmFtZSI6ImNveW90ZSIsInJlYWwtbmFtZSI6IldpbGUgRS4gQ295b3RlIiwiYXZhdGFyIjoiaHR0cDpcL1wvd3d3LmVtb3RpY29uc3dhbGxwYXBlcnMuY29tXC9hdmF0YXJcL2NhcnRvb25zXC9XaWxleS1Db3lvdGUtRGF6ZWQuanBnIiwiZW1haWwiOiJ3aWxlLmUuY295b3RlQGFjbWUuY29tIiwib3duZXIiOmZhbHNlLCJhZG1pbiI6ZmFsc2UsIm9yZy1pZCI6Ijk4NzY1In0.HwqwEijPYDXTLdnL0peO8_KEtj379s4P5oJyv06yhfU" \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
--header "Content-Type: application/vnd.open-company.company.v1+json" \
http://localhost:3000/companies/hotel-procrastination/kudos
```

Delete the company with cURL:

```console
curl -i -X DELETE \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMTIzNDU2IiwibmFtZSI6ImNveW90ZSIsInJlYWwtbmFtZSI6IldpbGUgRS4gQ295b3RlIiwiYXZhdGFyIjoiaHR0cDpcL1wvd3d3LmVtb3RpY29uc3dhbGxwYXBlcnMuY29tXC9hdmF0YXJcL2NhcnRvb25zXC9XaWxleS1Db3lvdGUtRGF6ZWQuanBnIiwiZW1haWwiOiJ3aWxlLmUuY295b3RlQGFjbWUuY29tIiwib3duZXIiOmZhbHNlLCJhZG1pbiI6ZmFsc2UsIm9yZy1pZCI6Ijk4NzY1In0.HwqwEijPYDXTLdnL0peO8_KEtj379s4P5oJyv06yhfU" \
http://localhost:3000/companies/hotel-procrastination
```

Then, try (and fail) to get a section and the company with cURL:

```console
curl -i -X GET \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMTIzNDU2IiwibmFtZSI6ImNveW90ZSIsInJlYWwtbmFtZSI6IldpbGUgRS4gQ295b3RlIiwiYXZhdGFyIjoiaHR0cDpcL1wvd3d3LmVtb3RpY29uc3dhbGxwYXBlcnMuY29tXC9hdmF0YXJcL2NhcnRvb25zXC9XaWxleS1Db3lvdGUtRGF6ZWQuanBnIiwiZW1haWwiOiJ3aWxlLmUuY295b3RlQGFjbWUuY29tIiwib3duZXIiOmZhbHNlLCJhZG1pbiI6ZmFsc2UsIm9yZy1pZCI6Ijk4NzY1In0.HwqwEijPYDXTLdnL0peO8_KEtj379s4P5oJyv06yhfU" \
--header "Accept: application/vnd.open-company.section.v1+json" \
--header "Accept-Charset: utf-8" \
http://localhost:3000/companies/hotel-procrastination/update

curl -i -X GET \
--header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyLWlkIjoiMTIzNDU2IiwibmFtZSI6ImNveW90ZSIsInJlYWwtbmFtZSI6IldpbGUgRS4gQ295b3RlIiwiYXZhdGFyIjoiaHR0cDpcL1wvd3d3LmVtb3RpY29uc3dhbGxwYXBlcnMuY29tXC9hdmF0YXJcL2NhcnRvb25zXC9XaWxleS1Db3lvdGUtRGF6ZWQuanBnIiwiZW1haWwiOiJ3aWxlLmUuY295b3RlQGFjbWUuY29tIiwib3duZXIiOmZhbHNlLCJhZG1pbiI6ZmFsc2UsIm9yZy1pZCI6Ijk4NzY1In0.HwqwEijPYDXTLdnL0peO8_KEtj379s4P5oJyv06yhfU" \
--header "Accept: application/vnd.open-company.company.v1+json" \
--header "Accept-Charset: utf-8" \
http://localhost:3000/companies/hotel-procrastination
```

#### Import sample data

To import company sample data from an edn file run:
```console
lein run -m open-company.util.sample-data -- ./opt/samples/buff.edn
```

use `-d` to erase the company while importing like this:
```console
lein run -m open-company.util.sample-data -- -d ./opt/samples/buff.edn
```

To add all the company sample data in a directory (each file with a `.edn` extension), run:
```console
lein run -m open-company.util.sample-data -- ./opt/samples/
```

use `-d` to erase companies while importing like this:
```console
lein run -m open-company.util.sample-data -- -d ./opt/samples/
```

To add sample data on a production environment, specify the production database name:

```console
DB_NAME="open_company" lein run -m open-company.util.sample-data -- -d ./opt/samples/buff.edn
```

or

```console
DB_NAME="open_company" lein run -m open-company.util.sample-data -- -d ./opt/samples/
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

Copyright © 2015-2016 OpenCompany, LLC.