(disable-warning
 {:linter :suspicious-expression
  :for-macro 'clojure.core/and
  :if-inside-macroexpansion-of #{'defun.core/defun}
  :within-depth 20
  :reason "defun creates calls to and with only 1 argument."})