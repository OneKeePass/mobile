(ns onekeepass.ios.autofill.core
  (:require [onekeepass.ios.autofill.constants :refer [DARK-THEME]]
            [onekeepass.ios.autofill.events.common :as cmn-events]
            [onekeepass.ios.autofill.rn-components :as rnc :refer [reset-colors
                                                                   use-color-scheme]]
            [onekeepass.ios.autofill.start-page :as sp]
            [reagent.core :as r]))


(defn main-content []
  (fn []
    (let [theme-name (use-color-scheme)]
      (reset-colors theme-name)
      [rnc/rnp-provider {:theme (if (= DARK-THEME theme-name) rnc/dark-theme rnc/light-theme)}
       [sp/open-page-content]])))

(defn app-root []
  [rnc/gh-gesture-handler-root-view {:style {:flex 1}}
   [:f> main-content]])

(defn ^:export -main
  []
  (cmn-events/sync-initialize)
  (r/as-element [app-root]))