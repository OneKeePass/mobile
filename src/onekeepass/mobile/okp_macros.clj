(ns onekeepass.mobile.okp-macros
  "Macros to create defs of reagent components from passed symbols
   Macros to define wrapper functions for the generic dialog related dispatch and subscribe events
  "
  (:require [clojure.string]
            [clojure.pprint]
            [camel-snake-kebab.core]))


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

;; 'dialog-events-by-name' is a helper fn
;; Though we are not using any gensym based variables here
(defn- dialog-events-by-name [dlg-name suffix args subscribe-event?]
  (let [f-name (symbol (str dlg-name "-" (str suffix)))
        g-name (keyword (str "generic-dialog" "-" (str suffix)))
        dlg-id (keyword (str dlg-name))
        event-name (if subscribe-event? (symbol "re-frame.core/subscribe")  (symbol "re-frame.core/dispatch"))]
    (if (nil? args)
      `(defn ~f-name []
         (~event-name [~g-name ~dlg-id]))

      `(defn ~f-name [~args]
         (~event-name [~g-name ~dlg-id ~args])))))

#_(defn- dialog-events-by-name [dlg-name suffix args subscribe-event?]
    (let [f-name# (symbol (str dlg-name "-" (str suffix)))
          g-name# (keyword (str "generic-dialog" "-" (str suffix)))
          dlg-id# (keyword (str dlg-name))
          event-name# (if subscribe-event? (symbol "re-frame.core/subscribe")  (symbol "re-frame.core/dispatch"))]
      (if (nil? args)
        `(defn ~f-name# []
           (~event-name# [~g-name# ~dlg-id#]))

        `(defn ~f-name# [~args]
           (~event-name# [~g-name# ~dlg-id# ~args])))))

;; We can see all functions defined by this macros call in a repl by using like 
;; (clojure.repl onekeepass.mobile.events.dialogs)

(defmacro def-generic-dialog-events
  "Generates all wrapper functions for a specific dialog events 
   dlg-name is a specific dialog name
   suffixes-with-args is a vector of vectors. 
     eg [[show nil] [close nil] [show-with-state args]]
   "
  [dlg-name suffixes-with-args subscribe-event?]
  `(do
     ~@(map (fn [[sx args]] (dialog-events-by-name dlg-name sx args subscribe-event?)) suffixes-with-args)))

;; (macroexpand-1 '(as-map [a b])) => {:a a, :b b}
;; Here variables a and b are already set in the calling site
(defmacro as-map
  "Returns a map using the passed variable names"
  [variable-names]
  (reduce (fn [m s] (assoc m (keyword s) s)) {} variable-names))

(defmacro as-api-response-handler 
  "The arg ok-response-handler, error-response-handler 
   are functions names - symbols or anonymous functions
   "
  [ok-response-handler error-response-handler]
  (let [api-response (gensym)
        ok-response (gensym)]
    `(fn [~api-response]
       (when-let [~ok-response (onekeepass.mobile.events.common/on-ok ~api-response ~error-response-handler)]
         (~ok-response-handler ~ok-response)))))


(comment
  ;; Refences:
  ;;  https://clojure.org/guides/deps_and_cli
  ;;  https://code.thheller.com/blog/shadow-cljs/2019/10/12/clojurescript-macros.html
  ;;  https://clojure-doc.org/articles/tutorials/getting_started_cli/
  
  ;; https://www.braveclojure.com/writing-macros/
  
  ;; Macros are compiled by 'clj' and not by 'cljs' compiler
  ;; Use clj repl in the folder 
  ;; mobile where deps.edn is located
  ;; by default, the sources under src/ are visible to the repl
  ;; clj
  ;; Clojure 1.11.1
  ;; user=> (require '[onekeepass.mobile.comp-classes :refer [declare-comp-classes ]])
  ;; Then do the following macroexpand-1
  
  (macroexpand-1 '(declare-comp-classes [TextInput.Icon TextInput Textinput Text] "rn1-" "rn1/"))
     ;;Will print in clj repl
  (do
    (def rn1-text-input-icon (reagent.core/adapt-react-class rn1/TextInput.Icon))
    (def rn1-text-input (reagent.core/adapt-react-class rn1/TextInput))
    (def rn1-textinput (reagent.core/adapt-react-class rn1/Textinput))
    (def rn1-text (reagent.core/adapt-react-class rn1/Text)))


  ;; Need to use '(use ...) to reload changes when we are in a repl and helps to verify changes made in macros in repl
  ;; Using load-file and require did not reload the changes
  (use '[onekeepass.mobile.okp-macros :refer [def-generic-dialog-events]] :reload-all)

  (macroexpand '(def-generic-dialog-events setup-otp-action-dialog  [[show nil] [close nil] [show-with-state state-m]] false))
  (do (clojure.core/defn setup-otp-action-dialog-show []
        (re-frame.core/dispatch [:generic-dialog-show :setup-otp-action-dialog]))
      (clojure.core/defn setup-otp-action-dialog-close []
        (re-frame.core/dispatch [:generic-dialog-close :setup-otp-action-dialog]))
      (clojure.core/defn setup-otp-action-dialog-show-with-state [state-m]
        (re-frame.core/dispatch [:generic-dialog-show-with-state :setup-otp-action-dialog state-m])))
  
  
  (macroexpand-1 '(as-api-response-handler my-ok-fn  error-fn)) 
  (clojure.core/fn [G__4938] 
    (clojure.core/when-let [G__4939 (onekeepass.mobile.events.common/on-ok G__4938 error-fn)] (my-ok-fn G__4939)))
  )