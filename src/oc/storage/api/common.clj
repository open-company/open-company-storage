(ns oc.storage.api.common
  "Common functions for storage API resources."
  (:require [if-let.core :refer (if-let*)]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]))
 
;; ----- Authorization Functions -----

(defn access-level-for
  "
  Given an org slug and a user map, return the authorization level for the user on the org:
    :author
    :viewer
    :public
    false

  Or, given an org slug, board slug and user map, return the authorization level for the user on the board:
    :author
    :viewer
    false
  "
  ([conn org-slug {user-id :user-id teams :teams}]
  (if-let [org (org-res/get-org conn org-slug)]
    (let [org-authors (set (:authors org))]
      (cond
        
        ;; a named author of this org
        (org-authors user-id) {:access-level :author}
        
        ;; a team member of this org
        ((set teams) (:team-id org)) {:access-level :viewer}
        
        ;; TODO public access to orgs w/ a public board

        ;; no access
        :else false))

    ;; Will fail existence checks later
    true))


  ([conn org-slug board-slug {user-id :user-id teams :teams}]
  (if-let* [org (org-res/get-org conn org-slug)
            board (board-res/get-board conn (:uuid org) board-slug)]
    (let [org-uuid (:org-uuid org)
          org-authors (set (:authors org))
          board-access (keyword (:access board))
          board-authors (set (:authors board))
          board-viewers (set (:viewers board))]
      (cond
        
        ;; a named author of this board
        (board-authors user-id) {:access-level :author}
        
        ;; a team author of this non-private board
        (and (not= board-access :private) (org-authors user-id)) {:access-level :author}
        
        ;; a named viewer of this board
        (board-viewers user-id) {:access-level :viewer}
        
        ;; a team member on a non-private board
        (and (not= board-access :private) ((set teams) (:team-id org))) {:access-level :viewer}
        
        ;; anyone else on a public board
        (= board-access :public) {:access-level :viewer}
        
        ;; no access
        :else false))
    
    ;; Will fail existence checks later
    true)))

(defn allow-team-admins
  ""
  [user]
  ;; TODO
  true)

(defn allow-team-admins-or-no-org
  ""
  [conn user]
  ;; TODO
  {:access-level :author})

(defn allow-members
  "
  Given an org slug and a user map, return an access level of :author or :viewer if the user is a team member
  and false otherwise.
  "
  [conn org-slug user]
  (let [access (access-level-for conn org-slug user)]
    (if (= (:acess-level access) :public)
      false
      access)))

(defn allow-authors
  "
  Given an org slug, and user map, return true if the user is an author on the org.

  Or, given an org slug, board slug and user map, return true if the user is an author on the board.
  "
  ([conn org-slug user]
  (= (:access-level (access-level-for conn org-slug user)) :author))

  ([conn org-slug board-slug user]
  (= (:access-level (access-level-for conn org-slug board-slug user)) :author)))