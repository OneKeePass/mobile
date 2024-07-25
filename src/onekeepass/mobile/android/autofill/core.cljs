(ns onekeepass.mobile.android.autofill.core
  (:require [onekeepass.mobile.android.autofill.appbar :refer [appbar-main-content
                                                               hardware-back-pressed]]
            [onekeepass.mobile.background :as bg]
            [onekeepass.mobile.common-components :refer [message-dialog
                                                         message-snackbar]]
            [onekeepass.mobile.constants :refer [DARK-THEME]]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.rn-components :as rnc :refer [react-use-effect
                                                             reset-colors
                                                             rn-view
                                                             rnp-portal
                                                             rnp-provider
                                                             rnp-text
                                                             use-color-scheme]]))




;; (defn content []
;;   (fn []
;;     (println "Content is called ....")
;;     [rn-view {}
;;      [open-page-content]
;;      #_[rnp-text "Android autofill comes here...2"]]))

;; (defn main-content
;;   "A functional component so that we can call the react hook set-translator and use any react useEffects"
;;   []
;;   (fn []
;;     (let [;; Need to use this React hook as it provides and subscribes to color scheme updates in Device settings
;;           ;; This ensures that the app detects the system default theme change and updates accordingly
;;           ;; Without this hook call, the app theme will not change when the system default theme is changed
;;           ;; even when 'app-theme' from our app settings is 'system'
;;           theme-name1 (use-color-scheme)
;;           theme-name (rnc/theme-to-use @(app-theme))]

;;       (println "theme-name1 is " theme-name1)

;;       ;; displays the current time, Wi-Fi and cellular network information, battery level
;;       ;; In case we want use white color for these, we need to do the following onetime
;;       ;; See https://stackoverflow.com/questions/39297291/how-to-set-ios-status-bar-background-color-in-react-native
;;       ;; https://reactnative.dev/docs/statusbar#statusbarstyle
;;       ;; This is mainly for iOS
;;       (.setBarStyle rn/StatusBar "light-content" true)

;;       ;; Resets r/atoms that hold some standard colors used in many UI pages
;;       (reset-colors theme-name)

;;       ;; useEffect is called after the component is rendered
;;       ;; it runs both after the first render and after every update 
;;       #_(set-translator)

;;       [rnp-provider {:theme (if (= DARK-THEME theme-name) rnc/dark-theme rnc/light-theme)}
;;        [:f> content]])))

;; (defn app-root []
;;   [rnc/gh-gesture-handler-root-view {:style {:flex 1}}
;;    [:f> main-content]])



(defn main-content-tr []
  (fn []
    (if-not @(cmn-events/language-translation-loading-completed)
      [rnc/rn-view [rnc/rnp-text "Please wait..."]]

      [:<>
       [appbar-main-content]
       [rnp-portal
        [message-snackbar]
        [message-dialog @(cmn-events/message-dialog-data)] 
        ]]

      #_[sp/open-page-content])))


;; System back action handler (Android)
(def ^:private back-handler (atom nil))

(defn main-content []
  (fn []
    (let [theme-name (use-color-scheme)]
      (reset-colors theme-name)
      
      #_(when (bg/is-Android)
        (react-use-effect
         (fn []
           (reset! back-handler (.addEventListener rnc/rn-back-handler "hardwareBackPress" hardware-back-pressed))
           (println "Android af back-handler is registered ")
           ;; Returns a fn
           (fn []
             (when-not (nil? @back-handler)
               (.remove ^js/BackHanler @back-handler)
               (reset! back-handler nil))))
               ;; Empty parameter array to useEffect fn
         (clj->js [])))
      
      [rnc/rnp-provider {:theme (if (= DARK-THEME theme-name) rnc/dark-theme rnc/light-theme)}
       [main-content-tr]
       #_[sp/open-page-content]])))

(defn app-root []
  [rnc/gh-gesture-handler-root-view {:style {:flex 1}}
   [:f> main-content]])