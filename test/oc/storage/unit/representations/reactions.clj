(ns oc.storage.unit.representations.reactions
  (:require [midje.sweet :refer :all]
            [midje.util :refer (testable-privates)]
            [oc.storage.config :as config]
            [oc.storage.representations.entry :as entry-rep]))

;; this is the function we're testing in this namespace
(testable-privates oc.storage.representations.content reactions-and-links)

(def uuid "1234-abcd-1234") ; dummy uuid, not important to what we're testing

;; ----- Utility Functions -----

(defn fake-reactions
  "
  Create as many fake reactions as specified by the counter.

  For the purposes of this test, the only thing a reaction map needs is the `:reaction` key.
  "
  [unicode counter]
  (repeat counter {:reaction unicode}))

(defn- fake-reaction-data 
  "Given counts of the 5 test reactions, return fake data pretending to be from the DB."
  [👌-count 👀-count 🇫🇰-count 😎-count 🙉-count]
  (flatten [(fake-reactions "👌" (Integer. 👌-count))
            (fake-reactions "👀" (Integer. 👀-count))
            (fake-reactions "🇫🇰" (Integer. 🇫🇰-count))
            (fake-reactions "😎" (Integer. 😎-count))
            (fake-reactions "🙉" (Integer. 🙉-count))]))

(defn- correct-selection-and-order? [👌 👌-count 👀 👀-count 🇫🇰 🇫🇰-count 😎 😎-count 🙉 🙉-count]
  (let [inputs (zipmap [👌 👀 🇫🇰 😎 🙉] ["👌" "👀" "🇫🇰" "😎" "🙉"]) ; just looking for 1st, 2nd, 3rd
        reaction-data (fake-reaction-data 👌-count 👀-count 🇫🇰-count 😎-count 🙉-count)]
    ;; "stub" the config var for default reactions to match the test case
    (intern 'oc.storage.config 'default-reactions [(get inputs "first") (get inputs "second") (get inputs "third")])
    ;; setup the data from the "DB" to match this test case
    (let [test-output (reactions-and-links uuid uuid uuid reaction-data uuid)
          unicode-only (map :reaction test-output)]
      [(inc (.indexOf unicode-only "👌"))
       (inc (.indexOf unicode-only "👀"))
       (inc (.indexOf unicode-only "🇫🇰"))
       (inc (.indexOf unicode-only "😎"))
       (inc (.indexOf unicode-only "🙉"))])))

;; ----- Combinatorial Tests -----

;; Testing that reactions are returned in their configuration order, unless reactions that aren't
;; in the configuration are present (legacy reactions), in which case the highest count reactions
;; are returned and are then in configuration order for any that are in the configuration (higher precedence)
;; and in count order for any that are legacy (lower precedence).

