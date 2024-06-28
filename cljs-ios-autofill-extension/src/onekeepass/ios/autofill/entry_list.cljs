(ns onekeepass.ios.autofill.entry-list
  (:require [onekeepass.ios.autofill.common-components :refer [menu-action-factory]]
            [onekeepass.ios.autofill.events.common :as cmn-events]
            [onekeepass.ios.autofill.events.entry-list :as el-events]
            [onekeepass.ios.autofill.icons-list :refer [icon-id->name]]
            [onekeepass.ios.autofill.rn-components :as rnc :refer [icon-color
                                                                   page-background-color
                                                                   primary-container-color
                                                                   rn-safe-area-view
                                                                   rn-section-list
                                                                   rn-view
                                                                   rnp-divider
                                                                   rnp-list-icon
                                                                   rnp-menu
                                                                   rnp-portal
                                                                   rnp-menu-item
                                                                   rnp-list-item
                                                                   rnp-text]]
            [reagent.core :as r]))


(def ^:private entry-long-press-menu-data (r/atom
                                           {:show false :entry-summary nil :x 0 :y 0}))

(defn hide-entry-long-press-menu []
  (swap! entry-long-press-menu-data assoc :show false))

#_(defn show-entry-long-press-menu [^js/PEvent event entry-summary]
    (swap! entry-long-press-menu-data assoc
           :show true
           :entry-summary entry-summary
           :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

#_(def entry-long-press-menu-action (menu-action-factory hide-entry-long-press-menu))

#_(defn entry-long-press-menu [{:keys [show x y entry-summary]}]
    [rnp-menu {:visible show :onDismiss hide-entry-long-press-menu :anchor (clj->js {:x x :y y})}
     [rnp-menu-item {:title "Copy username " #_(lstr-ml "putback")
                     :onPress #() #_(entry-long-press-menu-action
                                     md-events/open-putback-dialog (:uuid entry-summary))}]
     [rnp-menu-item {:title  "Copy password" #_(lstr-ml "deletePermanently")
                     :onPress #() #_(entry-long-press-menu-action
                                     md-events/openn-delete-permanent-dialog (:uuid entry-summary))}]]

    #_(let [deleted-cat @(elist-events/deleted-category-showing)]
        (if-not deleted-cat
          [rnp-menu {:visible show :onDismiss hide-entry-long-press-menu :anchor (clj->js {:x x :y y})}
       ;; TODO: Need to add a rust api to toggle an entry as Favorites or not and then enable this 
           #_[rnp-menu-item {:title "Favorites" :onPress #()  :trailingIcon "check"}]
           [rnp-menu-item {:title (lstr-ml "delete")
                           :onPress (entry-long-press-menu-action cc/show-entry-delete-confirm-dialog (:uuid entry-summary))}]
       ;; TDOO: 
       ;; Need to add backend api support to get history count as part of summary
       ;; and use that to enable/disable History menu item
           #_[rnp-divider]
           #_[rnp-menu-item {:title "History"  :onPress #()}]]

          [rnp-menu {:visible show :onDismiss hide-entry-long-press-menu :anchor (clj->js {:x x :y y})}
           [rnp-menu-item {:title (lstr-ml "putback")
                           :onPress (entry-long-press-menu-action
                                     md-events/open-putback-dialog (:uuid entry-summary))}]
           [rnp-menu-item {:title (lstr-ml "deletePermanently")
                           :onPress (entry-long-press-menu-action
                                     md-events/openn-delete-permanent-dialog (:uuid entry-summary))}]])))

(def entry-long-press-menu-action (menu-action-factory el-events/long-press-menu-hide))

(defn show-entry-long-press-menu [^js/PEvent event uuid]
  (el-events/long-press-start (-> event .-nativeEvent .-pageX) (-> event .-nativeEvent .-pageY) uuid))

(defn entry-long-press-menu []
  (let [{:keys [show x y]} @(el-events/entry-list-long-press-data)]
    [rnp-menu {:visible show :onDismiss el-events/long-press-menu-hide :anchor (clj->js {:x x :y y})}
     [rnp-menu-item {:title "Copy username " #_(lstr-ml "putback")
                     :onPress #() #_(entry-long-press-menu-action
                                     md-events/open-putback-dialog (:uuid entry-summary))}]
     [rnp-menu-item {:title  "Copy password" #_(lstr-ml "deletePermanently")
                     :onPress #() #_(entry-long-press-menu-action
                                     md-events/openn-delete-permanent-dialog (:uuid entry-summary))}]
     [rnp-divider]

     [rnp-menu-item {:title "Entry Details"
                     :onPress (entry-long-press-menu-action cmn-events/to-entry-form-page)  }]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn row-item []
  (fn [{:keys [title secondary-title icon-id uuid] :as entry-summary}]
    (let [icon-name (icon-id->name icon-id)]
      [rnp-list-item {:onPress #()
                      :onLongPress (fn [e]
                                     (show-entry-long-press-menu e uuid))    #_#(el-events/find-entry-by-id uuid)
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
   [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleLarge"} title]])

(defn main-content []
  (let [entry-items @(el-events/selected-entry-items)
        search-entry-items @(cmn-events/search-result-entry-items)
        entry-items (if (empty? search-entry-items) entry-items  search-entry-items)
        sections [{:title "Entries"
                   :key "Entries"
                   :data entry-items}]]
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
                                                   {:keys [title data]} (-> props :section)]
                                               (r/as-element [section-header title])))}]))


(defn content []
  [rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}
   [rnc/rn-scroll-view {:style {} :contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}
    [main-content]]
   [rnp-portal
    [entry-long-press-menu]]])