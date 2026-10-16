(ns onekeepass.mobile.app-lock
  (:require
   [onekeepass.mobile.rn-components :as rnc :refer [page-background-color
                                                    rn-scroll-view
                                                    rnp-text]]
   [onekeepass.mobile.utils :refer [str->int]]
   [onekeepass.mobile.events.app-lock :as app-lock-events]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.pin-pad :refer [pin-pad]]
   [onekeepass.mobile.translation :refer [lstr-mt lstr-dlg-title]]))

(defn content []
  (let [attempts-left @(app-lock-events/attempts-remaining)
        ;; Set only for a fixed length PIN (iOS). nil lets the PIN be of any length
        {:keys [pin-length]} @(cmn-events/app-lock-preference)]
    ;; Scrolls when the pad does not fit, e.g a phone in landscape
    [rn-scroll-view {:style {:flex 1 :backgroundColor @page-background-color}
                     :contentContainerStyle {:flexGrow 1 :justifyContent "center"}}
     [rnp-text {:style {:text-align "center" :color @rnc/primary-color :margin-top 16}
                :variant "headlineLarge"} "OneKeePass"]
     [pin-pad {:title (lstr-dlg-title 'enterPin)
               :pin-length pin-length
               ;; The number of attempts left is not shown. Only the last one is called out as
               ;; one more wrong PIN resets the app
               :message (when (= attempts-left 1) (lstr-mt 'enterPin 'lastAttemptWarning))
               :message-error? true
               :on-submit (fn [pin rejected-fn]
                            (app-lock-events/verify-app-lock-pin (str->int pin) rejected-fn))}]]))
