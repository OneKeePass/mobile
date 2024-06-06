(ns onekeepass.ios.autofill.core
  (:require [reagent.core :as r]
            [react-native :as rn]))


(def view (r/adapt-react-class rn/View))
(def text (r/adapt-react-class rn/Text))
(def button (r/adapt-react-class rn/Button))

(defn app-root
  []
  [view {:style {:flex 1 :align-items "center" :justify-content "center"}}
   
   [text "Hello Jey..RN 0.72.1 works"]

   [button {:title "Press Now"}]

   ;; Initially will show the value in this r/atom and can be reset another string in REPL
   #_[text @some-repl-text]])

(defn ^:export -main
  []
  (r/as-element [app-root]))