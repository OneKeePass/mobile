(ns onekeepass.mobile.crash-info
  "Shows what was recorded when the app was last ended by an unhandled javascript error"
  (:require
   [onekeepass.mobile.events.crash-info :as crash-info-events]
   [onekeepass.mobile.rn-components :as rnc :refer [on-background-color
                                                    rn-scroll-view rn-view
                                                    rnp-button rnp-dialog
                                                    rnp-dialog-actions
                                                    rnp-dialog-content
                                                    rnp-dialog-icon
                                                    rnp-dialog-title rnp-text]]))

(defn crash-info-dialog [{:keys [dialog-show text]}]
  [rnp-dialog {:style {}
               :dismissable false
               :visible (boolean dialog-show)
               :onDismiss #()}
   [rnp-dialog-icon {:icon "information" :color @rnc/outline-color}]
   [rnp-dialog-title {:style {:color @rnc/tertiary-color}} "The app closed unexpectedly"]
   [rnp-dialog-content
    [rn-view {:style {:flexDirection "column"}}
     [rnp-text {:style {:margin-bottom 10}}
      (str "This is what was recorded the last time the app closed on its own. "
           "Sending it to us will help in fixing the problem.")]
     [rn-view {:style {:min-height 100 :max-height 250}}
      [rn-scroll-view {:style {:borderWidth 0.20
                               :borderRadius 4
                               :border-color @on-background-color
                               :padding 8}}
       ;; Selectable so that the text can also be picked out by hand
       [rnp-text {:selectable true :variant "bodySmall"} text]]]]]
   [rnp-dialog-actions
    [rnp-button {:mode "text" :onPress crash-info-events/crash-info-copy} "Copy"]
    [rnp-button {:mode "text" :onPress crash-info-events/crash-info-close} "Close"]]])

(defn crash-info-dialog-mounted []
  ;; Unmount the details while locked; keep the pending report in app-db for unlock.
  (when-let [info @(crash-info-events/crash-info-data)]
    [crash-info-dialog info]))
