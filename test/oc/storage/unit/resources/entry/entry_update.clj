(ns oc.storage.unit.resources.entry.entry-update
  (:require [midje.sweet :refer :all]
            [oc.lib.schema :as lib-schema]
            [oc.lib.check :as check]
            [oc.lib.test-setup :as ts]
            [oc.lib.db.pool :as pool]
            [oc.lib.db.common :as db-common]
            [oc.lib.resources :as r]
            [oc.storage.config :as config]
            [oc.storage.resources.common :as common]
            [oc.storage.resources.org :as org]
            [oc.storage.resources.board :as s]))

;; ----- Tests -----

; (with-state-changes [(before :contents (ts/setup-system!))
;                      (after :contents (ts/teardown-system!))
;                      (before :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
;                                       (org/delete-org! conn r/slug)
;                                       (org/create-org! conn (org/->org r/open r/coyote))))
;                      (after :facts (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]
;                                      (org/delete-org! conn r/slug)))]

;   (pool/with-pool [conn (-> @ts/test-system :db-pool :pool)]

;     (facts "about updating entries"

;       (facts "when a topic exists, but is not specified in the update"))))