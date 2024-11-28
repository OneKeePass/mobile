(ns onekeepass.mobile.rs-files-folders
  "Shows the list of files and folders found for a remote connection"
  (:require [onekeepass.mobile.common-components :refer [list-section-header]]
            [onekeepass.mobile.constants :as const]
            [onekeepass.mobile.rn-components :as rnc :refer [dots-icon-name
                                                             page-background-color
                                                             rn-safe-area-view
                                                             rn-section-list
                                                             rnp-divider
                                                             rnp-icon-button
                                                             rnp-list-icon
                                                             rnp-list-item
                                                             rnp-text]]
            [reagent.core :as r]))


#_(defn appbar-title []
  (println "RS appbar-title called")
  [rn-view {:flexDirection "row"
            :style {:alignItems "center"
                    :justify-content "space-between"}}
   [rnp-button {:style {}
                :textColor @appbar-text-color
                :mode "text"
                :onPress cmn-events/to-previous-page} "Cancel"]
   [rnp-text {:style {:color @appbar-text-color
                      :max-width 100
                      :margin-right 20 :margin-left 20}
              :ellipsizeMode "tail"
              :numberOfLines 1
              :variant page-title-text-variant} "Select Connection"]
   [rnp-button {:style {}
                :textColor @appbar-text-color

                :mode "text"
                :onPress #()} "Add"]])


(defn row-item []
  (fn [connection-id parent-dir {:keys [entry-name is-dir]}]
    (let [[icon-name color] [const/ICON-FILE @rnc/tertiary-color]]
      [rnp-list-item {:style {}
                      :onPress #()
                      :title (r/as-element
                              [rnp-text {:style {:color color}
                                         :variant "titleMedium"} entry-name])
                      :left (fn [_props]
                              (r/as-element
                               [rnp-list-icon {:style {:height 24}
                                               :icon icon-name
                                               :color color}]))
                      :right (when is-dir
                               (fn [_props] (r/as-element
                                             [:<> ;; We can add another icon here if required
                                              [rnp-icon-button
                                               {:size 24
                                                :style {:margin -10}
                                                :icon dots-icon-name
                                                :onPress #()}]])))}])))

(defn combine-entries [sub-dirs files]
  (let [dirs (reduce (fn [acc d] (merge acc {:entry-name d :is-dir true})) [] sub-dirs)
        all (reduce (fn [acc d] (merge acc {:entry-name d :is-dir false})) dirs files)]
    all))

(defn list-content []
  (fn [{:keys [connection-id]
        {:keys [parent-dir sub-dirs files]} :dir-entries}]
    (let [all-entries-m (combine-entries sub-dirs files)
          sections  [{:title "All Items" #_(lstr-l "databases")
                      :key "AllItems"
                      :data all-entries-m}]]
      [rn-section-list
       :style {}
       :sections (clj->js sections)
       :renderItem  (fn [props] ;; keys are (:item :index :section :separators)
                      (let [props (js->clj props :keywordize-keys true)]
                        (r/as-element [row-item connection-id parent-dir (-> props :item)])))
       :ItemSeparatorComponent (fn [_p]
                                 (r/as-element [rnp-divider]))

       :renderSectionHeader (fn [props] ;; key is :section
                              (let [props (js->clj props :keywordize-keys true)
                                    {:keys [title]} (-> props :section)]
                                (r/as-element [list-section-header title])))])))


(defn dir-entries-content []
  [rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}
   [list-content]])