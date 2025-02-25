(ns onekeepass.mobile.app-lock-settings
  (:require
   [clojure.string :as str]
   [onekeepass.mobile.background :refer [is-iOS]]
   [onekeepass.mobile.rn-components :as rnc :refer [cust-dialog
                                                    rnp-dialog-title
                                                    rnp-dialog-content
                                                    rnp-dialog-actions
                                                    rnp-helper-text
                                                    rnp-portal
                                                    modal-selector-colors
                                                    page-background-color
                                                    rnp-text-input
                                                    rn-scroll-view
                                                    rn-keyboard-avoiding-view
                                                    rn-view
                                                    rnp-button
                                                    rnp-divider
                                                    rnp-switch
                                                    rnp-text]]
   [onekeepass.mobile.common-components :as cc :refer [select-field get-form-style]]
   [onekeepass.mobile.utils :as u :refer [str->int]]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.dialogs :as dlg-events]
   [onekeepass.mobile.events.app-lock :as app-lock-events]
   [onekeepass.mobile.events.app-lock-settings :as al-settings-events]
   [onekeepass.mobile.translation :refer [lstr-bl lstr-l  lstr-dlg-title lstr-mt]]))

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

;; This dialog component is copied from app_lock.cljs and slightly modified
;; so that we can ask user to enter pin before making changes to the app lock settings
;; TDOO: Make a single component
(defn locked-app-log-in-dialog [{:keys [dialog-show error-text pin-value visible]}]
  [cust-dialog {:style {} :dismissable true :visible dialog-show :onDismiss #()}
   [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1} (lstr-dlg-title 'enterPin)]
   [rnp-dialog-content
    [rn-view {:flexDirection "column"}
     [rnp-text-input {:label (lstr-l 'pin)
                      :keyboardType "number-pad"
                      :defaultValue pin-value
                      :secureTextEntry (not visible)
                      :onChangeText #(dlg-events/locked-app-log-in-dialog-update-with-map
                                      {:pin-value %
                                       :error-text nil})}]
     (when error-text
       [rnp-helper-text {:type "error" :visible true}
        error-text])]]
   [rnp-dialog-actions
    [rnp-button {:mode "text"
                 :onPress (fn [] (dlg-events/locked-app-log-in-dialog-close))}
     (lstr-bl "cancel")]
    [rnp-button {:mode "text"
                 :disabled (not (nil? error-text))
                 :onPress (fn []
                            (let [val (str->int pin-value)]
                              (if (str/blank? pin-value)
                                (dlg-events/app-pin-lock-settings-dialog-update-with-map
                                 {:error-text (lstr-mt 'enterPin 'validPinRequired)})
                                (app-lock-events/verify-pin-entered
                                 val
                                 (fn [pin-verify-result]
                                   (if pin-verify-result
                                     (do
                                       (al-settings-events/app-lock-settings-pin-verified-success)
                                       (dlg-events/locked-app-log-in-dialog-close))
                                     (dlg-events/locked-app-log-in-dialog-update-with-map
                                      {:error-text (lstr-mt 'enterPin 'validPinRequired)})))))))}
     (lstr-bl "ok")]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
                                   (al-settings-events/app-lock-settings-preference-update :lock-timeout (.-key option)))}]
       (if  (> lock-timeout 0)
         [rn-view {:style {:margin-top 10 :margin-bottom 10 :padding 10}}
          [rnp-text "The app will be locked after this timeout. You need to enter the PIN to use OneKeePass"]]
         [rn-view {:style {:margin-top 10 :margin-bottom 10 :padding 10}}
          [rnp-text "The app will be locked when app looses focus. You need to enter the PIN to use OneKeePass"]])]

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
        [rnp-text "When PIN authentication failure exceeds this many attempts, the app will be reset. All previous references of databases, key files and internal caches will be removed"]]

       [rn-view {:style {:margin-top 5 :margin-bottom 5 :padding 10}}
        [rnp-text "The database files from its original locations are not removed"]]]])])

(defn authenticate-with-pin []
  [rn-view {:flex 1 :justify-content "center"}
   [rn-view {:style {}}
    [rnp-button {:style {:margin-top 10}
                 :labelStyle {:fontWeight "bold" :fontSize 15 :color @rnc/tertiary-color}
                 :mode "text"
                 :onPress (fn []
                            (dlg-events/locked-app-log-in-dialog-show))} "Enter PIN"]]])

(defn main-content []
  (let [{:keys [pin-lock-enabled] :as ap} @(cmn-events/app-lock-preference)
        pin-auth-completed? @(al-settings-events/app-lock-settings-pin-auth-completed)]
    ;; pin-auth-completed? is used to determine whether we need to ask user 
    ;; PIN authentication before allowing to make changes in app lock settings
    (if  (and pin-lock-enabled (not pin-auth-completed?))
      [authenticate-with-pin]
      [page-content ap])))

(defn content []
  [rn-keyboard-avoiding-view {:style {:flex 1}
                              :behavior (if (is-iOS) "padding" nil)}
   [rn-scroll-view {:contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}
    [main-content]]

   [rnp-portal
    [app-pin-lock-settings-dialog @(dlg-events/app-pin-lock-settings-dialog-data)]
    [locked-app-log-in-dialog @(dlg-events/locked-app-log-in-dialog-data)]]])