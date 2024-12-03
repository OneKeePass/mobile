(ns onekeepass.mobile.rs-configs
  "Remote storage configs page showing list of all for sftp or webdav conenctions"
  (:require [onekeepass.mobile.constants :as const]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.events.remote-storage :as rs-events]
            [onekeepass.mobile.rn-components
             :as rnc
             :refer [appbar-text-color page-title-text-variant
                     cust-dialog cust-rnp-divider divider-color-1
                     dots-icon-name primary-color primary-container-color
                     rn-keyboard rn-safe-area-view rn-section-list rn-view
                     rnp-button rnp-dialog-actions rnp-dialog-content
                     rnp-dialog-title rnp-divider rnp-helper-text
                     rnp-icon-button rnp-list-icon rnp-list-item rnp-menu
                     rnp-menu-item rnp-portal rnp-progress-bar rnp-text
                     rnp-text-input rnp-text-input-icon]]
            [onekeepass.mobile.common-components :refer [list-section-header]]
            [onekeepass.mobile.translation :refer [lstr-bl lstr-l lstr-dlg-text
                                                   lstr-dlg-title lstr-ml]]
            [onekeepass.mobile.utils :as u]
            
            [reagent.core :as r]))


(defn appbar-title [] 
  [rn-view {:flexDirection "row"
            :style {:alignItems "center"
                    :justify-content "space-between"}}
   [rnp-button {:style {}
                :textColor @appbar-text-color
                :mode "text"
                :onPress cmn-events/to-previous-page} "Cancel"]
   [rnp-text {:style {:color @appbar-text-color
                      :max-width 200
                      :margin-right 10 :margin-left 10}
              :ellipsizeMode "tail"
              :numberOfLines 1
              :variant page-title-text-variant} "Select Connection"]
   [rnp-button {:style {}
                :textColor @appbar-text-color

                :mode "text"
                :onPress rs-events/remote-storage-rs-type-new-form-page-show} "Add"]])


(defn row-item []
  (fn [{:keys [name connection-id]} data]
    (let [selected-type-kw @(rs-events/remote-storage-current-rs-type)
          [icon-name color] [const/ICON-DATABASE-ARROW-LEFT @rnc/tertiary-color]]
      [rnp-list-item {:style {}
                      :onPress (fn [] (rs-events/connect-by-id-and-retrieve-root-dir selected-type-kw connection-id ) )
                      :title (r/as-element
                              [rnp-text {:style {:color color}
                                         :variant "titleMedium"} name])
                      :left (fn [_props]
                              (r/as-element
                               [rnp-list-icon {:style {:height 24}
                                               :icon icon-name
                                               :color color}]))
                      :right (fn [_props] (r/as-element
                                           [:<> ;; We can add another icon here if required
                                            [rnp-icon-button
                                             {:size 24
                                              :style {:margin -10}
                                              :icon dots-icon-name
                                              :onPress #()}]]))}])))


(defn connections-list-content []
  (fn [connections]
    (let [sections  [{:title (lstr-l "databases")
                      :key "Databases"
                      ;; Connetions info forms the data for this list
                      :data (if (nil? connections) [] connections)}]] 
      [rn-section-list
       {:style {}
        :sections (clj->js sections)
        :renderItem  (fn [props] ;; keys are (:item :index :section :separators)
                       (let [props (js->clj props :keywordize-keys true)]
                         (r/as-element [row-item (-> props :item)])))
        :ItemSeparatorComponent (fn [_p]
                                  (r/as-element [rnp-divider]))
        :renderSectionHeader (fn [props] ;; key is :section
                               (let [props (js->clj props :keywordize-keys true)
                                     {:keys [title]} (-> props :section)]
                                 (r/as-element [list-section-header title])))}])))



(defn remote-connections-list-page-content [] 
  (let [selected-type-kw @(rs-events/remote-storage-current-rs-type)
        connections @(rs-events/remote-storage-connection-configs selected-type-kw)]
    #_(println "In remote-connections-list-page-content selected-type-kw" selected-type-kw "\n connections " connections )
    [rn-safe-area-view {:style {:flex 1 :background-color @rnc/page-background-color}}
     [rn-view {:style {:flex 1 :justify-content "center" :align-items "center" :margin-top "10%"}}

      [rn-view {:style {:flex 1 :width "100%"}}
       [connections-list-content connections]]]]))