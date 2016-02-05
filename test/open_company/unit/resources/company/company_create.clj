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

;   (fact "about validity checks of a valid new companies"
;     (c/valid-company r/TICKER r/OPEN) => true)

;   (facts "about validity checks of invalid new companies"

;     (facts "when no ticker symbol is provided"
;       (doseq [ticker r/bad-symbols]
;         (c/valid-company ticker (assoc r/OPEN :symbol ticker)) => :invalid-symbol
;         (c/valid-company ticker r/OPEN) => :invalid-symbol
;         (c/valid-company r/TICKER (assoc r/OPEN :symbol ticker)) => :invalid-symbol))

;     (facts "when no name is provided"
;       (c/valid-company r/TICKER (dissoc r/OPEN :name)) => :invalid-name
;       (doseq [bad-name r/bad-names]
;         (c/valid-company r/TICKER (assoc r/OPEN :name bad-name)) => :invalid-name))

;     (fact "when a ticker symbol that's too long is provided"
;       (c/valid-company r/too-long (assoc r/OPEN :symbol r/too-long)) => :invalid-symbol)

;     (future-fact "when a ticker symbol with special characters is provided")

;     (future-fact "when a reserved property is included"))

;   (future-facts "about company creation failures"

;     (future-facts "when no ticker symbol is provided")

;     (future-facts "when no name is provided")

;     (future-facts "when a ticker symbol that's too long is provided")

;     (future-fact "when a ticker symbol with special characters is provided")

;     (future-fact "when a reserved property is included")

;     (future-fact "when a ticker symbol is already used"))

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

    (let [add-section (fn [c section-name] (assoc c section-name (merge {:title (name section-name) :description "x"})))]
      (facts "it adds timestamps to notes"
        (let [w-note  (-> r/open (add-section :growth) (assoc-in [:growth :notes :body] "A Note"))
              co      (c/->company w-note r/coyote)
              company (c/create-company! co)
              from-db (c/get-company (:slug r/open))]
          (get-in from-db [:updated-at]) => (:updated-at company)
          (get-in from-db [:growth :notes :updated-at]) => (:updated-at company)))

      (fact "it returns the pre-defined categories"
        (:categories (c/create-company! (c/->company r/open r/coyote))) => (contains common/category-names))

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
        => {:progress [:update :challenges :press :help] :financial [] :company [:diversity :mission]}))))
