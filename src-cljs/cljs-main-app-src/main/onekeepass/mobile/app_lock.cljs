(ns onekeepass.mobile.app-lock
  (:require
   [clojure.string :as str]
   [onekeepass.mobile.rn-components :as rnc :refer [page-background-color
                                                    react-use-effect
                                                    cust-dialog
                                                    no-assist-text-props
                                                    rnp-dialog-title
                                                    rnp-dialog-content
                                                    rnp-dialog-actions
                                                    rnp-text-input
                                                    rnp-helper-text
                                                    rnp-portal
                                                    rn-view
                                                    rnp-button
                                                    rnp-text]]
   [onekeepass.mobile.utils :as u :refer [str->int]]
   [onekeepass.mobile.events.dialogs :as dlg-events]
   [onekeepass.mobile.events.app-lock :as app-lock-events]
   [onekeepass.mobile.translation :refer [lstr-bl lstr-l lstr-mt lstr-dlg-title]]))

(defn locked-app-log-in-dialog [{:keys [dialog-show error-text pin-value visible]}]
  [cust-dialog {:style {} :dismissable true :visible dialog-show :onDismiss #()}
   [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1} (lstr-dlg-title 'enterPin)]
   [rnp-dialog-content
    [rn-view {:flexDirection "column"}
     [rnp-text-input (merge no-assist-text-props
                            {:label (lstr-l 'pin)
                             :autoFocus true
                             :keyboardType "number-pad"
                             :defaultValue pin-value
                             :secureTextEntry (not visible)
                             :onChangeText #(dlg-events/locked-app-log-in-dialog-update-with-map
                                             {:pin-value %
                                              :error-text nil})})]
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
                                (dlg-events/locked-app-log-in-dialog-update-with-map
                                 {:error-text (lstr-mt 'enterPin 'validPinRequired)})
                                (app-lock-events/verify-pin-entered val app-lock-events/app-lock-verify-pin-handler))))}
     (lstr-bl "ok")]]])

(defn main-content []
  [rn-view {:flex 1 :justify-content "center"}
   [rn-view {:style {}}
    [rnp-text {:style {:text-align "center" :color @rnc/primary-color} :variant "displayMedium"} "OneKeePass"]
    ;; The dialog opens on its own. This button is for reopening it after 'Cancel'
    [rnp-button {:style {:margin-top 10}
                 :labelStyle {:fontWeight "bold" :fontSize 15 :color @rnc/tertiary-color}
                 :mode "text"
                 :onPress (fn []
                            (dlg-events/locked-app-log-in-dialog-show))} (lstr-dlg-title 'enterPin)]]])

(defn- content-f []
  (react-use-effect
   (fn []
     ;; When the app is locked while in the background, the dialog is opened later when the
     ;; app comes to foreground (see register-app-becomes-active) so that the PIN field gets
     ;; focus and the keyboard only when the user can see it
     (when (= "active" (.-currentState rnc/rn-app-state))
       (app-lock-events/pin-dialog-show-when-locked))
     js/undefined)
   (clj->js []))
  [rn-view {:style {:flex 1 :justify-content "center" :backgroundColor @page-background-color}}
   [main-content]
   [rnp-portal
    [locked-app-log-in-dialog @(dlg-events/locked-app-log-in-dialog-data)]]])

(defn content []
  [:f> content-f])
