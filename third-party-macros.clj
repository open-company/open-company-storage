(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :if-inside-macroexpansion-of #{'defun.core/defun}
  :within-depth 12
  :reason "defun creates calls to and with only 1 argument."})