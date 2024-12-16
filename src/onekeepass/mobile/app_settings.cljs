(ns onekeepass.mobile.app-settings
  (:require
   [onekeepass.mobile.common-components :refer [settings-section-header]]
   [onekeepass.mobile.constants :as const :refer [DARK-THEME
                                                  DEFAULT-SYSTEM-THEME
                                                  LIGHT-THEME]]
   [onekeepass.mobile.events.app-settings :as as-events]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.rn-components :as rnc :refer [modal-selector-colors
                                                    page-background-color
                                                    rn-safe-area-view
                                                    rn-section-list rn-view
                                                    rnms-modal-selector
                                                    rnp-divider rnp-list-icon
                                                    rnp-list-item rnp-text]]
   [onekeepass.mobile.translation :as t :refer [lstr-bl lstr-cv lstr-l lstr-mt]]
   [reagent.core :as r]))

#_(defn section-header [title]
  [rn-view  {:style {:flexDirection "row"
                     :width "100%"
                     :backgroundColor @inverse-onsurface-color
                     :margin-top 0
                     :min-height 38}}
   [rnp-text {:style {:textTransform "uppercase"
                      :alignSelf "center"
                      ;;:width "85%"
                      :text-align "center"
                      :padding-left 5} :variant "titleSmall"} (lstr-l title)]])

;;TODO: Need to add lstr for these options
(def db-session-timeout-options [{:key 15000 :label "15 seconds"}
                                 {:key 20000 :label "20 seconds"}
                                 {:key 30000  :label  "30 seconds"}
                                 {:key 60000  :label  "60 seconds"}
                                 {:key 120000 :label  "2 minutes"}
                                 {:key 300000 :label  "5 minutes"}
                                 {:key 600000 :label  "10 minutes"}
                                 {:key 900000 :label  "15 minutes"}
                                 {:key 1800000 :label  "30 minutes"}
                                 {:key 3600000 :label  "1 hour"}
                                 {:key -1 :label  "Never"}])

(def clipboard-session-timeout-options [{:key 10000 :label "10 seconds"}
                                        {:key 20000 :label "20 seconds"}
                                        {:key 30000  :label  "30 seconds"}
                                        {:key 60000  :label  "1 minute"}
                                        {:key 120000 :label  "2 minutes"}
                                        {:key 300000 :label  "5 minutes"}
                                        {:key 600000 :label  "10 minutes"}
                                        {:key 900000 :label  "15 minutes"}
                                        ;;{:key 1800000 :label  "30 minutes"}
                                        {:key 3600000 :label  "1 hour"}
                                        {:key -1 :label  "Never"}])

(def theme-options [{:key LIGHT-THEME :label "Light"}
                    {:key DARK-THEME :label "Dark"}
                    {:key DEFAULT-SYSTEM-THEME :label "System Default"}])

(def language-options [{:key "en" :label "en - English"}
                       {:key "es" :label "es - Español"}
                       {:key "fr" :label "fr - Français"}])

(defn list-item-modal-selector
  [{:keys [options value on-change label-extractor-fn list-title]}]
  (let [modal-ref (atom nil)
        label-extractor-fn (if-not (nil? label-extractor-fn) label-extractor-fn (fn [^js/RnModal d] (.-label d)))]
    ;; customSelector of react-native-modal-selector is used here to use 'Custom component'instead of the built-in select box.
    ;; Also see 'onekeepass.mobile.common-components.select-field where 'rnp-text-input-field' is used to hold and open the 
    ;; modal selector
    [rnms-modal-selector {:data options
                          :optionContainerStyle {:background-color @(:background-color modal-selector-colors)}
                          :selectedItemTextStyle {:color @(:selected-text-color modal-selector-colors) :fontWeight "bold"}
                          :onChange on-change
                          :initValue value
                          :labelExtractor label-extractor-fn
                          :ref (fn [ref] (reset! modal-ref ref))
                          ;; modal selector shows this list item 
                          :customSelector (r/as-element
                                           [rnp-list-item {:style {}
                                                           :onPress (fn []
                                                                      (.open @modal-ref))
                                                           :title (r/as-element
                                                                   [rnp-text {:style {}
                                                                              :variant "titleMedium"} (lstr-l list-title)])
                                                           :right (fn [_props] (r/as-element [rnp-list-icon {:icon const/ICON-CHEVRON-RIGHT}]))}])}]))

(defn db-timeout-explain [{:keys [key label]}]
  (if (= key -1)
    [rn-view {:style {:margin-top 5} :flexDirection "row" :flexWrap "wrap"}
     [rnp-text {:style {:margin-left 15}} (lstr-mt 'appSettings 'dbRemainOpened)]]
    [rn-view {:style {:margin-top 5} :flexDirection "row" :flexWrap "wrap"}
     [rnp-text {:style {:margin-left 15}}
      (lstr-mt 'appSettings 'inactiveDbLocked {:dbTimeoutTime label})]]))

