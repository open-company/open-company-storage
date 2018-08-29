# [OpenCompany](https://github.com/open-company) Storage Service

[![AGPL License](http://img.shields.io/badge/license-AGPL-blue.svg?style=flat)](https://www.gnu.org/licenses/agpl-3.0.en.html)
[![Build Status](http://img.shields.io/travis/open-company/open-company-storage.svg?style=flat)](https://travis-ci.org/open-company/open-company-storage)
[![Dependencies Status](https://versions.deps.co/open-company/open-company-storage/status.svg)](https://versions.deps.co/open-company/open-company-storage)

## Background

> A lack of transparency results in distrust and a deep sense of insecurity.

> -- Dalai Lama

Teams struggle to keep everyone on the same page. People are hyper-connected in the moment with chat and email, but it gets noisy as teams grow, and people miss key information. Everyone needs clear and consistent leadership, and the solution is surprisingly simple and effective - **great leadership updates that build transparency and alignment**.

With that in mind we designed [Carrot](https://carrot.io/), a software-as-a-service application powered by the open source [OpenCompany platform](https://github.com/open-company) and a source-available [web UI](https://github.com/open-company/open-company-web).

With Carrot, important company updates, announcements, stories, and strategic plans create focused, topic-based conversations that keep everyone aligned without interruptions. When information is shared transparently, it inspires trust, new ideas and new levels of stakeholder engagement. Carrot makes it easy for leaders to engage with employees, investors, and customers, creating alignment for everyone.

Transparency expectations are changing. Organizations need to change as well if they are going to attract and retain savvy teams, investors and customers. Just as open source changed the way we build software, transparency changes how we build successful companies with information that is open, interactive, and always accessible. Carrot turns transparency into a competitive advantage.

To get started, head to: [Carrot](https://carrot.io/)


## Overview

The OpenCompany Storage Service handles data access and data management of open company content and data. It supports other OpenCompany services, such as the [Web application](https://github.com/open-company/open-company-web) and the [Digest Service](https://github.com/open-company/open-company-digest), as well as open API access.


## Local Setup

Prospective users of [Carrot](https://carrot.io/) should get started by going to [Carrot.io](https://carrot.io/). The following local setup is **for developers** wanting to work on the OpenCompany Storage Service.

Most of the dependencies are internal, meaning [Leiningen](https://github.com/technomancy/leiningen) will handle getting them for you. There are a few exceptions:

* [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) - a Java 8+ JRE is needed to run Clojure
* [Leiningen](https://github.com/technomancy/leiningen) 2.7.1+ - Clojure's build and dependency management tool
* [RethinkDB](http://rethinkdb.com/) v2.3.6+ - a multi-modal (document, key/value, relational) open source NoSQL database
* [ngrok](https://ngrok.com/) - Secure web tunnel to localhost

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
git clone https://github.com/open-company/open-company-storage.git
cd open-company-storage
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

#### Ziggeo Webhooks (ngrok)

In order for the video posts to function properly you will need to handle
webhooks from ziggeo.

ngrok allows you to setup a secure web tunnel for HTTP/S requests to your localhost. You'll need this to utilize the Ziggeo webhook during local development so Ziggeo can communicate with your local development environment.

ngrok is trivial to setup:

1. [Download](https://ngrok.com/download) the version for your operating system.
1. Unzip the download and put ngrok someplace handy for you (in your path is good!)
1. Verify you can run ngrok with: `ngrok help`

To use the webhook from Slack with local development, you need to run ngrok, then configure your Slack integration.

First start the Slack Router service (see below), and start the ngrok tunnel:

```console
ngrok http 3001
```

Note the HTTPS URL ngrok provides. It will look like: `https://6ae20d9b.ngrok.io` -> localhost:3001

To configure Ziggeo to use the ngrok tunnel, go to the ziggeo web app at [https://ziggeo.com/](https://ziggeo.com/) and log in.

On the left nav bar there is a 'Manage' option. Click that and then choose the 'Webhooks' item in the middle of the page. Add your ngrok URL and choose 'JSON encoding' for the template option.


#### Configuration

An AWS SQS queue is used to pass messages to the OpenCompany Storage service from the OpenCompany Authentication service. Setup SQS Queues and key/secret access to the queue using the AWS Web Console or API.

You will also need to subscribe the `aws-sqs-auth-queue` SQS queue to the Authentication service SNS topic:

Go to the AWS SQS Console and select the `aws-sqs-auth-queue` SQS queue configured above. From the 'Queue Actions' dropdown, select 'Subscribe Queue to SNS Topic'. Select the SNS topic you've configured your Authentication service instance to publish to, and click the 'Subscribe' button.

Make sure you update the section in `project.clj` that looks like this to contain your specific configuration:

```clojure
:dev [:qa {
  :env ^:replace {
    :db-name "open_company_storage_dev"
    :liberator-trace "false" ; liberator debug data in HTTP response headers
    :hot-reload "true" ; reload code when changed on the file system
    :open-company-auth-passphrase "this_is_a_dev_secret" ; JWT secret
    :aws-access-key-id "CHANGE-ME"
    :aws-secret-access-key "CHANGE-ME"
    :aws-sqs-bot-queue "CHANGE-ME" ; SQS queue to pass on requests to the Slack Bot
    :aws-sqs-email-queue "CHANGE-ME" ; SQS queue to pass on requests to the Email service
    :aws-sqs-auth-queue "CHANGE-ME" ; SQS queue to read notifications from the Auth service
    :aws-sns-storage-topic-arn "" ; SNS topic to publish notifications (optional)
    :log-level "debug"
  }
```

You can also override these settings with environmental variables in the form of `OPEN_COMPANY_AUTH_PASSPHRASE` and
`AWS_ACCESS_KEY_ID`, etc. Use environmental variables to provide production secrets when running in production.

##### Shared Secrets

A secret, `open-company-auth-passphrase`, is shared between the [OpenCompany Authentication Service](https://github.com/open-company/open-company-auth) and the Storage Service for creating and validating [JSON Web Tokens](https://jwt.io/).

##### HTTP Behavior

There are two options controlling HTTP behavior:

`liberator-trace` will include detailed debug info in HTTP response headers that correspond to each step in the [Liberator decision graph](https://clojure-liberator.github.io/liberator/tutorial/decision-graph.html) for that request.

The `hot-reload` configuration controls if each request checks for updated code to load, useful for development, but not for production.

Both of these settings take the string `true` or `false`.

##### AWS

[AWS SQS](https://aws.amazon.com/sqs/)  queues are used to pass messages from the Storage Service to other OpenCompany services. Setup an SQS Queue and key/secret access to the queue using the AWS Web Console or API and update the corresponding `aws-` configuration properties with the key, secret and queue names.

An optional [AWS SNS](https://aws.amazon.com/sns/) pub/sub topic is used to push notifications of content changes to interested listeners. If you want to take advantage of this capability, configure the `aws-sns-storage-topic-arn` with the ARN (Amazon Resource Name) of the SNS topic you setup in AWS.

## Technical Design

The Storage service is composed of 8 main responsibilities:

- CRUD of orgs, boards, and entries
- Access control to orgs, boards, and entries
- Notifying the [Bot service](https://github.com/open-company/open-company-bot) of new orgs via SQS
- Notifying the [Bot service](https://github.com/open-company/open-company-bot) and [Email service](https://github.com/open-company/open-company-email) of share requests via SQS
- Notifying the [Bot service](https://github.com/open-company/open-company-bot) and [Email service](https://github.com/open-company/open-company-email) of new invites via SQS
- Notifying the [Email service](https://github.com/open-company/open-company-email) of password reset and email validation requests via SQS
- Publishing org, board and entry change notifications to interested subscribers via SNS
- Handling call-backs from Ziggeo for video processing events

The Storage service provides a HATEOAS REST API:

![Storage Service Diagram](https://cdn.rawgit.com/open-company/open-company-storage/mainline/docs/Storage-REST-API.svg)

The Storage service shares a RethinkDB database instance with the [Interaction service](https://github.com/open-company/open-company-interaction).

![Storage service schema diagram](https://cdn.rawgit.com/open-company/open-company-storage/mainline/docs/Storage-Schema.svg)

## Usage

Prospective users of [Carrot](https://carrot.io/) should get started by going to [Carrot.io](https://carrot.io/). The following usage is **for developers** wanting to work on the OpenCompany Storage Service.

You can verify all is well with your RethinkDB instance and get familiar with RethinkDB [ReQL query language](http://rethinkdb.com/docs/introduction-to-reql/) by using the Data Explorer:

```console
open http://localhost:8080/
```

#### REPL

Next, you can try some things with Clojure by running the REPL from within this project:

```console
lein migrate-db
lein repl
```

#### Server

Start a production instance:

```console
lein start!
```

Or start a development instance:

```console
lein start
```

#### API Requests

You'll need a JWToken to use the REST API via cURL as an authenticated user. The token is passed in the `Authorization`
header with each request. You can either extract your own token from the headers of requests from your web browser, 
or you can generate a token using the [Authentication service](https://github.com/open-company/open-company-auth).

You can see a list of common API requests and responses by loading the `oc-storage.paw` file from this repository
into the [Paw API Development tool](https://paw.cloud/).


#### Import sample data

To import company sample data from an edn file run:
```console
lein run -m oc.storage.util.import -- ./opt/samples/18f.edn
```

use `-d` to erase the company while importing like this:
```console
lein run -m oc.storage.util.import -- -d ./opt/samples/green-labs.edn
```

To add all the company sample data in a directory (each file with a `.edn` extension), run:
```console
lein run -m oc.storage.util.import -- ./opt/samples/
```

use `-d` to erase companies while importing like this:
```console
lein run -m oc.storage.util.import -- -d ./opt/samples/
```

To add sample data on a production environment, specify the production database name:

```console
DB_NAME="open_company_storage" lein run -m oc.storage.util.import -- -d ./opt/samples/18F.edn
```

or

```console
DB_NAME="open_company_storage" lein run -m oc.storage.util.import -- -d ./opt/samples/
```

#### Generate sample data

To generate sample data into an existing org, run:

```
lein run -m oc.storage.util.generate -- <org-slug> <config-file> <start-date> <end-data>
```

e.g.

```
lein run -m oc.storage.util.generate -- 18f ./opt/generate.edn 2017-01-01 2017-06-30
```

See the sample generation config file `./opt/generate.edn` for how the sample data generation can be customized.
 
#### Force initial indexing or re-indexing

You can initiate an initial indexing of all the content stored in the storage service, or a re-indexing of the content from the commandline. Ensure you have `AWS_SQS_SEARCH_INDEX_QUEUE` set to the search service SQS queue you are using:

```console
export AWS_SQS_SEARCH_INDEX_QUEUE=change-me
```

Then run:

```
lein full-index
```

Or you can do it from the REPL. Ensure you have the `aws-sqs-search-index-queue` configuration set to the search service SQS queue you are using:

```clojure
config/aws-sqs-search-index-queue
```

then, from the REPL, run:

```clojure
(send-data-to-search-index)
```


## Testing

Tests are run in continuous integration of the `master` and `mainline` branches on [Travis CI](https://travis-ci.org/open-company/open-company-storage):

[![Build Status](http://img.shields.io/travis/open-company/open-company-storage.svg?style=flat)](https://travis-ci.org/open-company/open-company-storage)

To run the tests locally:

```console
lein test!
```


## Participation

Please note that this project is released with a [Contributor Code of Conduct](https://github.com/open-company/open-company-storage/blob/mainline/CODE-OF-CONDUCT.md). By participating in this project you agree to abide by its terms.


## License

Distributed under the [GNU Affero General Public License Version 3](https://www.gnu.org/licenses/agpl-3.0.en.html).

Copyright © 2015-2018 OpenCompany, LLC.

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the [GNU Affero General Public License](https://www.gnu.org/licenses/agpl-3.0.en.html) for more details.