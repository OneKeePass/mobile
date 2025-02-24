(ns onekeepass.mobile.app-database-settings
  "Additional database Settings"
  (:require
   [onekeepass.mobile.background :refer [is-iOS]]
   [onekeepass.mobile.common-components :refer [settings-section-header]]
   [onekeepass.mobile.events.app-database-settings :as ada-events]
   [onekeepass.mobile.events.common :refer [active-db-key
                                            biometric-enabled-to-open-db
                                            biometric-enabled-to-unlock-db]]
   [onekeepass.mobile.rn-components :as rnc :refer [page-background-color
                                                    rn-safe-area-view
                                                    rn-section-list rn-view
                                                    rnp-divider rnp-switch
                                                    rnp-text]]
   [onekeepass.mobile.translation :refer [lstr-l]]
   [reagent.core :as r]))

(def db-open-id "1")

(def db-unlock-id "2")

(defn- row-item [_m]
  (fn [{:keys [title] :as _item}]
    (let [db-key @(active-db-key)
          enabled? (if (= title db-open-id)
                     @(biometric-enabled-to-open-db db-key)
                     @(biometric-enabled-to-unlock-db db-key))
          text (if (is-iOS)
                 (lstr-l 'faceId)
                 (lstr-l 'faceUnlockFingerprint))]
      [rn-view  {:style {:margin-top 10 :margin-bottom 10}}
       [rn-view {:style {:flexDirection "row" :min-height 50 :justify-content "space-between"}}
        [rnp-text {:style {:align-self "center" :padding-left 5} :variant "titleMedium"} text]
        [rn-view {:style {:padding-right 10 :align-self "center"}}
         [rnp-switch {:style {:align-self "center"}
                      :value enabled?
                      :onValueChange (fn []
                                       (if (= title db-open-id)
                                         (ada-events/set-db-open-biometric-enabled (not enabled?))
                                         (ada-events/set-db-unlock-biometric-enabled (not enabled?))))}]]]
       #_(if-not enabled?
           [rn-view {:style (merge box-style-1 {:margin-top 20})}
            [rnp-text {:style {:padding-top 20 :padding-bottom 20} :variant "titleSmall"}
             "1. You need to enable this to open database using FaceID"]]

           [rn-view {:style (merge box-style-1 {:margin-top 20})}
            [rnp-text {:style {:padding-top 20 :padding-bottom 20} :variant "titleSmall"}
             "1. This database is ready to open with FaceID"]
            [rnp-divider {:style {}}]
            [rnp-text {:style {:padding-top 20  :padding-bottom 20} :variant "titleSmall"}
             "2. You need to open the database onetime with the usual credentials and then the use FaceId will be active"]])])))

(defn- settings-list-content []
  (let  [sections [{:title "databaseOpen"
                    :key "databaseOpen"
                    :data [{:title db-open-id}]}

                   {:title "databaseUnlock"
                    :key "databaseUnlock"
                    :data [{:title db-unlock-id}]}]]
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

;; This page is called from onekeepass.mobile.settings
(defn content
  "Provides content when user wants to set additional database settings like FaceID enable/disable"
  []
  [rn-safe-area-view {:style {:flex 1 :backgroundColor @page-background-color}}
   [main-content]])