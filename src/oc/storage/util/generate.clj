; (ns oc.storage.util.generate
;   "
;   Commandline client to generate data, in a date range, into an existing OpenCompany org, according to a configuration
;   file.

;   Usage:

;   lein run -m oc.storage.util.generate -- <org-slug> <config-file> <start-date> <end-date>

;   lein run -m oc.storage.util.generate -- 18f ./opt/generate.edn 2017-01-01 2017-06-31
;   "
;   (:require [clojure.string :as s]
;             [clojure.walk :refer (keywordize-keys)]
;             [clojure.tools.cli :refer (parse-opts)]
;             [defun.core :refer (defun-)]
;             [clj-http.client :as http]
;             [cheshire.core :as json]
;             [clj-time.core :as t]
;             [clj-time.format :as f]
;             [oc.lib.db.pool :as db]
;             [oc.lib.db.common :as db-common]
;             [oc.storage.resources.org :as org-res]
;             [oc.storage.resources.board :as board-res]
;             [oc.storage.resources.entry :as entry-res]
;             [oc.storage.config :as c])
;   (:gen-class))

; (def date-parser (f/formatter "YYYY-MM-dd"))

; (def image-types ["animals" "business" "city" "people" "nature" "sports" "technics" "transport"])

; (def charts [
;   {:sheet-id "1X5Ar6_JJ3IviO64-cJ0DeklFuS42BSdXxZV6x5W0qOc" :oid "1033950253"}
;   {:sheet-id "1X5Ar6_JJ3IviO64-cJ0DeklFuS42BSdXxZV6x5W0qOc" :oid "1315570007"}
;   {:sheet-id "1X5Ar6_JJ3IviO64-cJ0DeklFuS42BSdXxZV6x5W0qOc" :oid "1022324161"}
;   {:sheet-id "1X5Ar6_JJ3IviO64-cJ0DeklFuS42BSdXxZV6x5W0qOc" :oid "1340351038"}
;   {:sheet-id "1X5Ar6_JJ3IviO64-cJ0DeklFuS42BSdXxZV6x5W0qOc" :oid "1138076795"}
;   ])

; (def videos [
;   {:id "ZSiuK4Mt9gs" :provider :youtube}
;   {:id "OcCRZkeqFY8" :provider :youtube}
;   {:id "7IXi_ANNMC8" :provider :youtube}
;   {:id "exIDp2vlkmw" :provider :youtube}
;   {:id "FSvNhxKJJyU" :provider :youtube}
;   {:id "c1nMpeLEEj8" :provider :youtube}
;   {:id "BDlBT6g6OYM" :provider :youtube}
;   ])

; ;; ----- Author Generation -----

; (defn- author-data []
;   (-> "https://randomuser.me/api/"
;     (http/get {:accept :json})
;     :body
;     json/parse-string
;     keywordize-keys
;     :results
;     first))

; (defn- author []
;   (let [data (author-data)]
;     {:name (str (s/capitalize (-> data :name :first)) " " (s/capitalize (-> data :name :last)))
;      :avatar-url (-> data :picture :large)
;      :user-id (db-common/unique-id)}))

; (defn- author-pool [size] (for [x (range 0 size)] (author)))

; ;; ----- Update Generation -----

; (defn- iframe-tag
;   [data-thumbnail data-type id-attribute id-value attribs]
;   (str "<p><iframe "
;             "data-thumbnail='" data-thumbnail "' "
;             "data-media-type='" data-type "' "
;             id-attribute "='" id-value "' "
;             attribs " "
;             "class='carrot-no-preview' width='560' height='315' frameborder='0' "
;             "webkitallowfullscreen='' mozallowfullscreen='' allowfullscreen=''>"
;           "</iframe></p>"))

; (defn- video-tag []
;   (let [video (rand-nth videos)
;         id (:id video)
;         provider (:provider video)
;         thumbnail (if (= provider :youtube)
;                     (str "https://img.youtube.com/vi/" id "/0.jpg")
;                     (str "https://i.vimeocdn.com/video/" id "_100x75.jpg"))
;         source (if (= provider :youtube)
;                   (str "https://www.youtube.com/embed/" id)
;                   (str "https://player.vimeo.com/video/" id))]
;     (iframe-tag thumbnail "video" "data-video-id" id (str "src='" source "' data-video-type='" (name provider) "'"))))

; (defn chart-tag []
;   (let [chart (rand-nth charts)
;         sheet-id (:sheet-id chart)
;         oid (:oid chart)
;         thumbnail (str "https://docs.google.com/spreadsheets/d/" sheet-id "/embed/oimg?id=" sheet-id "&amp;oid=" oid "&amp;disposition=ATTACHMENT&amp;bo=false&amp;zx=sohupy30u1p")
;         source (str "src='/_/sheets-proxy/spreadsheets/d/" sheet-id "/pubchart?oid=" oid "&amp;format=interactive'")]
;     (iframe-tag thumbnail "chart" "data-chart-id" sheet-id source)))

; (defn- image-tag []
;   (str "<p><img class='carrot-no-preview' data-media-type='image' src='http://lorempixel.com/640/480/"
;     (rand-nth image-types) "/' width='640' height='480'></p>"))

; (defn- body-text [size]
;   (:body (http/get (str "http://skateipsum.com/get/" (inc (int (* (rand) size))) "/0/text"))))

