(ns onekeepass.mobile.password-generator
  (:require
   [onekeepass.mobile.rn-components :refer [page-title-text-variant
                                            rn-view
                                            rnp-button
                                            rnp-divider
                                            rnp-slider
                                            rnp-switch
                                            rnp-text
                                            rnp-icon-button
                                            rn-safe-area-view]]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.password-generator :as pg-events]))

(defn appbar-title []
  [rn-view {:flexDirection "row"
            :style {:alignItems "center"
                    :justify-content "space-between"}}
   [rnp-button {:style {}
                :textColor "white"
                :mode "text"
                :onPress cmn-events/to-previous-page} "Cancel"]
   [rnp-text {:style {:color "white"
                      :max-width 100
                      :margin-right 20 :margin-left 20}
              :ellipsizeMode "tail"
              :numberOfLines 1
              :variant page-title-text-variant} "Generator"]
   [rnp-button {:style {}
                :textColor "white"
                :disabled (not @(pg-events/on-selection-available))
                :mode "text"
                :onPress pg-events/generated-password-selected} "Select"]])

(defn main-content []
  (let [{:keys [numbers symbols lowercase-letters uppercase-letters]} @(pg-events/password-generation-options)
        generated-password @(pg-events/generated-analyzed-password)]
    [rn-view {:style {:flexDirection "column" :justify-content "center" :padding 5}}
     [rn-view {:style {:flexDirection "row" :min-height 75}}
      [rn-view {:style {:flexDirection "row" :flexWrap "wrap" :align-content "center" :width "75%"}}
       [rnp-text {:style {:align-self "center"} :variant "headlineSmall"} generated-password]]
      [rnp-icon-button  {:style {:align-self "center"}
                         :icon "content-copy"
                         :onPress (fn [_e]
                                    (pg-events/write-string-to-clipboard generated-password)
                                    (cmn-events/show-snackbar "Copied to clipboard"))}]
      [rnp-icon-button  {:style {:align-self "center"}
                         :icon "cached"
                         :onPress pg-events/regenerate-password}]]

     [rnp-divider {:style {}}]

     [rn-view {:style {:flexDirection "row" :min-height 50}}  ;;:justify-content "space-between"
      [rnp-text {:style {:align-self "center" :width "20%"} :variant "titleMedium"} "Length"]
      [rnp-slider {:style {:align-self "center" :width "65%"}
                   :minimumValue 5
                   :maximumValue 200
                   :step 1
                   :value @(pg-events/password-length-slider-value)
                   :onValueChange (fn [v]
                                    (pg-events/generator-data-update :slider-value v))
                   :onSlidingComplete (fn [v]
                                        (pg-events/password-options-update :length v))}]
      [rnp-text {:style {:align-self "center" :text-align "center" :width "15%"}} @(pg-events/password-length-slider-value)]]

     [rnp-divider {:style {}}]
     [rn-view {:style {:flexDirection "row" :min-height 50 :justify-content "space-between"}}
      [rnp-text {:style {:align-self "center"} :variant "titleMedium"} "A-Z"]
      [rnp-switch {:style {:align-self "center"}
                   :value uppercase-letters
                   :onValueChange #(pg-events/password-options-update
                                    :uppercase-letters (not uppercase-letters))}]]

     [rnp-divider {:style {}}]
     [rn-view {:style {:flexDirection "row" :min-height 50 :justify-content "space-between"}}
      [rnp-text {:style {:align-self "center"} :variant "titleMedium"} "a-z"]
      [rnp-switch {:style {:align-self "center"}
                   :value lowercase-letters
                   :onValueChange #(pg-events/password-options-update
                                    :lowercase-letters (not lowercase-letters))}]]

     [rnp-divider {:style {}}]
     [rn-view {:style {:flexDirection "row" :min-height 50 :justify-content "space-between"}}
      [rnp-text {:style {:align-self "center"} :variant "titleMedium"} "0-9"]
      [rnp-switch {:style {:align-self "center"}
                   :value numbers
                   :onValueChange #(pg-events/password-options-update :numbers (not numbers))}]]

     [rnp-divider {:style {}}]
     [rn-view {:style {:flexDirection "row" :min-height 50 :justify-content "space-between"}}
      [rnp-text {:style {:align-self "center"} :variant "titleMedium"} "Symbols"]
      [rnp-switch {:style {:align-self "center"}
                   :value symbols
                   :onValueChange #(pg-events/password-options-update :symbols (not symbols))}]]]))

(defn content []
  [rn-safe-area-view {:style {:flex 1}}
   [main-content]])