(ns onekeepass.mobile.android.autofill.core
  "The Android Autofill specific entry point. Here we load all autofill specific modules
   This name is loaded in 'onekeepass.mobile.core' which in turn loads all modules listed here
  "
  (:require [onekeepass.mobile.android.autofill.appbar :refer [appbar-main-content
                                                               hardware-back-pressed]]
            [onekeepass.mobile.android.autofill.events.common :as android-af-cmn-events]
            [onekeepass.mobile.background :as bg]
            [onekeepass.mobile.common-components :refer [message-dialog
                                                         message-snackbar]]
            [onekeepass.mobile.constants :refer [DARK-THEME]]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.rn-components :as rnc :refer [react-use-effect
                                                             reset-colors
                                                             rn-view
                                                             rnp-portal
                                                             use-color-scheme]]))

(defn main-content-tr []
  (fn []
    (if-not @(cmn-events/language-translation-loading-completed)
      [rn-view [rnc/rnp-text "Please wait..."]]
      [:<>
       [appbar-main-content]
       [rnp-portal
        [message-snackbar]
        [message-dialog @(cmn-events/message-dialog-data)]]])))

;; System back action handler (Android)
(def ^:private back-handler (atom nil))

(defn main-content []
  (fn []
    (let [theme-name (use-color-scheme)]
      (reset-colors theme-name)

      (when (bg/is-Android)
        (react-use-effect
         (fn []
           ;; event to load all available keyfiles to use as options in a select modal dialog
           (android-af-cmn-events/sync-initialize)
           
           (reset! back-handler (.addEventListener rnc/rn-back-handler "hardwareBackPress" hardware-back-pressed))
           ;;(println "Android af back-handler is registered ")
           ;; Returns a fn
           (fn []
             (when-not (nil? @back-handler)
               (.remove ^js/BackHanler @back-handler)
               (reset! back-handler nil))))
         ;; Empty parameter array to useEffect fn
         (clj->js [])))

      [rnc/rnp-provider {:theme (if (= DARK-THEME theme-name) rnc/dark-theme rnc/light-theme)}
       [main-content-tr]])))

;; This is the entry point (from onekeepass/mobile/core.cljs) for Android AutoFill
;; Android AutoFill shares the same '@re-frame.db/app-db' 
;; and also some events (loading app preference ? - See fn 'init-calls') from the main app
(defn app-root []
  [rnc/gh-gesture-handler-root-view {:style {:flex 1}}
   [:f> main-content]])