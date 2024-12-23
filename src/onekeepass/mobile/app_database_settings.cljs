(ns onekeepass.mobile.app-database-settings
  (:require
   [onekeepass.mobile.common-components :refer [settings-section-header]]
   [onekeepass.mobile.rn-components :as rnc :refer [page-background-color
                                                    rn-safe-area-view
                                                    rn-section-list rn-view
                                                    rnp-divider rnp-switch
                                                    rnp-text]]
   [reagent.core :as r]))


(def box-style-1 {:padding-right 5
                  :padding-left 5
                  :margin 5
                  :border-color @rnc/primary-color
                  :borderWidth .20
                  :borderRadius 4})

(defn  row-item []
  (fn [_m]
    (let [enabled? true
          text "Enable/Disable"]

      [rn-view  {:style {}}
       [rn-view {:style {:flexDirection "row" :min-height 50 :justify-content "space-between"}}
        [rnp-text {:style {:align-self "center" :padding-left 5} :variant "titleMedium"} text]
        [rn-view {:style {:padding-right 10 :align-self "center"}}
         [rnp-switch {:style {:align-self "center"}
                      :value enabled?
                      :onValueChange (fn []
                                       #_(if enabled?
                                         (af-events/ios-delete-copied-autofill-details)
                                         (af-events/ios-copy-file-to-group)))}]]]

       (if-not enabled?
         [rn-view {:style (merge box-style-1 {:margin-top 20})}
          [rnp-text {:style {:padding-top 20 :padding-bottom 20} :variant "titleSmall"}
           "1. You need to enable this to open database using FaceID"]]

         [rn-view {:style (merge box-style-1 {:margin-top 20})}
          [rnp-text {:style {:padding-top 20 :padding-bottom 20} :variant "titleSmall"}
           "1. This database is ready to open with FaceID"]
          [rnp-divider {:style {}}]
          [rnp-text {:style {:padding-top 20  :padding-bottom 20} :variant "titleSmall"} 
           "2. You need to open the database onetime with the usual credentials and then the use FaceId will be active"]])])))

(defn settings-list-content []
  (let  [sections [{:title "biometric"
                    :key "biometric"
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
                                                (r/as-element [settings-section-header title])))}]))

(defn- main-content []
  [rn-view {:flex 1 :backgroundColor @page-background-color}
   [rn-view
    [settings-list-content]]])


(defn content []
  [rn-safe-area-view {:style {:flex 1 :backgroundColor @page-background-color}}
   [main-content]])