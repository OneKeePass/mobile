(ns onekeepass.mobile.android.autofill.entry-list
  "Only the Android Autofill specific entry list components"
  (:require [clojure.string :as str]
            [onekeepass.mobile.android.autofill.events.common :as android-af-cmn-events]
            [onekeepass.mobile.android.autofill.events.entry-list :as el-events]
            [onekeepass.mobile.common-components :refer [menu-action-factory]]
            [onekeepass.mobile.constants :refer [TR-KEY-AUTOFILL]]
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
                                                             rnp-searchbar
                                                             rnp-text]]
            [onekeepass.mobile.translation :refer [lstr-cv lstr-ml lstr-mt]]
            [reagent.core :as r]))

;; NOTE: We are showing menu dialog for both single press and long press action
(def entry-long-press-menu-action (menu-action-factory el-events/long-press-menu-hide))

(defn show-entry-long-press-menu [^js/PEvent event uuid]
  (el-events/long-press-start (-> event .-nativeEvent .-pageX) (-> event .-nativeEvent .-pageY) uuid))

(defn entry-long-press-menu []
  (let [{:keys [show x y]} @(el-events/entry-list-long-press-data)]
    [rnp-menu {:visible show :key (str show) :onDismiss el-events/long-press-menu-hide :anchor (clj->js {:x x :y y})}
     ;; TODO: Disable this menuitem if the both USERNAME and PASSWORD are nil
     [rnp-menu-item {:title "Autofill"
                     :onPress (entry-long-press-menu-action el-events/complete-login-autofill)}]

     [rnp-divider]

     [rnp-menu-item {:title (lstr-ml 'entryDetails)
                     :onPress (entry-long-press-menu-action android-af-cmn-events/to-entry-form-page)}]]))

(defn row-item []
  (fn [{:keys [title secondary-title icon-id uuid] :as _entry-summary}]
    (let [icon-name (icon-id->name icon-id)]
      [rnp-list-item {:onPress (fn [e]
                                 (show-entry-long-press-menu e uuid))
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

;; https://github.com/OneKeePass/mobile/issues/50

;; When user tries enter text in searchbar the text feedback is slow with ':value term' in a searchbar props.
;; This appears to be the same issue that was observed in the main app's TextInput use also (entry-form, key-form etc )
;; Not sure this is problem with react native or with reagent use (most probably this is a reagent issue)
;; There we had used ':defaultValue term' instead of ':value term' and that solved the issue.
;; This also worked with 'rnp-searchbar'. But the 'clearIcon' was not showing. 
;; Going through the source code at https://github.com/callstack/react-native-paper/blob/v5.14.5/src/components/Searchbar.tsx#L258
;; We see this
;; const shouldRenderTraileringIcon = isBarMode && traileringIcon &&  !loading &&(!value || right !== undefined);
;; So the 'clearIcon' will not work when we use defaultValue. The solution is to use :right prop or :traileringIcon 
;; Using :right (fn [..]), put the icon to the left instead of right - not sure why?
;; Using traileringIcon with onTraileringIconPress works and input text entry is not sluggish and also we can clear the text entered if required

(defn searchbar []
  (let [term @(android-af-cmn-events/search-term)]
    [rn-view {:margin-top 10}
     [rnp-searchbar {;; clearIcon mostly visible when value has some vlaue
                     ;; :clearIcon "close"
                     :style {:margin-left 1
                             :margin-right 1
                             :borderWidth 0}
                     :placeholder "Search"
                     :onChangeText (fn [v]
                                     (android-af-cmn-events/search-term-update v))
                     
                     ;; :value term
                     
                     :defaultValue term
                     :traileringIcon "close"
                     :onTraileringIconPress (fn [_e]
                                              (android-af-cmn-events/search-term-update nil))

                     ;; This put the icon on the left instead of right side
                     ;;  :right (fn [props]
                     ;;           (r/as-element [rnc/rnp-text-input-icon
                     ;;                          {:icon "close"
                     ;;                           :style {:right 0}
                     ;;                           :onPress #(println "Clear is clicked")}])
                     ;;           )
                     }]]))

(defn main-content []
  (let [entry-items @(el-events/selected-entry-items)
        search-entry-items @(android-af-cmn-events/search-result-entry-items)
        term @(android-af-cmn-events/search-term)
        entry-items (if (empty? search-entry-items) entry-items  search-entry-items)
        sections [{:title "Entries"
                   :key "Entries"
                   :data entry-items}]]

    [rn-view
     (when (and (not (str/blank? term)) (empty? search-entry-items))
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
   [rn-view
    [searchbar]
    [rnc/rn-scroll-view {:style {} :contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}
     [main-content]]]

   [rnp-portal
    [entry-long-press-menu]]])

