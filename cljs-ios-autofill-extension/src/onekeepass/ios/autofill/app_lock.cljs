(ns onekeepass.ios.autofill.app-lock
  (:require [onekeepass.ios.autofill.rn-components :as rnc :refer [rn-keyboard
                                                                   rn-view
                                                                   rnp-button
                                                                   rnp-helper-text
                                                                   rnp-text
                                                                   rnp-text-input
                                                                   rnp-text-input-icon]]
            [onekeepass.ios.autofill.events.app-lock :as app-lock-events]
            [onekeepass.ios.autofill.constants :as const]
            [onekeepass.ios.autofill.translation :refer [lstr-bl]]
            [onekeepass.ios.autofill.utils :as u]
            [reagent.core :as r]))

(defn main-content [{:keys [pin-entered error-text password-visible]}]
  [rn-view {:style {:flex 1
                    :width "90%"
                    :flexDirection "column"}}
   [rn-view {:style {:flex .5  :align-items "center"
                     :justify-content "center"
                     :flexDirection "column"}}
    [rnp-text {:style {:margin-top 10 :margin-bottom 20} :variant "titleMedium"} "Enter PIN"]
    [rnp-text-input {:style {:width "80%"}
                     :label "PIN"
                     :defaultValue pin-entered
                     :autoComplete "off"
                     :autoCapitalize "none"
                     :autoCorrect false
                     :keyboardType "number-pad"
                     :secureTextEntry (not password-visible)
                     :right (r/as-element
                             [rnp-text-input-icon
                              {:icon (if password-visible "eye" "eye-off")
                               :onPress #(app-lock-events/app-lock-update-data :password-visible (not password-visible))}])
                     :onChangeText (fn [v]
                                     (app-lock-events/app-lock-update-data :pin-entered v))}]

    (when error-text
      [rnp-helper-text {:type "error" :visible true}
       error-text])

    [rn-view {:flexDirection const/FLEX-DIR-ROW :style {:margin-top 20 :justify-content "center"}}

     [rnp-button {:mode "text"
                  :onPress (fn []
                             (.dismiss rn-keyboard)
                              ;;^js/RNKeyboard (.dismiss rn-keyboard)
                             (app-lock-events/verify-pin-entered (u/str->int pin-entered)))}
      (lstr-bl 'continue)]]]])


(defn content []
  [main-content @(app-lock-events/app-lock-data)])