(ns open-company.unit.resources.company.company-create
  (:require [midje.sweet :refer :all]
            [open-company.lib.check :as check]
            [open-company.lib.resources :as r]
            [open-company.lib.db :as db]
            [open-company.resources.common :as common]
            [open-company.resources.company :as c]))

;; ----- Startup -----

(db/test-startup)

;; ----- Tests -----

(with-state-changes [(before :facts (c/delete-all-companies!))
                     (after :facts (c/delete-all-companies!))]

  (facts "about company creation"

    (fact "it fails to create a company if no org-id is provided"
      (c/create-company! (c/->company r/open (dissoc r/coyote :org-id))) => (throws Exception))

    (facts "it returns the company after successful creation"
      (c/create-company! (c/->company r/open r/coyote)) => (contains r/open)
      (c/get-company r/slug) => (contains r/open))

    (facts "it accepts unicode company names"
      (doseq [good-name r/names]
        (let [new-oc (assoc r/open :name good-name)]
          (c/create-company! (c/->company new-oc r/coyote)) => (contains new-oc)
          (c/get-company r/slug) => (contains new-oc)
          (c/delete-company r/slug))))

    (facts "it creates timestamps"
      (let [company (c/create-company! (c/->company r/open r/coyote))
            created-at (:created-at company)
            updated-at (:updated-at company)
            retrieved-company (c/get-company r/slug)]
        (check/timestamp? created-at) => true
        (check/about-now? created-at) = true
        (= created-at updated-at) => true
        (= created-at (:created-at retrieved-company)) => true
        (= updated-at (:updated-at retrieved-company)) => true))

    (facts "it adds timestamps to notes"
      (let [co (c/->company (assoc r/open :finances r/finances-notes-section-1) r/coyote)
            company (c/create-company! co)
            from-db (c/get-company (:slug r/open))]
        (get-in from-db [:updated-at]) => (:updated-at company)
        (get-in from-db [:finances :notes :updated-at]) => (:updated-at company)))

    (fact "it returns the pre-defined categories"
      (:categories (c/create-company! (c/->company r/open r/coyote))) => common/category-names)

    (let [add-section (fn [c section-name] (assoc c section-name (merge {:title (name section-name) :description "x"})))]
      (facts "it returns the sections in the company in the pre-defined order"
        (:sections (c/create-company! (c/->company r/open r/coyote))) => {:progress [] :financial [] :company []}
        (c/delete-company r/slug)
        (:sections (c/create-company! (c/->company (add-section r/open :update) r/coyote))) =>
        {:progress [:update] :financial [] :company []}
        (c/delete-company r/slug)
        (:sections (c/create-company! (c/->company (add-section r/open :values) r/coyote))) =>
        {:progress [] :financial [] :company [:values]}
        (c/delete-company r/slug)
        (:sections (c/create-company!
                    (c/->company (-> r/open
                                     (add-section :mission) (add-section :press) (add-section :help)
                                     (add-section :challenges) (add-section :diversity) (add-section :update))
                                 r/coyote)))
        => {:progress [:update :challenges :press] :financial [] :company [:mission :diversity :help]}))))