(ns onekeepass.mobile.password-generator
  (:require
   [onekeepass.mobile.background :refer [is-iOS]]
   [onekeepass.mobile.rn-components :as rnc :refer [custom-color0
                                                    page-title-text-variant
                                                    appbar-text-color
                                                    page-background-color
                                                    rnp-text-input
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
   [onekeepass.mobile.utils :as u :refer [contains-val?]]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.password-generator :as pg-events]
   [onekeepass.mobile.translation :refer [lstr-pt lstr-bl lstr-l lstr-cv]]))

(defn appbar-title []
  [rn-view {:flexDirection "row"
            :style {:alignItems "center"
                    :justify-content "space-between"}}
   [rnp-button {:style {}
                :textColor @appbar-text-color
                :mode "text"
                :onPress cmn-events/to-previous-page} "Cancel"]
   [rnp-text {:style {:color @appbar-text-color
                      :max-width 100
                      :margin-right 20 :margin-left 20}
              :ellipsizeMode "tail"
              :numberOfLines 1
              :variant page-title-text-variant} (lstr-pt 'generator)]
   [rnp-button {:style {}
                :textColor @appbar-text-color
                :disabled (not @(pg-events/on-selection-available))
                :mode "text"
                :onPress pg-events/generated-password-selected} (lstr-bl 'select)]])

(defn score-color [{:keys [name]}]
  (cond
    (contains-val? ["VeryDangerous" "Dangerous" "VeryWeak" "Weak"] name)
    @rnc/error-color

    (= name "Good")
    @rnc/custom-color1

    :else
    @custom-color0))

;; keys should match enum WordListSource
(def all-wl [{:key "EFFLarge" :label "EFF Large List"}
             {:key "EFFShort1" :label "EFF Short List 1"}
             {:key "EFFShort2" :label "EFF Short List 2"}
             {:key "Google10000UsaEnglishNoSwearsMedium" :label "U.S English, No Swears words"}
             {:key "FrenchDicewareWordlist" :label "French Word List"}
             {:key "GermanDicewareWordlist" :label "German Word List"}])

;; keys should match enum ProbabilityOption
(defn capitalize-word-choices []
  [{:key "Always" :label (lstr-l 'always)}
   {:key "Never" :label (lstr-l 'never)}
   {:key "Sometimes" :label (lstr-l 'sometimes)}])

;; keys should match enum ProbabilityOption
(def capitalize-first-choices capitalize-word-choices)

(defn select-on-change-factory
  "The arg 'handler-fn' is  a fn that calls the event to update the value of 'field-name-kw'
   Returns a fn that accepts the select option as arg
   "
  [handler-fn field-name-kw]
  (fn [^js/SelOption option]
    (handler-fn field-name-kw (.-key option))))

(defn select-on-change-factory-1 [field-name-kw]
  (select-on-change-factory pg-events/pass-phrase-options-select-field-update field-name-kw))

(defn pass-phrase-panel [{:keys [word-list-source separator capitalize-first capitalize-words]}]
  (let [wl-source (:type-name word-list-source)
        cap-first (:type-name capitalize-first)
        cap-words (:type-name capitalize-words)]
    (println  "slider val in in PP " @(pg-events/password-length-slider-value))
    [rn-view
     [rn-view {:style (get-form-style)}
      [rn-view {:style {}}
       [select-field {:text-label (lstr-l 'wordList)
                      :options all-wl
                      :disabled false
                      :value wl-source
                      :on-change  (select-on-change-factory-1 :word-list-source)}]]]
     [rn-view {:style (get-form-style)}

      [rn-view {:style {:flexDirection "row" :min-height 50 :margin-left 15}}
       [rnp-text {:style {:align-self "center" :width "20%"}} (lstr-l 'words)]
       [rnp-slider {:style {:align-self "center" :width "65%"}
                    :minimumValue 1
                    :maximumValue 40
                    :step 1
                    :value @(pg-events/password-length-slider-value)
                    :onValueChange (fn [v]
                                     (pg-events/generator-data-update :slider-value v))
                    :onSlidingComplete (fn [v]
                                         (pg-events/pass-phrase-options-update :words v))}]
       [rnp-text {:style {:align-self "center" :text-align "center" :width "15%"}} @(pg-events/password-length-slider-value)]]

      [rn-view {:style {:margin-top 5}}
       [select-field {:text-label (lstr-l 'capitalizeFirstLetter)
                      :options (capitalize-first-choices)
                      :disabled false
                      :value cap-first
                      :on-change (select-on-change-factory-1 :capitalize-first)}]]

      [rn-view {:style {:margin-top 5}}
       [select-field {:text-label (lstr-l 'capitalizeWords)
                      :options (capitalize-word-choices)
                      :disabled false
                      :value cap-words
                      :on-change (select-on-change-factory-1 :capitalize-words)}]]

      [rn-view {:style {:margin-top 5}}
       [rnp-text-input {:style {}
                        :label (lstr-l 'separator)
                        :editable true
                        :defaultValue separator
                        :onChangeText #(pg-events/pass-phrase-options-update  :separator %)}]]]]))

(defn password-gen-panel
  [{:keys [numbers symbols lowercase-letters uppercase-letters]}]
  [rn-view {:style (get-form-style)}
   [rn-view {:style {:flexDirection "row" :min-height 50}}  ;;:justify-content "space-between"
    [rnp-text {:style {:align-self "center" :width "20%"} :variant "titleMedium"} (lstr-l 'length)]
    [rnp-slider {:style {:align-self "center" :width "65%"}
                 :minimumValue 5
                 :maximumValue 200
                 :step 1
                 :value @(pg-events/password-length-slider-value)
                 ;; This callback continuously called while the user is dragging the slider.
                 :onValueChange (fn [v]
                                  (pg-events/generator-data-update :slider-value v))
                 ;; Callback that is called when the user releases the slider, regardless if the value has changed
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
    [rnp-text {:style {:align-self "center"} :variant "titleMedium"} (lstr-l 'symbols)]
    [rnp-switch {:style {:align-self "center"}
                 :value symbols
                 :onValueChange #(pg-events/password-options-update :symbols (not symbols))}]]])

(defn main-content []
  (let [{:keys [analyzed-password score]} @(pg-events/generator-password-result)
        generated-password analyzed-password
        panel-shown @(pg-events/generator-panel-shown)]
    [rn-view {:style {:flexDirection "column" :justify-content "center" :padding 5}}
     [rn-view {:style {:flexDirection "row"  :align-self "center" :margin-top 5 :margin-bottom 10}}
      [rnp-segmented-buttons {:style {:width "95%"}
                              :value panel-shown :onValueChange (fn [val] (pg-events/generator-panel-shown-update val))
                              :buttons (clj->js [{:value "password" :label (lstr-l 'password)}
                                                 {:value "passphrase" :label (lstr-l 'passPhrase)}])}]]

     [rnp-divider {:style {}}]

     [rn-view {:style (merge {:flexDirection "column"} (get-form-style))}
      [rn-view {:style {:flexDirection "row" :min-height 75}}
       [rn-view {:style {:flexDirection "row" :flexWrap "wrap" :align-content "center" :width "80%"}}
        [rnp-text {:style {:align-self "center" :color @rnc/primary-color} :variant "titleLarge"} generated-password]]
       [rnp-icon-button  {:style {:align-self "center"}
                          :icon "content-copy"
                          :onPress (fn [_e]
                                     (pg-events/write-string-to-clipboard generated-password)
                                     (cmn-events/show-snackbar "Copied to clipboard"))}]
       [rnp-icon-button  {:style {:align-self "center" :margin-left -10}
                          :icon "cached"
                          :onPress pg-events/regenerate-password}]]

      [rnp-text {:style {:align-self "center" :color (score-color score)} :variant "bodyLarge"} (lstr-cv (:name score))]]

     #_[rnp-divider {:style {}}]

     [rn-view {:style {:margin-bottom 15}}]

     (if (= panel-shown "password")
       [password-gen-panel @(pg-events/password-generation-options)]
       [pass-phrase-panel @(pg-events/pass-phrase-generation-options)])]))

(defn content []
  [rn-keyboard-avoiding-view {:style {:flex 1}
                              :behavior (if (is-iOS) "padding" nil)}
   [rn-scroll-view {:contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}
    [main-content]]])