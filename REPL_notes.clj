;; productive set of development namespaces (Clojure API)
(require '[rethinkdb.query :as r])
(require '[schema.core :as schema])
(require '[oc.storage.config :as config])
(require '[oc.lib.db.pool :as pool])
(require '[oc.lib.slugify :as slug])
(require '[oc.lib.db.common :as db-common])
(require '[oc.storage.resources.common :as common] :reload)
(require '[oc.storage.resources.org :as org] :reload)
(require '[oc.storage.resources.board :as board] :reload)
(require '[oc.storage.resources.entry :as entry] :reload)

;; productive set of development namespaces (REST API)
(require '[cheshire.core :as json])
(require '[ring.mock.request :refer (request body content-type header)])
(require '[open-company.lib.rest-api-mock :refer (api-request)] :reload)
(require '[oc.storage.app :refer (app)] :reload)
(require '[oc.lib.api.common :as api-common])
(require '[oc.storage.api.access :as access] :reload)
(require '[oc.storage.api.entry-point :as entry-point-api] :reload)
(require '[oc.storage.api.orgs :as orgs-api] :reload)
(require '[oc.storage.api.activity :as activity-api] :reload)
(require '[oc.storage.api.boards :as boards-api] :reload)
(require '[oc.storage.api.entries :as entries-api] :reload)
(require '[oc.storage.representations.media-types :as mt] :reload)
(require '[oc.storage.representations.org :as org-rep] :reload)
(require '[oc.storage.representations.board :as board-rep] :reload)
(require '[oc.storage.representations.entry :as entry-rep] :reload)

;; make a (fake) REST API request
(api-request :get "/orgs/buffer" {:headers {:Accept (company-rep/media-type)}})

;; print last exception
(print-stack-trace *e)

;; Validate sections in sections.edn against schema (should be all nils)
(def sections*
  (->> common/sections (map #(assoc % :company-slug "x"))))
(map #(schema/check common/Section %) sections*)

;; RethinkDB usage
(def conn2 [:host "127.0.0.1" :port 28015 :db "open_company_storage"])

;; Get an org
(aprint (with-open [c (apply r/connect conn2)]
  (-> (r/table "orgs")
      (r/get "buffer")
      (r/run c))))

;; Get orgs by team ID
(aprint (with-open [c (apply r/connect conn2)]
  (-> (r/table "orgs")
      (r/get-all ["51ab-4c86-a477"] {:index :team-id})
      (r/run c))))

;; Get orgs by team ID
(aprint (with-open [c (apply r/connect conn2)]
  (-> (r/table "orgs")
      (r/get-all ["51ab-4c86-a477"] {:index :team-id})
      (r/run c))))

;; Get orgs by team IDs
(aprint (with-open [c (apply r/connect conn2)]
  (-> (r/table "orgs")
      (r/get-all ["51ab-4c86-a474" "51ab-4c86-a477"] {:index :team-id})
      (r/run c))))

;; Get all the entries for a company
(aprint (with-open [c (apply r/connect conn2)]
  (-> (r/table "entries")
    (r/get-all ["buffer"] {:index "company-slug"})
    (r/run c))))

;; Remove a property from all entries
(with-open [c (apply r/connect conn2)]
  (-> (r/table "entries")
    (r/replace (r/fn [e]
      (r/without e [:data :intervals :metrics :units :prompt])))
    (r/run c)))

;; Update a property in an entry
(with-open [c (apply r/connect conn2)]
  (-> (r/table "entries")
    (r/get "b0d3-4072-954e")
    (r/update (r/fn [e]
      {:published-at "2017-10-27T19:12:40.644Z"}))
    (r/run c)))

;; Provide a new slug for an org
;; Insert a copy with the new slug
(with-open [c (apply r/connect conn2)]
  (-> (r/table "orgs")
      (r/insert (assoc (org/get-org conn "old-slug") :slug "new-slug"))
      (r/run c)))
(org/delete-org! conn "old-slug")

;; for more RethinkDB help, see:
;; https://github.com/apa512/clj-rethinkdb
;; http://apa512.github.io/clj-rethinkdb/
;; https://rethinkdb.com/api/python/