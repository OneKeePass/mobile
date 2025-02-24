(ns onekeepass.mobile.app-lock
  (:require
   [clojure.string :as str]
   [onekeepass.mobile.background :refer [is-iOS]]
   [onekeepass.mobile.rn-components :as rnc :refer [custom-color0
                                                    page-title-text-variant
                                                    appbar-text-color
                                                    page-background-color
                                                    cust-dialog
                                                    rnp-dialog-title
                                                    rnp-dialog-content
                                                    rnp-dialog-actions
                                                    rnp-text-input
                                                    rnp-helper-text
                                                    rnp-portal
                                                    rn-scroll-view
                                                    rn-keyboard-avoiding-view
                                                    rn-view
                                                    rnp-button
                                                    rnp-divider
                                                    rnp-slider
                                                    rnp-switch
                                                    rnp-segmented-buttons
                                                    rnp-text
                                                    rnp-icon-button]]
   [onekeepass.mobile.common-components :as cc :refer [select-field get-form-style]]
   [onekeepass.mobile.utils :as u :refer [str->int]]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.dialogs :as dlg-events]
   [onekeepass.mobile.events.app-lock :as app-lock-events]
   [onekeepass.mobile.translation :refer [lstr-pt lstr-bl lstr-l lstr-mt lstr-cv lstr-dlg-title]]))

;; locked-app-log-in-dialog
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
                                (app-lock-events/verify-pin-entered val)
                                #_(do
                                    (app-lock-events/verify-pin-entered val)
                                    (dlg-events/locked-app-log-in-dialog-close)))))}
     (lstr-bl "ok")]]])

(defn main-content []
  [rn-view {:flex 1 :justify-content "center"}
   [rn-view {:style {}}
    [rnp-text {:style {:text-align "center" :color @rnc/primary-color} :variant "displayMedium"} "OneKeePass"]
    [rnp-button {:style {:margin-top 10}
                 :labelStyle {:fontWeight "bold" :fontSize 15 :color @rnc/tertiary-color}
                 :mode "text"
                 :onPress (fn []
                            (dlg-events/locked-app-log-in-dialog-show))} "Enter PIN"]]])

(defn content []
  [rn-view {:style {:flex 1 :justify-content "center" :backgroundColor @page-background-color}}
   [main-content]
   [rnp-portal
    [locked-app-log-in-dialog @(dlg-events/locked-app-log-in-dialog-data)]]])