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
(require '[oc.storage.resources.update :as update] :reload)

;; productive set of development namespaces (REST API)
(require '[cheshire.core :as json])
(require '[ring.mock.request :refer (request body content-type header)])
(require '[open-company.lib.rest-api-mock :refer (api-request)] :reload)
(require '[oc.storage.app :refer (app)] :reload)
(require '[oc.lib.api.common :as api-common])
(require '[oc.storage.api.common :as storage-common] :reload)
(require '[oc.storage.api.entry-point :as entry-point-api] :reload)
(require '[oc.storage.api.orgs :as orgs-api] :reload)
(require '[oc.storage.api.boards :as boards-api] :reload)
(require '[oc.storage.api.entries :as entries-api] :reload)
(require '[oc.storage.representations.org :as org-rep] :reload)
(require '[oc.storage.representations.board :as board-rep] :reload)
(require '[oc.storage.representations.topic :as topic-rep] :reload)
(require '[oc.storage.representations.entry :as entry-rep] :reload)

;; make a (fake) REST API request
(api-request :get "/companies/buffer" {:headers {:Accept (company-rep/media-type)}})

;; print last exception
(print-stack-trace *e)

;; Validate sections in sections.edn against schema (should be all nils)
(def sections*
  (->> common/sections (map #(assoc % :company-slug "x"))))
(map #(schema/check common/Section %) sections*)

;; RethinkDB usage
(def conn2 [:host "127.0.0.1" :port 28015 :db "open_company"])

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

;; Get orgs by team IDs
(aprint (with-open [c (apply r/connect conn2)]
  (-> (r/table "orgs")
      (r/get-all ["51ab-4c86-a474" "51ab-4c86-a477"] {:index :team-id})
      (r/run c))))

;; Update the sections in a company
(with-open [c (apply r/connect conn2)]
  (-> (r/table "companies")
      (r/get "open")
      (r/update {:sections ["progress" "company"]})
      (r/run c)))

(with-open [c (apply r/connect conn2)]
  (-> (r/table "companies")
      (r/get "open")
      (r/replace (r/fn [company]
        (r/without company [:mission])))
      (r/run c)))

;; Empty out the stakeholder update template
(with-open [c (apply r/connect conn2)]
  (-> (r/table "companies")
      (r/get "open")
      (r/update {:stakeholder-update (r/literal {:title "" :sections []})})
      (r/run c)))

;; Mark a company as public/private
(with-open [c (apply r/connect conn2)]
  (-> (r/table "companies")
      (r/get "startup-city")
      (r/update {:public true})
      (r/run c)))

;; Remove a sub-property of a topic from a company
(with-open [c (apply r/connect conn2)]
  (-> (r/table "companies")
      (r/get "startup-city")
      (r/replace (r/fn [company]
        (r/without company [{:competition {:icon true}}])))
      (r/run c)))

;; Update a sub-property of a section for a company
(with-open [c (apply r/connect conn2)]
  (-> (r/table "companies")
      (r/get "open")
      (r/update {:finances {:body "<p>It's time to learn. That's it, really. Put it in the hands of people we admire and trust, and <b>LEARN FAST</b>.</p><p><img src=\"https://cdn.filestackcontent.com/ge9NSlJTP2AXfwl0nGvk\" data-height=\"370\" data-width=\"555\"><br></p><p><br></p>"}})
      (r/run c)))

;; Get all the topic revisions of a specific topic
(aprint (with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
    (r/filter (r/fn [section] {:section-name "business-development"}))
    (r/run c))))

(map :title (with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
    (r/filter (r/fn [section] {:section-name "custom-aaaa"}))
    (r/run c))))

;; Update the topic name of topic revisions of a specific topic
(with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
    (r/filter (r/fn [section] {:section-name "customer-service"}))
    (r/update {:section-name "customers"})
    (r/run c)))

(with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
    (r/filter (r/fn [section] {:section-name "business-development"}))
    (r/update {:section-name "custom-aaaa"})
    (r/run c)))

;; Update the topic topic name of a topic in a company
(def bus (:business-development (company/get-company conn "buff")))
(with-open [c (apply r/connect conn2)]
  (-> (r/table "companies")
    (r/get "buff")
    (r/update (r/fn [company] {:custom-aaaa bus}))
    (r/run c)))
(with-open [c (apply r/connect conn2)]
  (-> (r/table "companies")
      (r/get "buff")
      (r/replace (r/fn [company]
        (r/without company [:business-development])))
      (r/run c)))

;; Get all the topic revisions for a company
(aprint (with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
    (r/get-all ["buffer"] {:index "company-slug"})
    (r/run c))))

;; Get all the topic revisions of a specific type for a company
(aprint (with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
    (r/get-all [["open" "finances"]] {:index "company-slug-section-name"})
    (r/run c))))

;; Remove all the topic revisions of a specific type for a company
(with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
    (r/get-all [["open" "finances"]] {:index "company-slug-section-name"})
    (r/delete)
    (r/run c)))

;; Get a topic revisions by ID
(aprint (with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
    (r/get-all ["21c9ddd4-6d1c-47a5-b6c1-1308fed08523"] {:index "id"})
    (r/run c))))

;; Delete a topic revision by ID
(with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
    (r/get-all ["c4b035a9-f33a-40a3-9c5f-49632e5f32d8"] {:index "id"})
    (r/delete)
    (r/run c)))

;; Update a property of a topic revision by ID
(with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
    (r/get-all ["5ee22ac8-91ba-408f-9afb-e7546512ce90"] {:index "id"})
    (r/update {:created-at "2016-12-03T16:23:11.560Z" :updated-at "2016-12-03T16:23:11.560Z"})
    (r/run c)))

;; Remove a property from all topic revisions
(with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
    (r/replace (r/fn [section]
      (r/without section [:core])))
    (r/run c)))

;; Get all the stakeholder updates for a company
(with-open [c (apply r/connect conn2)]
  (-> (r/table "stakeholder_updates")
    (r/get-all ["message-io"] {:index "company-slug"})
    (r/run c)))

;; Update the property of a stakeholder update by update slug
(with-open [c (apply r/connect conn2)]
  (-> (r/table "stakeholder_updates")
    (r/get-all [["open" "investor-update-december-2016-819e6"]] {:index "company-slug-slug"})
    (r/update {:marketing {:headline ""}})
    (r/run c)))

;; Provide a new slug for a company
(with-open [c (apply r/connect conn2)]
  (-> (r/table "companies")
      (r/insert (assoc (company/get-company conn "old-slug") :slug "new-slug"))
      (r/run c)))
(with-open [c (apply r/connect conn2)]
  (-> (r/table "sections")
      (r/get-all ["old-slug"] {:index "company-slug"})
      (r/update {:slug "new-slug"})
      (r/run c)))
(company/delete-company! conn "old-slug")

;; for more RethinkDB help, see:
;; https://github.com/apa512/clj-rethinkdb
;; http://apa512.github.io/clj-rethinkdb/
;; https://rethinkdb.com/api/python/