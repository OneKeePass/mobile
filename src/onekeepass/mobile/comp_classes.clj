(ns onekeepass.mobile.comp-classes
  "A macro to create defs of reagent components from passed symbols
   TODO: To be removed. See okp_macros.clj
   "
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

;; Some experiments done for creating wrapper for common dialog dispatch and subscribe events 

(defn dispatches [dlg-name suffix]
  (let [f-name# (symbol (str dlg-name "-" (str suffix)))
        g-name# (keyword (str "generic-dialog" "-" (str suffix)))
        dlg-id# (keyword (str dlg-name))]
    `(defn ~f-name# []
       (re-frame.core/dispatch [~g-name# ~dlg-id#]))))

(defmacro def-dispatches [dlg-name suffixes]
  `(do
     ~@(map (fn [sx] (dispatches dlg-name sx)) suffixes)))

;; (macroexpand-1 '(def-dispatches setup-otp-action-dialog  [show close])) 
;; (do (clojure.core/defn setup-otp-action-dialog-show [] (re-frame.core/dispatch [:generic-dialog-show :setup-otp-action-dialog])) 
;;      (clojure.core/defn setup-otp-action-dialog-close [] (re-frame.core/dispatch [:generic-dialog-close :setup-otp-action-dialog])))


(defn dispatches-1 [dlg-name suffix args subscribe-event?]
  (let [f-name# (symbol (str dlg-name "-" (str suffix)))
        g-name# (keyword (str "generic-dialog" "-" (str suffix)))
        dlg-id# (keyword (str dlg-name))
        event-name# (if subscribe-event?(symbol "re-frame.core/subscribe")  (symbol "re-frame.core/dispatch"))
        ]
    (if (nil? args)
      `(defn ~f-name# []
         (~event-name# [~g-name# ~dlg-id#]))

      `(defn ~f-name# [~args]
         (~event-name# [~g-name# ~dlg-id# ~args])))))

(defmacro def-dispatches-1 [dlg-name suffixes-with-args subscribe-event?]
  `(do
     ~@(map (fn [[sx args]] (dispatches-1 dlg-name sx args subscribe-event?)) suffixes-with-args)))


;; (use '[onekeepass.mobile.comp-classes :refer [dispatches def-dispatches dispatches-1 def-dispatches-1  ]] :reload-all)

(comment
  ;; Refences:
  ;;  https://clojure.org/guides/deps_and_cli
  ;;  https://code.thheller.com/blog/shadow-cljs/2019/10/12/clojurescript-macros.html
  ;;  https://clojure-doc.org/articles/tutorials/getting_started_cli/

  ;; https://www.braveclojure.com/writing-macros/

  ;; Use clj repl in the folder 
  ;; mobile where deps.edn is located
  ;; by default, the sources under src/ are visible to the repl
  ;; clj
  ;; Clojure 1.11.1
  ;; user=> (require '[onekeepass.mobile.comp-classes :refer [declare-comp-classes ]])
  ;; Then do the following macroexpand-1

  (macroexpand-1 '(declare-comp-classes [TextInput.Icon TextInput Textinput Text] "rn1-" "rn1/"))
   ;;Will print in cljc repl
  (do
    (def rn1-text-input-icon (reagent.core/adapt-react-class rn1/TextInput.Icon))
    (def rn1-text-input (reagent.core/adapt-react-class rn1/TextInput))
    (def rn1-textinput (reagent.core/adapt-react-class rn1/Textinput))
    (def rn1-text (reagent.core/adapt-react-class rn1/Text))))