(ns oc.storage.resources.reaction)

(defn- reaction-collapse
  "Reducer function that collapses reactions into count and sequence of author based on common reaction unicode."
  [reactions reaction]
  (let [unicode (:reaction reaction)
        author (-> reaction :author :name)
        author-id (-> reaction :author :user-id)]
    (if-let [existing-reaction (reactions unicode)]
      ;; have this unicode already, so add this reaction to it
      (assoc reactions unicode (-> existing-reaction
                                  (update :count inc)
                                  (update :authors #(conj % author))
                                  (update :author-ids #(conj % author-id))))
      ;; haven't seen this reaction unicode before, so init a new one
      (assoc reactions unicode {:reaction unicode :count 1 :authors [author] :author-ids [author-id]}))))

(defn aggregate-reactions
  "
  Given a sequence of individual reaction interactions, collapse it down to a set of distinct reactions
  that include the count of how many times reacted and the list of author names and IDs that reacted.
  "
  [entry-reactions]
  (or (vals (reduce reaction-collapse {} (map #(assoc % :count 1) entry-reactions))) []))