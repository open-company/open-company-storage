(ns open-company.util.jwtoken
  (:require [clojure.string :as s]
            [clojure.tools.cli :refer (parse-opts)])
  (:gen-class))

(def identity-props [:user-id :name :real-name :avatar :email :owner :admin])

;; ----- CLI -----

(def cli-options
  [["-h" "--help"]])

(defn usage [options-summary]
  (s/join \newline
     ["This program creates an OpenCompany JWToken for command-line API usage (cURL)."
      ""
      "Usage: lein run -m open-company.util.jwtoken -- identity.edn"
      ""
      "Identity data: an EDN file with the user properties."
      ""
      "Please refer to ./opt/identities for more information on the file format."
      ""]))

(defn data-msg []
  "\nThe data file must be an EDN file with identity information.\n")

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (s/join \newline errors)))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    ;; Try the import
    (try
      (let [data (read-string (slurp (first arguments)))]
        (println data))
      (catch Exception e
        (println e)
        (exit 1 (data-msg))))))