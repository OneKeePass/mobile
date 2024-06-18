(ns onekeepass.ios.autofill.core
  (:require [onekeepass.ios.autofill.constants :refer [DARK-THEME]]
            [onekeepass.ios.autofill.events.common :as cmn-events]
            [onekeepass.ios.autofill.rn-components :as rnc :refer [reset-colors
                                                                   use-color-scheme]]
            [onekeepass.ios.autofill.start-page :as sp]
            [reagent.core :as r]))


;; (def view (r/adapt-react-class rn/View))
;; (def text (r/adapt-react-class rn/Text))
;; (def button (r/adapt-react-class rn/Button))

;; (defn app-root
;;   []
;;   [view {:style {:flex 1 :align-items "center" :justify-content "center"}}

;;    [text "Hello Jey..RN 0.72.1 works"]

;;    [button {:title "Press Now"}]

;;    ;; Initially will show the value in this r/atom and can be reset another string in REPL
;;    #_[text @some-repl-text]])

;; (defn ^:export -main
;;   []
;;   (r/as-element [app-root]))

(defn main-content []
  (fn []
    (let [theme-name (use-color-scheme)]
      (reset-colors theme-name)
      [rnc/rnp-provider {:theme (if (= DARK-THEME theme-name) rnc/dark-theme rnc/light-theme)}
       [sp/open-page-content]])))

(defn app-root []
  [rnc/gh-gesture-handler-root-view {:style {:flex 1}}
   [:f> main-content]])

#_(defn app-root
  []
  [rn-view {:style {:flex 1 :align-items "center" :justify-content "center"}}

   [rn-text "Hello Jey..RN 0.72.1 works"]

   [rn-button {:title "Press Now..."}]

   ;; Initially will show the value in this r/atom and can be reset another string in REPL
   #_[text @some-repl-text]])

(defn ^:export -main
  []
  (cmn-events/sync-initialize)
  (r/as-element [app-root])
  #_(r/as-element [main-content]))