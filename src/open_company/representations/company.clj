(ns open-company.representations.company
  (:require [clojure.set :as cset]
            [defun :refer (defun defun-)]
            [cheshire.core :as json]
            [open-company.representations.common :as common]
            [open-company.representations.section :as section-rep]
            [open-company.resources.company :as company]
            [open-company.representations.stakeholder-update :as su-rep]))

(def media-type common/company-media-type)
(def collection-media-type "application/vnd.collection+vnd.open-company.company+json;version=1")

(def section-list-media-type "application/vnd.open-company.section-list.v1+json")

(def recognized-links #{:self :update :partial-update :delete :section-list})

(def ^:private clean-properties [:id :org-id])

(defun url
  ([slug :guard string?] (str "/companies/" (name slug)))
  ([company :guard map?] (url (name (:slug company))))
  ([company :section-list] (str (url company) "/section/new"))
  ([company updated-at] (str (url company) "?as-of=" updated-at)))

(defn- self-link [company]
  (common/self-link (url company) media-type))

(defn- update-link [company]
  (common/update-link (url company) media-type))

(defn- partial-update-link [company]
  (common/partial-update-link (url company) media-type))

(defn- delete-link [company]
  (common/delete-link (url company)))

(defn- revision-link [company updated-at]
  (common/revision-link (url company updated-at) updated-at media-type))

(defn- section-list-link [company]
  (common/link-map "section-list" common/GET (url company :section-list) section-list-media-type))

(defn- company-links*
  "Return a list of links specified via the `links` argument.
   If all possible links should be returned pass `:all-links`"
  [company links]
  {:pre [(or (sequential? links) (set? links) (= links :all-links))]}
  (if (= :all-links links)
    (company-links* company recognized-links)
    (let [links (set links)]
      (assert (empty? (cset/difference links recognized-links))
              (str "Unrecognized link types " (cset/difference links recognized-links)))
      (cond-> []
        (:self links)           (conj (self-link company))
        (:update links)         (conj (update-link company))
        (:partial-update links) (conj (partial-update-link company))
        (:delete links)         (conj (delete-link company))
        (:section-list links)   (conj (section-list-link company))))))

(defn- company-links
  "Add the company HATEAOS links to the company"
  [company links]
  (assoc company :links (company-links* company links))) 

(defn- sections*
  [{:keys [slug] :as company} conn authorized]
  (reduce (fn [company section]
            (update company section (fn update-fn [s]
                                      (section-rep/section-for-rendering
                                       conn
                                       (merge s {:company-slug slug
                                                 :section-name section})
                                       authorized))))
          company
          (map keyword (flatten (vals (:sections company))))))

(defn- stakeholder-updates-link
  "Add HATEOAS links to existing stakeholder updates"
  [company]
  (update-in company [:links] conj (su-rep/stakeholder-updates-link (url company))))

(defn- stakeholder-update-create-link
  "Add the HATEOAS link to create a new stakeholder update if authorized"
  [company authorized]
  (if authorized
    (update-in company [:links] conj (su-rep/create-link (url company)))
    company))

(defn- stakeholder-update-links
  "Add the stakeholder update HATEOAS links to the company"
  [company authorized]
  (-> company
    (stakeholder-update-create-link authorized)
    (stakeholder-updates-link)))

(defn- company-for-rendering
  "Get a representation of the company for the REST API"
  [conn company authorized]
  (let [slug (:slug company)]
  (-> company
    (common/clean clean-properties)
    (assoc :revisions (company/list-revisions conn slug))
    (update :revisions #(map (fn [rev] (revision-link slug (:updated-at rev))) %))
    (company-links (if authorized :all-links [:self]))
    (stakeholder-update-links authorized)
    (sections* conn authorized)
    (assoc :archived (company/archived-sections conn slug)))))

(defn render-company
  "Create a JSON representation of a company for the REST API"
  ([conn company]
   (render-company conn company true))
  ([conn company authorized]
  (json/generate-string (company-for-rendering conn company authorized) {:pretty true})))

(defn render-company-list
  "Create a JSON representation of a group of companies for the REST API"
  [companies]
  (json/generate-string
   {:collection {:version common/json-collection-version
                 :href "/companies"
                 :links [(common/self-link "/companies" collection-media-type)]
                 :companies (map #(company-links % [:self]) companies)}}
   {:pretty true}))