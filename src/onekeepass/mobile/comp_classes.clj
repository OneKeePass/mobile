(ns onekeepass.mobile.comp-classes
  "A macro to create defs of reagent components from passed symbols"
  (:require [clojure.string]
            [clojure.pprint]
            [camel-snake-kebab.core])) ;;:as csk

#_{:clj-kondo/ignore [:unused-binding]}
(defmacro def-reagent-classes
  "See below"
  [args def-prefix ns-prefix]
  ;; If the args vector contains a symbol with '.', then that '.' is removed before forming symbol
  (list 'map '(fn [x] (list 'def
                            (symbol (str def-prefix (camel-snake-kebab.core/->kebab-case (clojure.string/replace x "." ""))))
                            (list 'reagent.core/adapt-react-class (symbol (str ns-prefix x))))) args))

(defmacro declare-comp-classes
  "Defines 'def's for all symbols passed in the args vector
   in the namespace where this macro is called
   e.g (def mui-icon-button (reagent.core/adapt-react-class mui/IconButton))
   args is a vector of material ui component names
   def-prefix is the prefix to use for the var name
   ns-prefix is the namespace to prefix to the 'imported' material ui component
   "
  [args def-prefix ns-prefix]
  `(do
     ~@(def-reagent-classes args def-prefix ns-prefix)))


(comment
  (macroexpand-1 '(declare-comp-classes [TextInput.Icon TextInput Textinput Text] "rn1-" "rn1/"))
   ;;Will print in cljc repl
  (do
    (def rn1-text-input-icon (reagent.core/adapt-react-class rn1/TextInput.Icon))
    (def rn1-text-input (reagent.core/adapt-react-class rn1/TextInput))
    (def rn1-textinput (reagent.core/adapt-react-class rn1/Textinput))
    (def rn1-text (reagent.core/adapt-react-class rn1/Text))))