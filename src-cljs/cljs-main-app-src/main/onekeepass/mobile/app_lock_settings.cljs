(ns onekeepass.mobile.app-lock-settings
  (:require
   [onekeepass.mobile.background :refer [is-iOS]]
   [onekeepass.mobile.rn-components :as rnc :refer [modal-selector-colors
                                                    page-background-color
                                                    rn-scroll-view
                                                    rn-view
                                                    rnp-divider
                                                    rnp-segmented-buttons
                                                    rnp-switch
                                                    rnp-text]]
   [onekeepass.mobile.common-components :as cc :refer [select-field find-matching-label get-form-style ]]
   [onekeepass.mobile.utils :as u :refer [str->int]]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.app-lock :as app-lock-events]
   [onekeepass.mobile.events.app-lock-settings :as al-settings-events]
   [onekeepass.mobile.pin-pad :refer [pin-pad]]
   [onekeepass.mobile.translation :refer [lstr-l lstr-dlg-title lstr-mt]]
   [reagent.core :as r]))

;; New PINs on Android need at least these many digits. PINs set earlier with any length still work
(def ^:private NEW-PIN-MIN-LENGTH 4)

;; New PINs on iOS have a fixed length as the iOS passcode does. The user may switch to 4
(def ^:private IOS-DEFAULT-PIN-LENGTH 6)

(def lock-timeout-options [{:key 0 :label  "Immediately"}
                           {:key 15000 :label "15 seconds"}
                           {:key 20000 :label "20 seconds"}
                           {:key 30000  :label  "30 seconds"}
                           {:key 60000  :label  "60 seconds"}
                           {:key 120000 :label  "2 minutes"}
                           {:key 300000 :label  "5 minutes"}
                           {:key 600000 :label  "10 minutes"}
                           {:key 900000 :label  "15 minutes"}
                           {:key 1800000 :label  "30 minutes"}
                           {:key 3600000 :label  "1 hour"}])

(def attemps-allowed-options [{:key 1 :label  "1"}
                              {:key 3 :label  "3"}
                              {:key 5 :label  "5"}
                              {:key 10 :label  "10"}
                              {:key 15  :label  "15"}])

#_(defn find-matching-label
  "Gets the option label from the selected value"
  [options value]
  (:label (first
           (filter
            (fn [m]
              (= value (:key m)))
            options))))


