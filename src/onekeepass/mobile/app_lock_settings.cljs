(ns onekeepass.mobile.app-lock-settings
  (:require
   [clojure.string :as str]
   [onekeepass.mobile.background :refer [is-iOS]]
   [onekeepass.mobile.rn-components :as rnc :refer [custom-color0
                                                    cust-dialog
                                                    rnp-dialog-title
                                                    rnp-dialog-content
                                                    rnp-dialog-actions
                                                    rnp-helper-text
                                                    rnp-portal
                                                    modal-selector-colors
                                                    page-title-text-variant
                                                    appbar-text-color
                                                    page-background-color
                                                    rnp-text-input
                                                    rn-scroll-view
                                                    rn-keyboard-avoiding-view
                                                    rn-view
                                                    rnp-button
                                                    rnp-divider
                                                    rnp-switch
                                                    rnp-text
                                                    rnp-icon-button]]
   [onekeepass.mobile.common-components :as cc :refer [select-field get-form-style]]
   [onekeepass.mobile.utils :as u :refer [contains-val? str->int]]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.dialogs :as dlg-events]
   [onekeepass.mobile.events.app-lock-settings :as al-settings-events]
   [onekeepass.mobile.translation :refer [lstr-pt lstr-bl lstr-l lstr-cv lstr-dlg-title lstr-mt]]))


;;;;;;;;;;;; dialog ;;;;;;;;;;

(defn app-pin-lock-settings-dialog [{:keys [dialog-show error-text pin-value visible]}]
  [cust-dialog {:style {} :dismissable true :visible dialog-show :onDismiss #()}
   [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1} (lstr-dlg-title 'enterPin)]
   [rnp-dialog-content
    [rn-view {:flexDirection "column"}
     [rnp-text-input {:label (lstr-l 'pin)
                      :keyboardType "number-pad"
                      :defaultValue pin-value
                      :secureTextEntry (not visible)
                      :onChangeText #(dlg-events/app-pin-lock-settings-dialog-update-with-map
                                      {:pin-value %
                                       :error-text nil})}]
     (when error-text
       [rnp-helper-text {:type "error" :visible true}
        error-text])]]
   [rnp-dialog-actions
    [rnp-button {:mode "text"
                 :onPress (fn [] (dlg-events/app-pin-lock-settings-dialog-close))}
     (lstr-bl "cancel")]
    [rnp-button {:mode "text"
                 :disabled (not (nil? error-text))
                 :onPress (fn []
                            (let [val (str->int pin-value)]
                              (if (str/blank? pin-value)
                                (dlg-events/app-pin-lock-settings-dialog-update-with-map
                                 {:error-text (lstr-mt 'enterPin 'validPinRequired)})
                                (do
                                  (al-settings-events/pin-entered val)
                                  (dlg-events/app-pin-lock-settings-dialog-close)))))}
     (lstr-bl "ok")]]])

;;;;;;;;

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

(defn find-matching-label
  "Gets the option label from the selected value"
  [options value]
  (:label (first
           (filter
            (fn [m]
              (= value (:key m)))
            options))))

(defn main-content []
  (let [{:keys [pin-lock-enabled lock-timeout attempts-allowed]} @(cmn-events/app-lock-preference)]
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
                                        (dlg-events/app-pin-lock-settings-dialog-show-with-state {})
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
                                     (al-settings-events/app-lock-preference-update :lock-timeout (.-key option)))}]
         (when (> lock-timeout 0)
           [rn-view {:style {:margin-top 10 :margin-bottom 10 :padding 10}}
            [rnp-text "The app will be locked after this timeout. You need to enter the PIN to use OneKeePass"]])]

        [rnp-divider]


             ;; ^{:key "attemps-allowed-options"} 
        [rn-view {:style {:margin-top 15}}
         [select-field {:text-label "Attempts"
                        :options attemps-allowed-options
                        :value (find-matching-label attemps-allowed-options attempts-allowed)
                        :text-input-style {:background-color @(:background-color modal-selector-colors)}
                        :on-change (fn [^js/SelOption option]
                                     (al-settings-events/app-lock-preference-update :attempts-allowed (.-key option)))}]

         [rn-view {:style {:margin-top 10 :margin-bottom 10
                           :padding 10}}
          [rnp-text "When PIN authentication failure exceeds this many attempts, the app will be reset. All previous references of databases, key files and internal caches will be removed"]]

         [rn-view {:style {:margin-top 5 :margin-bottom 5 :padding 10}}
          [rnp-text "The database files from its original locations are not removed"]]]])])
  ;;;;

  #_[rn-view
     [rnp-text "Content will come here"]])

(defn content []
  [rn-keyboard-avoiding-view {:style {:flex 1}
                              :behavior (if (is-iOS) "padding" nil)}
   [rn-scroll-view {:contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}
    [main-content]]

   [rnp-portal
    [app-pin-lock-settings-dialog @(dlg-events/app-pin-lock-settings-dialog-data)]]])



#_(defn appbar-title []
  [rn-view {:flexDirection "row"
            :style {:alignItems "center"
                    :justify-content "space-between"}}
   [rnp-button {:style {}
                :textColor @appbar-text-color
                :mode "text"
                :onPress cmn-events/to-previous-page} (lstr-bl 'cancel)]
   [rnp-text {:style {:color @appbar-text-color
                      :max-width 100
                      :margin-right 20 :margin-left 20}
              :ellipsizeMode "tail"
              :numberOfLines 1
              :variant page-title-text-variant} (lstr-pt 'appLock)]
   [rnp-button {:style {}
                :textColor @appbar-text-color
                :disabled false #_(not @(pg-events/on-selection-available))
                :mode "text"
                :onPress #()} (lstr-bl 'save)]])




#_(if (str/blank? pin-value)
    (dlg-events/app-pin-lock-settings-dialog-update-with-map
     {:error-text "Please enter a valid PIN"})
    (do
      (println "pin-value type " (type pin-value))
      #_(al-settings-events/pin-entered (str->int))
      (dlg-events/app-pin-lock-settings-dialog-close)))

#_[rn-view {:style {:flexDirection "row" :min-height 50 :justify-content "space-between"}}
   [rnp-text {:style {:align-self "center" :padding-left 5} :variant "titleMedium"} "Reset App"]
   [rn-view {:style {:padding-right 10 :align-self "center"}}
    [rnp-switch {:style {:align-self "center"}
                 :value true
                 :onValueChange (fn [])}]]]