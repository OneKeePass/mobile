(ns onekeepass.ios.autofill.core
  (:require [onekeepass.ios.autofill.constants :refer [DARK-THEME]]
            [onekeepass.ios.autofill.events.common :as cmn-events]
            [onekeepass.ios.autofill.events.native-events :as native-events]
            [onekeepass.ios.autofill.rn-components :as rnc :refer [reset-colors
                                                                   use-color-scheme]]
            [onekeepass.ios.autofill.start-page :as sp]
            [onekeepass.ios.autofill.translation :as t]
            [reagent.core :as r]))

(defn main-content-tr []
  (fn []
    (if-not @(cmn-events/language-translation-loading-completed)
      [rnc/rn-view [rnc/rnp-text "Please wait..."]]
      [sp/open-page-content])))

(defn main-content []
  (fn []
    (let [theme-name (use-color-scheme)]
      (reset-colors theme-name)
      [rnc/rnp-provider {:theme (if (= DARK-THEME theme-name) rnc/dark-theme rnc/light-theme)}
       [main-content-tr]
       #_[sp/open-page-content]])))

(defn app-root []
  [rnc/gh-gesture-handler-root-view {:style {:flex 1}}
   [:f> main-content]])

(defn ^:export -main
  []
  ;; Need to register the handlers for the events that
  ;; are emitted from native module side
  (native-events/register-entry-otp-update-handler)
  
  (cmn-events/sync-initialize)
  ;; Loads transalations data for the locale language - async call
  (t/load-language-translation)
  
  (r/as-element [app-root]))