; (tabular 

;   (fact "Reactions are selected and ordered properly based on their counts and their configuration"
  
;     (correct-selection-and-order? ?👌 ?👌-count ?👀 ?👀-count ?🇫🇰 ?🇫🇰-count 
;                                   ?😎 ?😎-count ?🙉 ?🙉-count) => ?expected)

; ?👌 ?👌-count ?👀 ?👀-count ?🇫🇰 ?🇫🇰-count ?😎 ?😎-count ?🙉 ?🙉-count ?expected
; "none" 0 "none" 0 "third" 0 "second" 0 "first" 0 [0 0 3 2 1]
; "none" 5 "third" 5 "none" 5 "first" 0 "second" 5 [3 2 0 0 1]
; "none" 10 "second" 10 "first" 10 "none" 0 "third" 10 [0 2 1 0 3]
; "none" 15 "first" 15 "second" 15 "third" 0 "none" 15 [3 1 2 0 0]
; "third" 20 "none" 5 "second" 10 "first" 10 "none" 0 [3 0 2 1 0]
; "none" 20 "second" 20 "none" 20 "third" 0 "first" 20 [3 2 0 0 1]
; "none" 25 "third" 25 "second" 25 "none" 0 "first" 25 [0 3 2 0 1]
; "second" 10 "third" 0 "none" 15 "none" 10 "first" 5 [1 0 3 2 0]
; "none" 10 "first" 15 "third" 20 "none" 20 "second" 0 [0 1 2 3 0]
; "none" 5 "none" 20 "first" 15 "second" 15 "third" 0 [0 3 1 2 0]
; "third" 20 "first" 0 "second" 5 "none" 15 "none" 10 [1 0 0 3 2]
; "none" 15 "third" 10 "none" 5 "second" 5 "first" 0 [3 2 0 1 0]
; "none" 5 "second" 0 "first" 25 "third" 25 "none" 0 [3 0 1 2 0]
; "none" 0 "none" 25 "first" 10 "third" 15 "second" 5 [0 3 1 2 0]
; "none" 0 "second" 10 "third" 25 "first" 20 "none" 5 [0 2 3 1 0]
; "none" 20 "none" 15 "second" 0 "first" 5 "third" 5 [3 2 0 1 0]
; "none" 15 "none" 5 "first" 20 "second" 25 "third" 5 [3 0 1 2 0]
; "none" 0 "first" 5 "none" 15 "third" 5 "second" 10 [0 1 3 0 2]
; "none" 15 "none" 20 "third" 0 "first" 10 "second" 10 [2 3 0 1 0]
; "none" 25 "second" 0 "none" 10 "first" 5 "third" 15 [3 0 2 0 1]
; "second" 10 "first" 20 "none" 10 "third" 25 "none" 25 [0 1 0 2 3]
; "none" 10 "second" 5 "third" 0 "none" 15 "first" 15 [2 0 0 3 1]
; "none" 5 "third" 25 "none" 0 "second" 20 "first" 10 [0 3 0 2 1]
; "none" 0 "none" 25 "first" 5 "second" 10 "third" 15 [0 3 0 1 2]
; "none" 10 "third" 5 "first" 25 "second" 5 "none" 20 [2 0 1 0 3]
; "none" 25 "none" 10 "first" 0 "third" 25 "second" 10 [3 0 0 2 1]
; "none" 25 "first" 20 "third" 5 "second" 20 "none" 15 [3 1 0 2 0]
; "none" 5 "first" 10 "third" 0 "none" 10 "second" 20 [0 1 0 3 2]
; "none" 20 "none" 25 "third" 15 "first" 25 "second" 20 [0 3 0 1 2]
; "third" 0 "none" 15 "first" 0 "second" 10 "none" 25 [0 2 0 1 3]
; "first" 25 "none" 10 "second" 20 "third" 10 "none" 10 [1 0 2 3 0]
; "none" 0 "third" 15 "second" 5 "none" 25 "first" 15 [0 2 0 3 1]
; "first" 5 "none" 15 "none" 25 "none" 10 "second" 10 [0 2 3 0 1]
; "none" 5 "none" 0 "third" 20 "first" 5 "second" 25 [0 0 3 1 2]
; "none" 25 "second" 15 "none" 5 "first" 15 "third" 20 [3 0 0 1 2]
; "none" 15 "none" 0 "third" 10 "second" 20 "first" 20 [3 0 0 2 1]
; "first" 20 "third" 10 "none" 20 "none" 15 "second" 15 [1 0 3 0 2]
; "second" 0 "first" 25 "third" 20 "none" 5 "none" 0 [0 1 2 3 0]
; "third" 25 "first" 20 "none" 15 "none" 5 "second" 5 [2 1 3 0 0]
; "none" 10 "none" 10 "second" 5 "first" 15 "third" 25 [0 3 0 1 2]
; "none" 20 "none" 5 "first" 15 "second" 20 "third" 25 [3 0 0 1 2]
; "none" 15 "first" 20 "second" 25 "none" 15 "third" 15 [0 1 2 0 3]
; "first" 15 "second" 25 "third" 15 "none" 5 "none" 25 [1 2 0 0 3]
; "first" 5 "none" 15 "second" 10 "third" 20 "none" 15 [0 2 0 1 3]
; "first" 0 "none" 10 "second" 15 "none" 0 "third" 20 [0 3 1 0 2]
; "first" 25 "second" 5 "none" 5 "third" 10 "none" 0 [1 2 0 3 0]
; "third" 10 "second" 25 "none" 20 "none" 0 "first" 15 [0 2 3 0 1]
; "second" 5 "none" 5 "first" 0 "none" 15 "third" 15 [1 0 0 3 2]
; "second" 20 "third" 20 "none" 25 "first" 0 "none" 10 [1 2 3 0 0]
; "first" 0 "third" 20 "none" 10 "second" 25 "none" 5 [0 2 3 1 0]
; "second" 15 "none" 10 "first" 5 "third" 20 "none" 20 [1 0 0 2 3]
; "third" 5 "first" 10 "second" 25 "none" 20 "none" 20 [0 0 1 3 2]
; "first" 10 "second" 0 "third" 0 "none" 5 "none" 0 [1 2 0 3 0]
; ; "third" 15 "second" 0 "none" 0 "none" 25 "none" 0 [2 0 0 3 0] ; funky case, no first configured and only 2 w/ reactions
; "second" 25 "first" 15 "none" 0 "none" 0 "third" 0 [2 1 0 0 3]

; )

; ;; ----- Test Plan Modeling -----