; (defn- headline-text [size]
;   (->> (-> "http://loripsum.net/api/plaintext/1/verylong"
;           (http/get)
;           :body
;           (s/replace #"(~|`|!|@|#|$|%|^|&|\*|\(|\)|\{|\}|\[|\]|;|:|\"|'|<|,|\.|>|\?|\/|\\|\||-|_|\+|=)" "") ; punctuation
;           (s/split #" "))
;     (drop 8) ; first 8 words don't change so we don't want them
;     (take (inc (int (* (rand) size))))
;     (s/join " ")))

; (defn- create-update [conn boards config-data author timestamp]
;   (let [board (rand-nth boards)
;         topic? (<= (rand) (:chance-of-topic config-data))
;         topic (when topic? (rand-nth (vec c/topics)))
;         headline? (<= (rand) (:chance-of-headline config-data))
;         headline (if headline? (headline-text (:max-headline-words config-data)) "")
;         body? (or (not headline?) (<= (rand) (:chance-of-body config-data)))
;         part1 (if body? (body-text (:max-paragraphs config-data)) "")
;         image? (<= (rand) (:chance-of-image config-data))
;         part2 (if image? (str part1 (image-tag)) part1)
;         chart? (<= (rand) (:chance-of-chart config-data))
;         part3 (if chart? (str part2 (chart-tag)) part2)
;         video? (<= (rand) (:chance-of-video config-data))
;         body (if video? (str part3 (video-tag)) part3)
;         entry-props {:headline headline :body body}
;         entry (if topic? (assoc entry-props :topic-name topic) entry-props)
;         with-published (assoc entry :status :published)]
;     (entry-res/create-entry! conn (entry-res/->entry conn board with-published author) timestamp)))

; (defn- generate-updates [conn org config-data authors this-date]
;   (let [update-count (int (inc (Math/floor (rand (:max-entries config-data)))))
;         boards (map :uuid (board-res/list-boards-by-org conn (:uuid org)))]
;     (println (str (f/unparse date-parser this-date) ": Generating " update-count " updates..."))
;     (dotimes [x update-count]
;       (let [hour-offset (t/plus this-date (t/hours (rem x 24)))
;             min-offset (t/plus hour-offset (t/minutes (int (* (rand) 60))))
;             timestamp (f/unparse db-common/timestamp-format min-offset)]
;         (create-update conn boards config-data (rand-nth authors) timestamp)))))

; (defn- complete? [date-range] (t/after? (first date-range) (last date-range)))

; (defun- generate 
  
;   ;; Initiate w/ a pool of authors
;   ([conn org config-data date-range]
;     (println "\nGenerating!")
;     (println "Creating" (:author-pool config-data) "authors...")
;     (let [authors (author-pool (:author-pool config-data))]
;       (generate conn org config-data authors date-range))) ; recurse

;   ;; Complete
;   ([conn org config-data authors date-range :guard complete?] (println "\nDone!"))

;   ;; Generate activity for this date
;   ([conn org config-data authors date-range]
;     (let [this-date (first date-range)]
;       (if (<= (rand) (:chance-of-entry config-data)) ; is there an update today?
;         (generate-updates conn org config-data authors this-date)
;         (println (str (f/unparse date-parser this-date) ": Skipped")))
;       (recur conn org config-data authors [(t/plus this-date (t/days 1)) (last date-range)])))) ; recurse

; ;; ----- CLI -----

; (def cli-options
;   [["-h" "--help"]])

; (defn- usage [options-summary]
;   (clojure.string/join \newline
;      ["\nThis program generates OpenCompany data in a date range, into an existing OpenCompany org, according to a configuration file, for development and testing purposes."
;       ""
;       "Usage:"
;       "  lein run -m oc.storage.util.generate -- [options] <org-slug> <config-file> <start-date> <end-date>"
;       "  lein run -m oc.storage.util.generate -- 18f ./opt/generate.edn 2016-01-01 2017-06-30"
;       ""
;       "Options:"
;       options-summary
;       ""
;       "Config file: an EDN file with a map of config options."
;       ""
;       "Please refer to ./opt/generate.edn for more information on the file format."
;       ""]))

; (def config-msg "\nNOTE: The config file must be an EDN file with a map of config options.\n")

; (defn- error-msg [errors]
;   (str "The following errors occurred while parsing your command:\n\n"
;        (clojure.string/join \newline errors)))

; (defn- exit [status msg]
;   (println msg)
;   (System/exit status))

; (defn -main [& args]
;   (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
;     ;; Handle help and error conditions
;     (cond
;       (:help options) (exit 0 (usage summary))
;       (not= (count arguments) 4) (exit 1 (usage summary))
;       errors (exit 1 (error-msg errors)))
;     ;; Get the list of files to import
;     (try
;       (let [org-slug (first arguments)
;             filename (second arguments)
;             start-date (f/parse date-parser (nth arguments 2))
;             end-date (f/parse date-parser (last arguments))
;             conn (db/init-conn c/db-options)
;             config-data (read-string (slurp filename))
;             org (org-res/get-org conn org-slug)]
;         (when-not (map? config-data) (exit 1 config-msg))
;         (when (nil? org) (exit 1 (str "Org not found for: " org-slug)))
;         (generate conn org config-data [start-date end-date])
;         (exit 0 "\n"))
;       (catch Exception e
;         (println e)
;         (exit 1 "Exception generating.")))))