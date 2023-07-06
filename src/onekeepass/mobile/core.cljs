(ns onekeepass.mobile.core
  (:require [reagent.core :as r]
            [react-native :as rn]
            [onekeepass.mobile.rn-components :as rnc :refer [set-translator
                                                             rnp-provider
                                                             rnp-portal]] 
            [onekeepass.mobile.appbar :refer [appbar-main-content]]
            [onekeepass.mobile.events.native-events :as native-events]
            [onekeepass.mobile.events.save :as save-events]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.background :as bg]
            [onekeepass.mobile.save-error-dialog :refer [save-error-modal]]
            [onekeepass.mobile.start-page :refer [open-db-dialog]]
            [onekeepass.mobile.common-components :as cc :refer [message-snackbar
                                                                message-modal
                                                                message-dialog]]))
(set! *warn-on-infer* true)

(defn main-content
  "All reagent atoms are referenced in this component so that the react hook set-translator is called onetime in main"
  []
  [:<>
   [appbar-main-content]
   [rnp-portal
    [message-snackbar]
    [open-db-dialog]
    [save-error-modal @(save-events/save-error-modal-data)]
    [message-modal @(cmn-events/message-modal-data)]
    [message-dialog @(cmn-events/message-dialog-data)]]])

(defn main
  "A functional component so that we can call the react hook set-translator"
  []
  (fn []
    ;; useEffect is called after the component is rendered
    ;; it runs both after the first render and after every update
    (rnc/react-use-effect
     (fn []
       (bg/hide-splash #())
       ;; cleanup fn is returned which is called when this component unmounts
       (fn []))
     ;; Need to pass the list of all reactive values referenced inside of the setup code or empty list
     (clj->js []))

    (set-translator)
    [rnp-provider {:theme rnc/custom-theme}
     [main-content]]))

(defn app-root
  []
  ;; displays the current time, Wi-Fi and cellular network information, battery level
  ;; In case we want use white color for these, we need to do the following onetime
  ;; See https://stackoverflow.com/questions/39297291/how-to-set-ios-status-bar-background-color-in-react-native
  ;; https://reactnative.dev/docs/statusbar#statusbarstyle
  ;; This is mainly for iOS
  (.setBarStyle rn/StatusBar "light-content" true)  ;; default or dark-content

  ;; Need to wrap the entry point with <GestureHandlerRootView> or gestureHandlerRootHOC
  ;; See https://docs.swmansion.com/react-native-gesture-handler/docs/installation

  [rnc/gh-gesture-handler-root-view {:style {:flex 1}}
   [:f> main]])

(defn ^:export -main
  []
  (native-events/register-open-url-handler)
  (cmn-events/sync-initialize)
  (r/as-element [app-root]))

(comment
  (in-ns 'onekeepass.mobile.core))