; (comment

; ;; Hexawise model

; ; 👌: first, second, third, none
; ; 👌-count: 0, 5, 10, 15, 20, 25
; ; 👀: first, second, third, none
; ; 👀-count: 0, 5, 10, 15, 20, 25
; ; 🇫🇰: first, second, third, none
; ; 🇫🇰-count: 0, 5, 10, 15, 20, 25
; ; 😎: first, second, third, none
; ; 😎-count: 0, 5, 10, 15, 20, 25
; ; 🙉: first, second, third, none
; ; 🙉-count: 0, 5, 10, 15, 20, 25

; ;; Constraints
; ;; All "first"s invalidated against all other "first"s
; ;; All "second"s invalidated against all other "second"s
; ;; All "third"s invalidated against all other "third"s
; )

; (def csv

; ;; Hexawise 2-way test cases CSV
; [
; 1,"none","0","none","0","third","0","second","0","first","0"
; 2,"none","5","third","5","none","5","first","0","second","5"
; 3,"none","10","second","10","first","10","none","0","third","10"
; 4,"none","15","first","15","second","15","third","0","none","15"
; 5,"third","20","none","5","second","10","first","10","none","0"
; 6,"none","20","second","20","none","20","third","0","first","20"
; 7,"none","25","third","25","second","25","none","0","first","25"
; 8,"second","10","third","0","none","15","none","10","first","5"
; 9,"none","10","first","15","third","20","none","20","second","0"
; 10,"none","5","none","20","first","15","second","15","third","0"
; 11,"third","20","first","0","second","5","none","15","none","10"
; 12,"none","15","third","10","none","5","second","5","first","0"
; 13,"none","5","second","0","first","25","third","25","none","0"
; 14,"none","0","none","25","first","10","third","15","second","5"
; 15,"none","0","second","10","third","25","first","20","none","5"
; 16,"none","20","none","15","second","0","first","5","third","5"
; 17,"none","15","none","5","first","20","second","25","third","5"
; 18,"none","0","first","5","none","15","third","5","second","10"
; 19,"none","15","none","20","third","0","first","10","second","10"
; 20,"none","25","second","0","none","10","first","5","third","15"
; 21,"second","10","first","20","none","10","third","25","none","25"
; 22,"none","10","second","5","third","0","none","15","first","15"
; 23,"none","5","third","25","none","0","second","20","first","10"
; 24,"none","0","none","25","first","5","second","10","third","15"
; 25,"none","10","third","5","first","25","second","5","none","20"
; 26,"none","25","none","10","first","0","third","25","second","10"
; 27,"none","25","first","20","third","5","second","20","none","15"
; 28,"none","5","first","10","third","0","none","10","second","20"
; 29,"none","20","none","25","third","15","first","25","second","20"
; 30,"third","0","none","15","first","0","second","10","none","25"
; 31,"first","25","none","10","second","20","third","10","none","10"
; 32,"none","0","third","15","second","5","none","25","first","15"
; 33,"first","5","none","15","none","25","none","10","second","10"
; 34,"none","5","none","0","third","20","first","5","second","25"
; 35,"none","25","second","15","none","5","first","15","third","20"
; 36,"none","15","none","0","third","10","second","20","first","20"
; 37,"first","20","third","10","none","20","none","15","second","15"
; 38,"second","0","first","25","third","20","none","5","none","0"
; 39,"third","25","first","20","none","15","none","5","second","5"
; 40,"none","10","none","10","second","5","first","15","third","25"
; 41,"none","20","none","5","first","15","second","20","third","25"
; 42,"none","15","first","20","second","25","none","15","third","15"
; 43,"first","15","second","25","third","15","none","5","none","25"
; 44,"first","5","none","15","second","10","third","20","none","15"
; 45,"first","0","none","10","second","15","none","0","third","20"
; 46,"first","25","second","5","none","5","third","10","none","0"
; 47,"third","10","second","25","none","20","none","0","first","15"
; 48,"second","5","none","5","first","0","none","15","third","15"
; 49,"second","20","third","20","none","25","first","0","none","10"
; 50,"first","0","third","20","none","10","second","25","none","5"
; 51,"second","15","none","10","first","5","third","20","none","20"
; 52,"third","5","first","10","second","25","none","20","none","20"
; 53,"first","10","second","0","third","0","none","5","none","0"
; 54,"third","15","second","0","none","0","none","25","none","0"
; 55,"second","25","first","15","none","0","none","0","third","0" 
; ]
;   )

; (defn- to-output
;   "Odd CSV items need to be turned into Integers and even ones quote wrapped strings."
;   [i data]
;   (if (odd? i)
;     (Integer. data)
;     (str "\"" data "\"")))

; (defn csv-to-midje
;   "Convert Hexawise CSV output into midje tabular data input."
;   []
;   (println (clojure.string/join "\n" ; join rows with newlines
;     (->> csv
;       (partition 11) ; separate each row
;       (map rest) ; drop the test case #
;       (map #(map-indexed to-output %)) ; convert each data element to string or int
;       (map #(clojure.string/join " " %)))))) ; join with spaces