(ns onekeepass.mobile.android.autofill.entry-list
  (:require [onekeepass.mobile.android.autofill.events.entry-list :as el-events]
            [onekeepass.mobile.android.autofill.events.common :as android-af-cmn-events]
            [onekeepass.mobile.common-components :refer [menu-action-factory]]
            [onekeepass.mobile.constants :refer [PASSWORD USERNAME]]
            [onekeepass.mobile.icons-list :refer [icon-id->name]]
            [onekeepass.mobile.rn-components :as rnc :refer [icon-color
                                                             page-background-color
                                                             primary-container-color
                                                             rn-safe-area-view
                                                             rn-section-list
                                                             rn-view
                                                             rnp-divider
                                                             rnp-list-icon
                                                             rnp-list-item
                                                             rnp-menu
                                                             rnp-menu-item
                                                             rnp-portal
                                                             rnp-text]]
            [onekeepass.mobile.translation :refer [lstr-cv lstr-ml]]
            [reagent.core :as r]))

(def entry-long-press-menu-action (menu-action-factory el-events/long-press-menu-hide))

(defn show-entry-long-press-menu [^js/PEvent event uuid]
  (el-events/long-press-start (-> event .-nativeEvent .-pageX) (-> event .-nativeEvent .-pageY) uuid))

(defn entry-long-press-menu []
  (let [{:keys [show x y]} @(el-events/entry-list-long-press-data)]
    (println "entry-long-press-menu is called with show " show)
    [rnp-menu {:visible show :onDismiss el-events/long-press-menu-hide :anchor (clj->js {:x x :y y})}
     [rnp-menu-item {:title (lstr-ml 'copyUserName)
                     :onPress #() #_(entry-long-press-menu-action
                                     form-events/copy-field-to-clipboard USERNAME)}]
     [rnp-menu-item {:title  (lstr-ml 'copyPassword)
                     :onPress #() #_(entry-long-press-menu-action
                                     form-events/copy-field-to-clipboard PASSWORD)}]
     [rnp-divider]

     [rnp-menu-item {:title (lstr-ml 'entryDetails)
                     :onPress (entry-long-press-menu-action android-af-cmn-events/to-entry-form-page)}]]))


(defn row-item []
  (fn [{:keys [title secondary-title icon-id uuid] :as _entry-summary}]
    (let [icon-name (icon-id->name icon-id)]
      [rnp-list-item {:onPress #() #_#(el-events/entry-pressed uuid)
                      :onLongPress (fn [e]
                                     (show-entry-long-press-menu e uuid))
                      :title (r/as-element
                              [rnp-text {:variant "titleMedium"} title])
                      :description secondary-title
                      :left (fn [_props] (r/as-element
                                          [rnp-list-icon {:icon icon-name
                                                          :color @icon-color
                                                          :style {:margin-left 5 :align-self "center"}}]))}])))

(defn section-header [title]
  [rn-view  {:style {:flexDirection "row"
                     :backgroundColor @primary-container-color
                     :margin-top 5
                     :min-height 38}}
   [rnp-text {:style {:alignSelf "center"
                      :text-align "center"
                      :width "85%" :padding-left 15}
              :variant "titleLarge"} (lstr-cv title)]])

(defn main-content []
  (let [entry-items @(el-events/selected-entry-items)
        ;; search-entry-items [] #_@(cmn-events/search-result-entry-items)
        ;;term @(cmn-events/search-term)
        ;; entry-items (if (empty? search-entry-items) entry-items  search-entry-items)
        sections [{:title "Entries"
                   :key "Entries"
                   :data entry-items}]]
    
    [rn-view
     #_(when (and (not (str/blank? term)) (empty? search-entry-items))
         [rn-view {:style {:margin-top 5 :height 40 :justify-content "center"}}
          [rnp-text {:style {:text-align "center" :color @rnc/error-color}
                     :variant "titleSmall"} (lstr-mt TR-KEY-AUTOFILL 'noEntryFound)]])

     [rn-section-list {:scrollEnabled false
                       :sections (clj->js sections)
                       :renderItem (fn [props]
                                     ;; keys in props are (:item :index :section :separators)
                                     (let [props (js->clj props :keywordize-keys true)]
                                       (r/as-element [row-item (-> props :item)])))
                       :ItemSeparatorComponent (fn [_p] (r/as-element [rnp-divider]))
                       :stickySectionHeadersEnabled false
                       :renderSectionHeader (fn [props] ;; key is :section
                                              (let [props (js->clj props :keywordize-keys true)
                                                    {:keys [title _data]} (-> props :section)]
                                                (r/as-element [section-header title])))}]]))

(defn content []
  [rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}
   [rnc/rn-scroll-view {:style {} :contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}
    [main-content]]
   [rnp-portal
      [entry-long-press-menu]]])

