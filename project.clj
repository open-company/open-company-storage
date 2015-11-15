(defproject open-company-api "0.0.2-SNAPSHOT"
  :description "OpenCompany.io Platform API"
  :url "https://opencompany.io/"
  :license {
    :name "Mozilla Public License v2.0"
    :url "http://www.mozilla.org/MPL/2.0/"
  }

  :min-lein-version "2.5.1" ; highest version supported by Travis-CI as of 7/5/2015

  ;; JVM memory
  :jvm-opts ^:replace ["-Xms512m" "-Xmx2048m" "-server"]

  ;; Production dependencies
  :dependencies [
    [org.clojure/clojure "1.8.0-RC1"] ; Lisp on the JVM http://clojure.org/documentation
    [org.clojure/core.match "0.3.0-alpha4"] ; Erlang-esque pattern matching https://github.com/clojure/core.match
    [org.clojure/core.async "0.2.371"] ; Dependency of core.match and RethinkDB https://github.com/clojure/core.async
    [defun "0.3.0-alapha"] ; Erlang-esque pattern matching for Clojure functions https://github.com/killme2008/defun
    [ring/ring-devel "1.4.0"] ; Web application library https://github.com/ring-clojure/ring
    [ring/ring-core "1.4.0"] ; Web application library https://github.com/ring-clojure/ring
    [jumblerg/ring.middleware.cors "1.0.1"] ; CORS library https://github.com/jumblerg/ring.middleware.cors
    [http-kit "2.1.19"] ; Web server http://http-kit.org/
    [compojure "1.4.0"] ; Web routing https://github.com/weavejester/compojure
    [liberator "0.13"] ; WebMachine (REST API server) port to Clojure https://github.com/clojure-liberator/liberator
    [com.apa512/rethinkdb "0.11.0"] ; RethinkDB client for Clojure https://github.com/apa512/clj-rethinkdb
    [prismatic/schema "1.0.3"] ; Data validation https://github.com/Prismatic/schema
    [environ "1.0.1"] ; Get environment settings from different sources https://github.com/weavejester/environ
    [com.taoensso/timbre "4.1.4"] ; Logging https://github.com/ptaoussanis/timbre
    [raven-clj "1.3.1"] ; Clojure interface to Sentry error reporting https://github.com/sethtrain/raven-clj
    [clj-http "2.0.0"] ; HTTP client https://github.com/dakrone/clj-http
    [org.clojure/tools.cli "0.3.3"] ; command-line parsing https://github.com/clojure/tools.cli
    [clj-jwt "0.1.1"] ; Clojure library for JSON Web Token (JWT) https://github.com/liquidz/clj-jwt
 ]

  ;; Production plugins
  :plugins [
    [lein-ring "0.9.7"] ; Common ring tasks https://github.com/weavejester/lein-ring
    [lein-environ "1.0.1"] ; Get environment settings from different sources https://github.com/weavejester/environ
  ]

  :profiles {

    ;; QA environment and dependencies
    :qa {
      :env {
        :db-name "open_company_qa"
        :liberator-trace false
        :hot-reload false
        :open-company-auth-passphrase "this_is_a_qa_secret" ; JWT secret
      }
      :dependencies [
        [midje "1.8.2"] ; Example-based testing https://github.com/marick/Midje
        [ring-mock "0.1.5"] ; Test Ring requests https://github.com/weavejester/ring-mock
      ]
      :plugins [
        [lein-midje "3.2"] ; Example-based testing https://github.com/marick/lein-midje
        [jonase/eastwood "0.2.1"] ; Clojure linter https://github.com/jonase/eastwood
        [lein-kibit "0.1.2"] ; Static code search for non-idiomatic code https://github.com/jonase/kibit
      ]
    }

    ;; Dev environment and dependencies
    :dev [:qa {
      :env ^:replace {
        :db-name "open_company_dev"
        :liberator-trace true ; liberator debug data in HTTP response headers
        :hot-reload true ; reload code when changed on the file system
        :open-company-auth-passphrase "this_is_a_dev_secret" ; JWT secret
      }
      :plugins [
        [lein-bikeshed "0.2.0"] ; Check for code smells https://github.com/dakrone/lein-bikeshed
        [lein-checkall "0.1.1"] ; Runs bikeshed, kibit and eastwood https://github.com/itang/lein-checkall
        [lein-pprint "1.1.2"] ; pretty-print the lein project map https://github.com/technomancy/leiningen/tree/master/lein-pprint
        [lein-ancient "0.6.8"] ; Check for outdated dependencies https://github.com/xsc/lein-ancient
        [lein-spell "0.1.0"] ; Catch spelling mistakes in docs and docstrings https://github.com/cldwalker/lein-spell
        [lein-deps-tree "0.1.2"] ; Print a tree of project dependencies https://github.com/the-kenny/lein-deps-tree
        [venantius/yagni "0.1.4"] ; Dead code finder https://github.com/venantius/yagni
      ]  
    }]
    :repl [:dev {
      :dependencies [
        [org.clojure/tools.nrepl "0.2.12"] ; Network REPL https://github.com/clojure/tools.nrepl
        [aprint "0.1.3"] ; Pretty printing in the REPL (aprint ...) https://github.com/razum2um/aprint
      ]
      ;; REPL injections
      :injections [
        (require '[aprint.core :refer (aprint ap)]
                 '[clojure.stacktrace :refer (print-stack-trace)]
                 '[clojure.test :refer :all]
                 '[clj-time.format :as t]
                 '[clojure.string :as s])
      ]
    }]
    ;; Production environment
    :prod {
      :env {
        :db-name "open_company"
        :liberator-trace false
        :hot-reload false
      }
    }
  }

  :aliases {
    "build" ["do" "clean," "deps," "compile"] ; clean and build code
    "init-db" ["run" "-m" "open-company.db.init"] ; create RethinkDB tables and indexes
    "midje!" ["with-profile" "qa" "midje"] ; run all tests
    "autotest" ["with-profile" "qa" "midje" ":autotest"] ; watch for code changes and run affected tests
    "test!" ["with-profile" "qa" "do" "build," "init-db," "midje"] ; build, init the DB and run all tests
    "start" ["do" "init-db," "run"] ; start a development server
    "start!" ["with-profile" "prod" "do" "build," "init-db," "run"] ; start a server in production
    "repl" ["with-profile" "repl" "repl"]
    "spell!" ["spell" "-n"] ; check spelling in docs and docstrings
    "bikeshed!" ["bikeshed" "-v" "-m" "120"] ; code check with max line length warning of 120 characters
    "ancient" ["ancient" ":all" ":allow-qualified"] ; check for out of date dependencies
  }

  ;; ----- Code check configuration -----

  :eastwood {
    ;; Dinable some linters that are enabled by default
    :exclude-linters [:wrong-arity]
    ;; Enable some linters that are disabled by default
    :add-linters [:unused-namespaces :unused-private-vars :unused-locals]

    ;; Exclude testing namespaces
    :tests-paths ["test"]
    :exclude-namespaces [:test-paths]
  }

  ;; ----- API -----

  :ring {
    :handler open-company.app/app
    :reload-paths ["src"] ; work around issue https://github.com/weavejester/lein-ring/issues/68
  }

  :main open-company.app
)