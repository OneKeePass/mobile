(ns onekeepass.mobile.app-settings
  (:require
   [reagent.core :as r]
   [onekeepass.mobile.rn-components :as rnc :refer [lstr
                                                    appbar-text-color
                                                    page-background-color
                                                    inverse-onsurface-color
                                                    modal-selector-colors
                                                    page-title-text-variant
                                                    rnms-modal-selector
                                                    rn-view
                                                    rn-safe-area-view
                                                    rn-section-list
                                                    rnp-text-input
                                                    rnp-helper-text
                                                    rnp-text-input-icon
                                                    rnp-icon-button
                                                    rnp-button
                                                    rnp-list-item
                                                    rnp-divider
                                                    rnp-list-icon
                                                    rnp-portal
                                                    rnp-text]]
   [onekeepass.mobile.events.app-settings :as as-events]
   [onekeepass.mobile.constants :as const]))

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

(defn row-item 
  "Provides a row component - a view with children list-item-modal-selector and db-timeout-explain
   for the 'renderItem' prop of rn-section-list
   The arg is a map that are passed in 'sections'
  "
  [_m]
  (fn [{:keys [title key]}]
    (cond
      (= key "DB-TIMEOUT")
      (let [{:keys [label] :as selected-m} (first 
                                            (filter 
                                             (fn [m]
                                               (= @(as-events/db-session-timeout-value) (:key m)))
                                             db-session-timeout-options))]
        [rn-view {:style {:margin-bottom 15}}
         [list-item-modal-selector {:options db-session-timeout-options
                                    :list-title title
                                    :on-change (fn [^js/SelOption option]
                                                 (as-events/update-db-session-timeout (.-key option)))
                                    :value label}]
         [db-timeout-explain selected-m]])

      (= key "CLIPBOARD-TIMEOUT")
      (let [{:keys [label] :as selected-m} (first 
                                            (filter 
                                             (fn [m]
                                               (= @(as-events/clipboard-timeout-value) (:key m)))
                                             clipboard-session-timeout-options))]
        [rn-view {:style {:margin-bottom 15}}
         [list-item-modal-selector {:options clipboard-session-timeout-options
                                    :list-title title
                                    :on-change (fn [^js/SelOption option]
                                                 (as-events/update-clipboard-timeout (.-key option)))
                                    :value label}]
         [clipboard-timeout-explain selected-m]]))))

(defn settings-list-content []
  (let [sections [{;;:title "Data Protection"
                   :title "dataProtection"
                   :key "Data Protection"
                   :data [{:title "dbTimeout" :key "DB-TIMEOUT"}
                          {:title "clipboardTimeout" :key "CLIPBOARD-TIMEOUT"}]}

                  #_{:title "App Protection"
                     :key "App Protection"
                     :data [{:title "Database Timeout"}
                            {:title "Clipboard Timeout"}]}]]
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
