;       (facts "about available options for new section revisions"

;         (fact "with a bad JWToken"
;           (let [response (mock/api-request :options (section-rep/url r/slug :update) {:auth mock/jwtoken-bad})]
;             (:status response) => 401
;             (:body response) => common/unauthorized))

;         (fact "with no company matching the company slug"
;           (let [response (mock/api-request :options (section-rep/url "foo" :update))]
;             (:status response) => 404
;             (:body response) => ""))

;         (fact "with no section matching the section name"
;           (let [response (mock/api-request :options (section-rep/url r/slug :diversity))]
;             (:status response) => 404
;             (:body response) => ""))

;         (fact "with no JWToken"
;           (let [response (mock/api-request :options (section-rep/url r/slug :update) {:skip-auth true})]
;             (:status response) => 204
;             (:body response) => ""
;             ((:headers response) "Allow") => limited-options))

;         (fact "with an organization that doesn't match the company"
;           (let [response (mock/api-request :options (section-rep/url r/slug :update) {:auth mock/jwtoken-sartre})]
;             (:status response) => 204
;             (:body response) => ""
;             ((:headers response) "Allow") => limited-options))

;         (fact "with an organization that matches the company"
;           (let [response (mock/api-request :options (section-rep/url r/slug :update))]
;             (:status response) => 204
;             (:body response) => ""
;             ((:headers response) "Allow") => full-options)))

;       (facts "about failing to update a section"

;         (doseq [method [:put]] ;:patch]]

;           (fact "with an invalid JWToken"
;             (let [response (mock/api-request method (section-rep/url r/slug :update) {:body r/text-section-2
;                                                                                       :auth mock/jwtoken-bad})]
;               (:status response) => 401
;               (:body response) => common/unauthorized)
;             ;; verify the initial section is unchanged
;             (s/get-section conn r/slug :update) => (contains r/text-section-1)
;             (count (s/get-revisions conn r/slug :update)) => 1)

;           (fact "with no JWToken"
;             (let [response (mock/api-request method (section-rep/url r/slug :update) {:body r/text-section-2
;                                                                                       :skip-auth true})]
;               (:status response) => 401
;               (:body response) => common/unauthorized)
;             ;; verify the initial section is unchanged
;             (s/get-section conn r/slug :update) => (contains r/text-section-1)
;             (count (s/get-revisions conn r/slug :update)) => 1)

;           (fact "with an organization that doesn't match the company"
;             (let [response (mock/api-request method (section-rep/url r/slug :update) {:body r/text-section-2
;                                                                                       :auth mock/jwtoken-sartre})]
;               (:status response) => 403
;               (:body response) => common/forbidden)
;             ;; verify the initial section is unchanged
;             (s/get-section conn r/slug :update) => (contains r/text-section-1)
;             (count (s/get-revisions conn r/slug :update)) => 1)