(defn page-content 
  "App lock settings component"
  [{:keys [pin-lock-enabled lock-timeout attempts-allowed]}]
  [rn-view {:style {:margin-top 10}}
   [rn-view  {:style (get-form-style)}
    [rn-view {:style {:flexDirection "row" :min-height 50 :justify-content "space-between"}}
     (if pin-lock-enabled
       [rnp-text {:style {:align-self "center" :padding-left 5} :variant "titleMedium"} (lstr-l 'pinLockEnabled)]
       [rnp-text {:style {:align-self "center" :padding-left 5} :variant "titleMedium"} (lstr-l 'pinLockDisabled)])
     [rn-view {:style {:padding-right 10 :align-self "center"}}
      [rnp-switch {:style {:align-self "center"}
                   :value pin-lock-enabled
                   :onValueChange (fn [new-val]
                                    (if new-val
                                      (al-settings-events/pin-setup-start)
                                      (al-settings-events/pin-removed)))}]]]

    (when pin-lock-enabled
      [rn-view {:style {}}
       [rnp-text "The app is protected with a PIN authentication. You need to enter the PIN to use OneKeePass"]])]

   (when pin-lock-enabled
     [rn-view  {:style (get-form-style)}
      [rn-view {:style {}}
       [select-field {:text-label "App lock delay"
                      :options lock-timeout-options
                      :value (find-matching-label lock-timeout-options lock-timeout)
                                                              ;;:label-extractor-fn cc/select-field-tr-key-label-extractor 
                      :text-input-style {:background-color @(:background-color modal-selector-colors)}
                      :on-change (fn [^js/SelOption option]
                                   (al-settings-events/app-lock-settings-preference-update :lock-timeout (.-key option)))}]
       (if  (> lock-timeout 0)
         [rn-view {:style {:margin-top 10 :margin-bottom 10 :padding 10}}
          [rnp-text "The app will be locked when it is not used for this long, whether it is in the foreground or in the background. You need to enter the PIN to use OneKeePass"]]
         [rn-view {:style {:margin-top 10 :margin-bottom 10 :padding 10}}
          [rnp-text "The app will be locked when it goes to the background, or after 1 minute without use. You need to enter the PIN to use OneKeePass"]])]

      [rnp-divider]

      [rn-view {:style {:margin-top 15}}
       [select-field {:text-label "Attempts"
                      :options attemps-allowed-options
                      :value (find-matching-label attemps-allowed-options attempts-allowed)
                      :text-input-style {:background-color @(:background-color modal-selector-colors)}
                      :on-change (fn [^js/SelOption option]
                                   (al-settings-events/app-lock-settings-preference-update :attempts-allowed (.-key option)))}]

       [rn-view {:style {:margin-top 10 :margin-bottom 10
                         :padding 10}}
        [rnp-text {:style {:color @rnc/error-color}} "When PIN authentication failure exceeds this many attempts, the app will be reset. All previous references of database files, key files and internal caches will be removed"]]

       [rn-view {:style {:margin-top 5 :margin-bottom 5 :padding 10}}
        [rnp-text {:style {:color @rnc/error-color}} "The database files from its original locations are not removed"]]]])])

(defn authenticate-with-pin
  "Asks for the current PIN before the app lock settings are shown. A wrong PIN here does not
   count against the allowed attempts"
  [pin-length]
  [pin-pad {:title (lstr-dlg-title 'enterPin)
            :pin-length pin-length
            :on-submit (fn [pin rejected-fn]
                         (app-lock-events/verify-pin-entered
                          (str->int pin)
                          (fn [verified?]
                            (if verified?
                              (al-settings-events/app-lock-settings-pin-verified-success)
                              (rejected-fn)))))}])

(defn- pin-setup
  "Enter and confirm steps for a new PIN. A mismatch goes back to the enter step.
   Each step is a pad of its own (by key) so that the digits start empty.
   On iOS the PIN has a fixed length (6 or 4) and is taken once the last digit is entered.
   On Android the PIN may be of any length from NEW-PIN-MIN-LENGTH and is confirmed with the done key"
  []
  (let [first-pin (r/atom nil)
        mismatch (r/atom false)
        pin-length (r/atom (when (is-iOS) IOS-DEFAULT-PIN-LENGTH))]
    (fn []
      (let [len @pin-length]
        [rn-view
         (if (nil? @first-pin)
           ^{:key (str "enter" len)}
           [pin-pad {:title (lstr-l 'newPin)
                     ;; The length is chosen here, as in iOS 'Passcode Options', and not on the
                     ;; confirm step so that it cannot change between the two entries
                     :header (when len
                               [rnp-segmented-buttons {:style {:width 240}
                                                       :density "small"
                                                       :value (str len)
                                                       :onValueChange #(reset! pin-length (js/parseInt % 10))
                                                       :buttons (clj->js [{:value "4" :label (lstr-l 'pin4Digits)}
                                                                          {:value "6" :label (lstr-l 'pin6Digits)}])}])
                     :message (cond
                                @mismatch (lstr-mt 'enterPin 'pinsNotMatching)
                                (nil? len) (lstr-mt 'enterPin 'pinMinLength))
                     :message-error? @mismatch
                     :pin-length len
                     :min-length NEW-PIN-MIN-LENGTH
                     :on-submit (fn [pin _rejected-fn]
                                  (reset! mismatch false)
                                  (reset! first-pin pin))
                     :on-cancel al-settings-events/pin-setup-cancel}]

           ^{:key (str "confirm" len)}
           [pin-pad {:title (lstr-l 'confirmPin)
                     :pin-length len
                     :min-length NEW-PIN-MIN-LENGTH
                     :on-submit (fn [pin _rejected-fn]
                                  (if (= pin @first-pin)
                                    (al-settings-events/pin-entered (str->int pin) len)
                                    (do
                                      (reset! mismatch true)
                                      (reset! first-pin nil))))
                     :on-cancel al-settings-events/pin-setup-cancel}])]))))

(defn main-content []
  (let [{:keys [pin-lock-enabled pin-length] :as ap} @(cmn-events/app-lock-preference)
        pin-auth-completed? @(al-settings-events/app-lock-settings-pin-auth-completed)]
    ;; pin-auth-completed? is used to determine whether we need to ask user 
    ;; PIN authentication before allowing to make changes in app lock settings
    (cond
      (and pin-lock-enabled (not pin-auth-completed?))
      [authenticate-with-pin pin-length]

      @(al-settings-events/pin-setup-active)
      [pin-setup]

      :else
      [page-content ap])))

(defn content []
  [rn-scroll-view {:contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}
   [main-content]])
