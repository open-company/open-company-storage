# [OpenCompany.io](https://opencompany.io) Platform API

## Overview

Build your company in the open with transparency for your co-founders, your team of employees and contractors, and your investors. Or open up your company for everyone, your customers and the rest of the startup community.

[OpenCompany.io](https://opencompany.io) is GitHub for the rest of your company:

* Dashboard - An easy tool for founders to provide transparency to their teams and beyond.
* Founders' Guide - Tools, best practices and insights from open company founders and their companies.
* Open Company Directory - Founders sharing with their teams and beyond.
* Community - Spread the word and knowledge and inspire more founders to open up.

Like the open companies we promote and support, the [OpenCompany.io](https://opencompany.io) platform is completely transparent. The company supporting this effort, Transparency, LLC, is an open company. The [platform](https://github.com/open-company/open-company-web) is open source software, and open company data is [open data](https://en.wikipedia.org/wiki/Open_data) accessible through this platform API.

To get started, head to [OpenCompany.io](https://opencompany.io).

## Local Setup

Users of the [OpenCompany.io](https://opencompany.io) platform should get started by going to [OpenCompany.io](https://opencompany.io). The following setup is for developers wanting to work on the platform's API software.

Most of the dependencies are internal, meaning [Leiningen](https://github.com/technomancy/leiningen) will handle getting them for you. There are a few exceptions:

* [Java 7/8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) - a Java 7 or 8 JRE is needed to run Clojure
* [Leiningen](https://github.com/technomancy/leiningen) - Clojure's build and dependency management tool
* [RethinkDB](http://rethinkdb.com/) - a multi-modal (document, key/value, relational) open source NoSQL database

Chances are your system already has Java 7 or 8 installed. You can verify this with:

```console
java -version
```

If you do not have Java 7 or 8 [download it]((http://www.oracle.com/technetwork/java/javase/downloads/index.html)) and follow the installation instructions.

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

RethinkDB is easy to install with official and community supported packages for most operating systems.

Assuming you are running Mac OS X and are a [Homebrew](http://mxcl.github.com/homebrew/) user, use brew to install RethinkDB:

```console
brew update && brew install rethinkdb
```

If you don't use brew, there is a binary package available for Mac OS X from the [Mac download page](http://rethinkdb.com/docs/install/osx/).

If you run Linux on your development environment you can get a package for you distribution or compile from source. Details are on the [installation page](http://rethinkdb.com/docs/install/).

RethinkDB [isn't supported on Windows](https://github.com/rethinkdb/rethinkdb/issues/1100) directly. If you are stuck on Windows, you can run Linux in a virtualized environment to host RethinkDB.

## Usage

## License

Distributed under the [Mozilla Public License v2.0](http://www.mozilla.org/MPL/2.0/).

Copyright © 2015 Transparency, LLC