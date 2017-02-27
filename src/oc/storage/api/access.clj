(ns oc.storage.api.access
  "Access control functions for storage API."
  (:require [if-let.core :refer (if-let*)]
            [taoensso.timbre :as timbre]
            [oc.lib.schema :as lib-schema]
            [oc.storage.resources.org :as org-res]
            [oc.storage.resources.board :as board-res]))

;; ----- Validation -----
 
 (defn malformed-user-id?
  "Read in the body param from the request and make sure it's a non-blank string
  that corresponds to a user-id. Otherwise just indicate it's malformed."
  [ctx]
  (try
    (if-let* [user-id (slurp (get-in ctx [:request :body]))
              valid? (lib-schema/unique-id? user-id)]
      [false {:data user-id}]
      true)
    (catch Exception e
      (do (timbre/warn "Request body not processable as a user-id: " e)
        true))))

;; ----- Authorization -----

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
  ([conn org-slug {user-id :user-id teams :teams admin :admin}]
  (if-let [org (org-res/get-org conn org-slug)]
    (let [org-authors (set (:authors org))]
      (cond
        
        ;; a named author of this org
        (org-authors user-id) {:access-level :author}
        
        ;; an admin of this org's team
        ((set admin) (:team-id org)) {:access-level :author}

        ;; a team member of this org
        ((set teams) (:team-id org)) {:access-level :viewer}
        
        ;; TODO public access to orgs w/ a public board

        ;; no access
        :else false))

    ;; Will fail existence checks later
    {:access-level :does-not-exist}))


  ([conn org-slug board-slug {user-id :user-id teams :teams admin :admin}]
  (if-let* [org (org-res/get-org conn org-slug)
            board (board-res/get-board conn (:uuid org) board-slug)]
    (let [org-uuid (:org-uuid org)
          org-authors (set (:authors org))
          board-access (keyword (:access board))
          board-authors (set (:authors board))
          board-viewers (set (:viewers board))]
      (cond
        
        ;; a named author of this private board
        (and (= board-access :private) (board-authors user-id)) {:access-level :author}
        
        ;; an org author of this non-private board
        (and (not= board-access :private) (org-authors user-id)) {:access-level :author}

        ;; an admin of this org's team
        ((set admin) (:team-id org)) {:access-level :author}
        
        ;; a named viewer of this board
        (and (= board-access :private) (board-viewers user-id)) {:access-level :viewer}
        
        ;; a team member on a non-private board
        (and (not= board-access :private) ((set teams) (:team-id org))) {:access-level :viewer}
        
        ;; anyone else on a public board
        (= board-access :public) {:access-level :viewer}
        
        ;; no access
        :else false))
    
    ;; Will fail existence checks later
    {:access-level :does-not-exist})))

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
    (if (= (:access-level access) :public)
      false
      access)))

(defn allow-authors
  "
  Given an org slug, and user map, return true if the user is an author on the org.

  Or, given an org slug, board slug and user map, return true if the user is an author on the board.
  "
  ([conn org-slug user]
  (let [access (access-level-for conn org-slug user)
        access-level (:access-level access)]
    (if (or (= access-level :author)
            (= access-level :does-not-exist))
      access
      false)))

  ([conn org-slug board-slug user]
  (let [access (access-level-for conn org-slug board-slug user)
        access-level (:access-level access)]
    (if (or (= access-level :author)
            (= access-level :does-not-exist))
      access
      false))))