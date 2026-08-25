(ns onekeepass.mobile.otp-url-received
  "Dialogs shown for an 'otpauth://' url that another app sends to us - see
   onekeepass.mobile.events.otp-url-received"
  (:require [clojure.string :as str]
            [onekeepass.mobile.events.otp-url-received :as otp-url-events]
            [onekeepass.mobile.rn-components :refer [cust-dialog
                                                     rn-view
                                                     rnp-button
                                                     rnp-dialog-actions
                                                     rnp-dialog-content
                                                     rnp-dialog-title
                                                     rnp-text]]
            [onekeepass.mobile.translation :refer [lstr-bl
                                                   lstr-dlg-text
                                                   lstr-dlg-title]]))

(defn- code-description
  "What the user recognises in the received url - the service it belongs to and the account.
   Any app on the device can send us such an url and this is what the confirmation is about"
  [{:keys [issuer account-name]}]
  (->> [issuer account-name] (remove str/blank?) (str/join " - ")))

;; Asks where the received url is to go. Nothing is written to the database till the user
;; answers this
(defn- otp-url-destination-dialog [{:keys [dialog-show data]}]
  [cust-dialog {:style {} :dismissable false :visible (boolean dialog-show) :onDismiss #()}
   [rnp-dialog-title (lstr-dlg-title "otpUrlReceived")]
   [rnp-dialog-content
    [rnp-text (lstr-dlg-text "otpUrlReceived")]
    [rn-view {:style {:margin-top 10}}
     [rnp-text {:variant "titleMedium"} (code-description data)]]]
   [rnp-dialog-actions {:style {:justify-content "center"}}
    [rn-view {:style {:width "100%"}}
     [rnp-button {:mode "text" :on-press otp-url-events/create-new-entry}
      (lstr-bl "createNewEntry")]
     [rnp-button {:mode "text" :on-press otp-url-events/pick-existing-entry}
      (lstr-bl "addToExistingEntry")]
     [rnp-button {:mode "text" :on-press otp-url-events/cancel}
      (lstr-bl "cancel")]]]])

;; The picked entry has an otp code already and replacing it makes the earlier one unusable
(defn- otp-url-overwrite-confirm-dialog [{:keys [dialog-show]}]
  [cust-dialog {:style {} :dismissable false :visible (boolean dialog-show) :onDismiss #()}
   [rnp-dialog-title (lstr-dlg-title "otpUrlOverwrite")]
   [rnp-dialog-content
    [rnp-text (lstr-dlg-text "otpUrlOverwrite")]]
   [rnp-dialog-actions {:style {:justify-content "center"}}
    [rnp-button {:mode "text" :on-press otp-url-events/overwrite-confirmed}
     (lstr-bl "overwrite")]
    [rnp-button {:mode "text" :on-press otp-url-events/cancel}
     (lstr-bl "cancel")]]])

(defn otp-url-dialogs-mounted []
  [:<>
   [otp-url-destination-dialog @(otp-url-events/destination-dialog-data)]
   [otp-url-overwrite-confirm-dialog @(otp-url-events/overwrite-confirm-data)]])
