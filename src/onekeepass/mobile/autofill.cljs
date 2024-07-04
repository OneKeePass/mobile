(ns onekeepass.mobile.autofill
  (:require [onekeepass.mobile.constants :refer [TR-KEY-AUTOFILL]]
            [onekeepass.mobile.events.autofill :as af-events]
            [onekeepass.mobile.rn-components :as rnc :refer [inverse-onsurface-color
                                                             page-background-color
                                                             rn-safe-area-view
                                                             rn-section-list
                                                             rn-view
                                                             rnp-divider
                                                             rnp-switch
                                                             rnp-text]]
            [onekeepass.mobile.translation :refer [lstr-l lstr-mt]]
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
                      :padding-left 5} :variant "titleSmall"} (lstr-l title)]])

(def box-style-1 {:padding-right 5
                  :padding-left 5 
                  :margin 5
                  :border-color @rnc/primary-color
                  :borderWidth .20
                  :borderRadius 4})

(defn  row-item []
  (fn [_m]
    (let [info @(af-events/ios-autofill-db-info)
          enabled? (if (nil? info) false true)
          text (if enabled? (lstr-l 'enabled)  (lstr-mt TR-KEY-AUTOFILL 'enableMsg1))]

      [rn-view  {:style {}}
       [rn-view {:style {:flexDirection "row" :min-height 50 :justify-content "space-between"}}
        [rnp-text {:style {:align-self "center" :padding-left 5} :variant "titleMedium"} text]
        [rn-view {:style {:padding-right 10 :align-self "center"}}
         [rnp-switch {:style {:align-self "center"}
                      :value enabled?
                      :onValueChange (fn []
                                       (if enabled?
                                         (af-events/ios-delete-copied-autofill-details)
                                         (af-events/ios-copy-file-to-group)))}]]]

       (if-not enabled?
         [rn-view {:style (merge box-style-1 {:margin-top 20})}
          [rnp-text {:style {:padding-top 20 :padding-bottom 20} :variant "titleSmall"}
           (str "1. " (lstr-mt TR-KEY-AUTOFILL 'enableMsg2))
           #_"1. You need to enable this database to use in autofill"
           ]
          #_[rnp-divider {:style {}}]
          #_[rnp-text {:style {:padding-top 20  :padding-bottom 20} :variant "titleSmall"}
             "2. Also select OneKeePass as your preferred provider in the iOS System Settings"]]

         [rn-view {:style (merge box-style-1 {:margin-top 20})}
          [rnp-text {:style {:padding-top 20 :padding-bottom 20} :variant "titleSmall"}
           (str "1. " (lstr-mt TR-KEY-AUTOFILL 'enableMsg3))
           #_"1. This database is ready to use in autofill"
           ]
          [rnp-divider {:style {}}]
          [rnp-text {:style {:padding-top 20  :padding-bottom 20} :variant "titleSmall"}
           (str "2. " (lstr-mt TR-KEY-AUTOFILL 'enableMsg4))
           #_"2. Also select OneKeePass as your preferred provider in the iOS System Settings"]])])))

(defn settings-list-content []
  (let  [sections [{:title "databaseAutoFill"
                    :key "databaseAutoFill"
                    :data [{:title "not used"}]}]]
    [rn-section-list  {:style {}
                       :sections (clj->js sections)
                       :renderItem  (fn [props]
                                          ;; keys are (:item :index :section :separators)
                                          ;; (-> props :item) gives the map corresponding to each member of the vec in :data
                                          ;; (-> props :section :key) gives section key value
                                      (let [props (js->clj props :keywordize-keys true)]
                                        (r/as-element [row-item (-> props :item)])))
                       :ItemSeparatorComponent (fn [_p]
                                                 (r/as-element [rnp-divider]))
                       :renderSectionHeader (fn [props]
                                              (let [props (js->clj props :keywordize-keys true)
                                                    {:keys [title]} (-> props :section)]
                                                (r/as-element [section-header title])))}]))

(defn- main-content []
  [rn-view {:flex 1 :backgroundColor @page-background-color}
   [rn-view
    [settings-list-content]]])


(defn content []
  [rn-safe-area-view {:style {:flex 1 :backgroundColor @page-background-color}}
   [main-content]])