(defn clipboard-timeout-explain [{:keys [key label]}]
  (if (= key -1)
    [rn-view {:style {:margin-top 5} :flexDirection "row" :flexWrap "wrap"}
     [rnp-text {:style {:margin-left 15}} (lstr-mt 'appSettings 'clipboardCleard1)]]
    [rn-view {:style {:margin-top 5 :margin-left 15} :flexDirection "row" :flexWrap "wrap"}
     [rnp-text {:style {}}
      (lstr-mt 'appSettings 'clipboardCleard2 {:clipboardTimeoutTime label})]]))

(defn theme-explain [{:keys [_key label]}]
  [rn-view {:style {:margin-top 1} :flexDirection "row" :flexWrap "wrap"}
   [rnp-text {:style {:margin-left 15}} label]])

(defn language-explain [{:keys [_key label]}]
  [rn-view {:style {:margin-top 1} :flexDirection "row" :flexWrap "wrap"}
   [rnp-text {:style {:margin-left 15}} label]])

(defn find-match [options value]
  (first
   (filter
    (fn [m]
      (= value (:key m)))
    options)))

(defn field-explain [title option]
  (cond
    (= title "dbTimeout")
    [db-timeout-explain option]

    (= title "clipboardTimeout")
    [clipboard-timeout-explain option]

    (= title "theme")
    [theme-explain option]

    (= title "language")
    [language-explain option]

    :else
    nil))

(defn row-item-with-select
  [title options current-selection update-fn]
  ;; title is used as key to the i18n map to get the transalated text
  ;; title - may be dbTimeout or clipboardTimeout or theme ... 

  (let [{:keys [label] :as selected-option} (find-match options current-selection)
        ;; Need to use label extraction fn to use lstr calls
        label-extractor-fn (when (= title "theme") (fn [^js/RnModal d] (lstr-cv (.-label d))))]
    [rn-view {:style {:margin-bottom 15}}
     [list-item-modal-selector {:options options
                                :list-title title
                                :on-change (fn [^js/SelOption option]
                                             (update-fn (.-key option)))

                                :label-extractor-fn label-extractor-fn

                                :value label}]
     [field-explain title selected-option]]))

(defn on-app-language-update
  "Called when user selects a language id" 
  [language-id]
  (as-events/app-language-update 
   language-id
  ;; This fn is called after the selected language translations data 
  ;; are laoded 
   (fn [_m]
     (t/reload-language-translation))))

(defn row-item
  "Provides a row component - a view with children list-item-modal-selector and db-timeout-explain
   for the 'renderItem' prop of rn-section-list
   The arg is a map that are passed in 'sections'
  "
  [_m]
  (fn [{:keys [title key]}]
    ;; title is used as key to the i18n map to get the transalated text
    (cond
      (= key "APP-THEME")
      [row-item-with-select title theme-options @(as-events/app-theme) as-events/app-theme-update]

      (= key "APP-LANGUAGE")
      [row-item-with-select title language-options @(as-events/app-language) on-app-language-update]

      (= key "DB-TIMEOUT")
      [row-item-with-select title db-session-timeout-options @(as-events/db-session-timeout-value) as-events/update-db-session-timeout]

      (= key "CLIPBOARD-TIMEOUT")
      [row-item-with-select title clipboard-session-timeout-options @(as-events/clipboard-timeout-value) as-events/update-clipboard-timeout])))

(defn settings-list-content []
  (let [sections [{:title "dataProtection"
                   :key "Data Protection"
                   :data [{:title "dbTimeout" :key "DB-TIMEOUT"}
                          {:title "clipboardTimeout" :key "CLIPBOARD-TIMEOUT"}]}

                  {:title "appearance"
                   :key "Appearance"
                   :data [{:title "theme" :key "APP-THEME"}
                          {:title "language" :key "APP-LANGUAGE"}]}]]
    [rn-section-list  {:style {}
                       :sections (clj->js sections)
                       :renderItem  (fn [props]
                                      ;; keys are (:item :index :section :separators)
                                      (let [props (js->clj props :keywordize-keys true)]
                                        (r/as-element [row-item (-> props :item)])))
                       :ItemSeparatorComponent (fn [_p]
                                                 (r/as-element [rnp-divider]))

                       :renderSectionHeader (fn [props]
                                              (let [props (js->clj props :keywordize-keys true)
                                                    {:keys [title]} (-> props :section)]
                                                (r/as-element [settings-section-header title])))}]))

(defn language-update-feedback []
  [rnc/rn-view {:style {:flex 1 :justify-content "center" :backgroundColor @page-background-color}}
   (if-not @(cmn-events/language-translation-loading-completed)
     [rnc/rnp-text {:style {:text-align "center"}} "Please wait..."]
     [rnc/rn-view
      [rnc/rnp-text {:style {:text-align "center"}} (lstr-mt 'appSettings 'languageTransLoaded)]
      [rnc/rnp-button {:style {:margin-top "10px"}
                       :mode "text"
                       :onPress cmn-events/to-previous-page} (lstr-bl 'refresh)]])])

(defn content []
  [rn-safe-area-view {:style {:flex 1 :backgroundColor @page-background-color}}
   [settings-list-content]])



;;;;;;;;;;;
