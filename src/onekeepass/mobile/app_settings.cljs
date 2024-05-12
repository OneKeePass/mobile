(ns onekeepass.mobile.app-settings
  (:require [onekeepass.mobile.constants :as const :refer [DARK-THEME
                                                           DEFAULT-SYSTEM-THEME
                                                           LIGHT-THEME]]
            [onekeepass.mobile.events.app-settings :as as-events]
            [onekeepass.mobile.rn-components :as rnc :refer [inverse-onsurface-color
                                                             lstr
                                                             modal-selector-colors
                                                             page-background-color
                                                             rn-safe-area-view
                                                             rn-section-list
                                                             rn-view
                                                             rnms-modal-selector
                                                             rnp-divider
                                                             rnp-list-icon
                                                             rnp-list-item
                                                             rnp-text]]
            [reagent.core :as r]))

(defn section-header [title]
  [rn-view  {:style {:flexDirection "row"
                     :width "100%"
                     :backgroundColor @inverse-onsurface-color
                     :margin-top 0
                     :min-height 38}}
   [rnp-text {:style {:textTransform "uppercase"
                      :alignSelf "center"
                      ;;:width "85%"
                      :text-align "center"
                      :padding-left 5} :variant "titleSmall"} (lstr title)]])

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
  [{:keys [options value on-change list-title]}]
  (let [modal-ref (atom nil)]
    ;; customSelector of react-native-modal-selector is used here to use 'Custom component'instead of the built-in select box.
    ;; Also see 'onekeepass.mobile.common-components.select-field where 'rnp-text-input-field' is used to hold and open the 
    ;; modal selector
    [rnms-modal-selector {:data options
                          :optionContainerStyle {:background-color @(:background-color modal-selector-colors)}
                          :selectedItemTextStyle {:color @(:selected-text-color modal-selector-colors) :fontWeight "bold"}
                          :onChange on-change
                          :initValue value
                          :ref (fn [ref] (reset! modal-ref ref))
                          ;; modal selector shows this list item 
                          :customSelector (r/as-element
                                           [rnp-list-item {:style {}
                                                           :onPress (fn []
                                                                      (.open @modal-ref))
                                                           :title (r/as-element
                                                                   [rnp-text {:style {}
                                                                              :variant "titleMedium"} (lstr list-title)])
                                                           :right (fn [_props] (r/as-element [rnp-list-icon {:icon const/ICON-CHEVRON-RIGHT}]))}])}]))

(defn db-timeout-explain [{:keys [key label]}]
  (if (= key -1)
    [rn-view {:style {:margin-top 5} :flexDirection "row" :flexWrap "wrap"}
     [rnp-text {:style {:margin-left 15}} "Database will remain open till locked or closed"]]
    [rn-view {:style {:margin-top 5} :flexDirection "row" :flexWrap "wrap"}
     [rnp-text {:style {:margin-left 15}}
      (str "Inactive database will be locked after the timeout period of" " " label)]
     #_[rnp-text {:style {:margin-left 0}} label]]))

(defn clipboard-timeout-explain [{:keys [key label]}]
  (if (= key -1)
    [rn-view {:style {:margin-top 5} :flexDirection "row" :flexWrap "wrap"}
     [rnp-text {:style {:margin-left 15}} "Clipboard will not be cleared"]]
    [rn-view {:style {:margin-top 5 :margin-left 15} :flexDirection "row" :flexWrap "wrap"}
     [rnp-text {:style {}}
      (str "Clipboard will be cleared in" " " label)]
     #_[rnp-text {:style {:margin-left 5}} label]]))

(defn theme-explain [{:keys [key label]}]
  [rn-view {:style {:margin-top 1} :flexDirection "row" :flexWrap "wrap"}
   [rnp-text {:style {:margin-left 15}} label]]
  )

(defn language-explain [{:keys [key label]}]
  [rn-view {:style {:margin-top 1 } :flexDirection "row" :flexWrap "wrap"}
   [rnp-text {:style {:margin-left 15}} label]])

(defn find-match [options value]
  (first
   (filter
    (fn [m]
      (= value (:key m)))
    options)))

#_(defn theme-row-item [title]
    (let [{:keys [label]} (find-match theme-options @(as-events/app-theme))]
      [rn-view {:style {:margin-bottom 15}}
       [list-item-modal-selector {:options theme-options
                                  :list-title title
                                  :on-change (fn [^js/SelOption option]
                                               (as-events/app-theme-update (.-key option)))
                                  :value label}]
       #_[db-timeout-explain selected-m]]))

#_(defn language-row-item [title]
    (let [{:keys [label]} (find-match language-options @(as-events/app-language))]
      [rn-view {:style {:margin-bottom 15}}
       [list-item-modal-selector {:options language-options
                                  :list-title title
                                  :on-change (fn [^js/SelOption option]
                                               (as-events/app-language-update (.-key option)))
                                  :value label}]
       #_[db-timeout-explain selected-m]]))

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

(defn row-item-with-select [title options current-selection update-fn]
  (let [{:keys [label] :as selected-option} (find-match options current-selection)]
    [rn-view {:style {:margin-bottom 15}}
     [list-item-modal-selector {:options options
                                :list-title title
                                :on-change (fn [^js/SelOption option]
                                             (update-fn (.-key option)))
                                :value label}]
     [field-explain title selected-option]]))

(defn row-item
  "Provides a row component - a view with children list-item-modal-selector and db-timeout-explain
   for the 'renderItem' prop of rn-section-list
   The arg is a map that are passed in 'sections'
  "
  [_m]
  (fn [{:keys [title key]}]
    (cond
      (= key "APP-THEME")
      [row-item-with-select title theme-options @(as-events/app-theme) as-events/app-theme-update]

      (= key "APP-LANGUAGE")
      [row-item-with-select title language-options @(as-events/app-language) as-events/app-language-update]

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
                                                (r/as-element [section-header title])))}]))

(defn content []
  [rn-safe-area-view {:style {:flex 1 :backgroundColor @page-background-color}}
   [settings-list-content]])



;;;;;;;;;;;
