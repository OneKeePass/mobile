(ns onekeepass.mobile.core
  (:require
   ;; When we build iOS production main bundle, we can comment out this ns
   ;; and this will ensure that all android autofill related code are excluded

   [onekeepass.mobile.android.autofill.core :as android-core]

   ;;;;;;; ;;;;;;; ;;;;;;; ;;;;;;;
   [onekeepass.mobile.appbar :refer [appbar-main-content
                                     hardware-back-pressed]]
   [onekeepass.mobile.background :as bg]
   [onekeepass.mobile.common-components :as cc :refer [message-dialog
                                                       message-modal
                                                       message-snackbar]]
   [onekeepass.mobile.constants :refer [DARK-THEME]]
   [onekeepass.mobile.events.app-settings :as as-events :refer [app-theme]]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.native-events :as native-events]
   [onekeepass.mobile.events.remote-storage :as rs-events]
   [onekeepass.mobile.events.save :as save-events]
   [onekeepass.mobile.rn-components :as rnc :refer [react-use-effect
                                                    reset-colors
                                                    rnp-portal
                                                    rnp-provider
                                                    use-color-scheme]]
   [onekeepass.mobile.save-error-dialog :refer [save-error-modal]]
   [onekeepass.mobile.start-page :refer [open-db-dialog]]
   [onekeepass.mobile.translation :as t]
   [react-native :as rn]
   [reagent.core :as r]))
;;(set! *warn-on-infer* true)

(defn main-content
  "All reagent atoms are referenced in this component so that the react hook set-translator is called onetime in main"
  []
  (if-not @(cmn-events/language-translation-loading-completed)
    [rnc/rn-view [rnc/rnp-text "Please wait."]]
    [:<>
     [appbar-main-content]
     [rnp-portal
      [message-snackbar]
      [open-db-dialog]
      [save-error-modal @(save-events/save-error-modal-data)]
      [message-modal @(cmn-events/message-modal-data)]
      [message-dialog @(cmn-events/message-dialog-data)]]]))

;; System back action handler (Android)
(def ^:private back-handler (atom nil))

(defn main
  "A functional component so that we can call the react hook set-translator and use any react useEffects"
  []
  (fn []
    (let [;; Need to use this React hook as it provides and subscribes to color scheme updates in Device settings
          ;; This ensures that the app detects the system default theme change and updates accordingly
          ;; Without this hook call, the app theme will not change when the system default theme is changed
          ;; even when 'app-theme' from our app settings is 'system'
          _theme-name1 (use-color-scheme)
          theme-name (rnc/theme-to-use @(app-theme))]

      ;;(println "theme-name1 is " theme-name1)

      ;; displays the current time, Wi-Fi and cellular network information, battery level
      ;; In case we want use white color for these, we need to do the following onetime
      ;; See https://stackoverflow.com/questions/39297291/how-to-set-ios-status-bar-background-color-in-react-native
      ;; https://reactnative.dev/docs/statusbar#statusbarstyle
      ;; This is mainly for iOS
      (if (bg/is-iOS)
        (.setBarStyle rn/StatusBar (if (= DARK-THEME theme-name) "dark-content" "light-content") true)
        (.setBarStyle rn/StatusBar "light-content" true))

      ;; Resets r/atoms that hold some standard colors used in many UI pages
      (reset-colors theme-name)

      ;; useEffect is called after the component is rendered
      ;; it runs both after the first render and after every update
      (react-use-effect
       (fn []
         (bg/hide-splash #())
          ;; cleanup fn is returned which is called when this component unmounts
         (fn []))

      ;; Need to pass the list of all reactive values referenced inside of the setup code or empty list
       (clj->js []))

      (when (bg/is-Android)
        (react-use-effect
         (fn []
           (reset! back-handler (.addEventListener rnc/rn-back-handler "hardwareBackPress" hardware-back-pressed))
           ;; Returns a fn
           (fn []
             (when-not (nil? @back-handler)
               (.remove ^js/BackHanler @back-handler)
               (reset! back-handler nil))))
         ;; Empty parameter array to useEffect fn
         (clj->js [])))

      [rnp-provider {:theme (if (= DARK-THEME theme-name) rnc/dark-theme rnc/light-theme)}
       [main-content]])))

(defn app-root
  []
  ;; Need to wrap the entry point with <GestureHandlerRootView> or gestureHandlerRootHOC
  ;; See https://docs.swmansion.com/react-native-gesture-handler/docs/installation
  [rnc/gh-gesture-handler-root-view {:style {:flex 1}}
   [:f> main]])


(defn init-calls []
  (native-events/register-backend-event-handlers)
  (cmn-events/sync-initialize)
  (as-events/init-session-timeout-tick)
  (t/load-language-translation)
  (rs-events/load-all-remote-connection-configs))

;; Make sure that either iOS or Android '-main' fn is available 

;; Entry root for iOS
#_(defn ^:export -main
    [_args]

  ;; (native-events/register-backend-event-handlers)
  ;; (cmn-events/sync-initialize)
  ;; (as-events/init-session-timeout-tick)
  ;; (t/load-language-translation)
    (init-calls)
    (r/as-element [app-root]))

;; Entry root for Android main and Android Autofill
;; Ensure we load the ns [onekeepass.mobile.android.autofill.core :as android-core] 

;; This '-main' fn will work with iOS app also. Only things the main bundle size will be more 
;; than required and all android-af events are registered needlessly

(defn ^:export -main
  [args]
  (let [{:keys [androidAutofill] :as options} (js->clj args :keywordize-keys true)]
    (println "The options from main args are ." options)

    ;; TODO: Add check so as to load the following only if there are not yet loaded
    ;; For now, these calls are made when main app opened and also when android autofill is called 
    ;; without checking whether the initializations are done or not

    ;; (native-events/register-backend-event-handlers)
    ;; (cmn-events/sync-initialize)
    ;; (as-events/init-session-timeout-tick)
    ;; (t/load-language-translation)

    (init-calls)

    (if androidAutofill
      (r/as-element [android-core/app-root])
      (r/as-element [app-root]))))

(comment
  (in-ns 'onekeepass.mobile.core))