(ns onekeepass.mobile.rs-files-folders
  "Shows the list of files and folders found for a remote connection"
  (:require [onekeepass.mobile.common-components :refer [list-section-header
                                                         message-dialog]]
            [onekeepass.mobile.constants :as const]
            [onekeepass.mobile.events.remote-storage :as rs-events]
            [onekeepass.mobile.rn-components :as rnc :refer [page-background-color
                                                             rn-safe-area-view
                                                             rn-section-list
                                                             rnp-divider
                                                             rnp-list-icon
                                                             rnp-list-item
                                                             rnp-portal
                                                             rnp-text]]
            [onekeepass.mobile.start-page :refer [open-db-dialog]]
            [reagent.core :as r]))




(defn row-item []
  (fn [connection-id parent-dir {:keys [entry-name is-dir]}]
    (let [color @rnc/secondary-color]
      [rnp-list-item {:style {}
                      :onPress (fn []
                                 (if  is-dir
                                   (rs-events/remote-storage-sub-dir-listing-start connection-id parent-dir entry-name)
                                   (rs-events/remote-storage-file-picked connection-id parent-dir entry-name)))
                      :title (r/as-element
                              [rnp-text {:style {:color color}
                                         :variant "titleMedium"} entry-name])
                      :left (fn [_props]
                              (r/as-element
                               (if is-dir
                                 [rnp-list-icon {:style {:height 24}
                                                 :icon const/ICON-FOLDER
                                                 :color color}]
                                 [rnp-list-icon {:style {:height 24}
                                                 :icon const/ICON-FILE-OUTLINE
                                                 :color color}])))
                      :right (when is-dir
                               (fn [_props] (r/as-element [rnp-list-icon {:icon const/ICON-CHEVRON-RIGHT}])))}])))

(defn combine-entries
  "Combines two vec of dir entry map and returns a single 
   vec of a map with keys :entry-name :is-dir"
  [sub-dirs files]
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
       {:style {}
        :sections (clj->js sections)
        :renderItem  (fn [props] ;; keys are (:item :index :section :separators)
                       (let [props (js->clj props :keywordize-keys true)]
                         (r/as-element [row-item connection-id parent-dir (-> props :item)])))
        :ItemSeparatorComponent (fn [_p]
                                  (r/as-element [rnp-divider]))
        :renderSectionHeader (fn [props] ;; key is :section
                               (let [props (js->clj props :keywordize-keys true)
                                     {:keys [title]} (-> props :section)]
                                 (r/as-element [list-section-header title])))}])))

(defn dir-entries-content []
  [rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}
   [list-content @(rs-events/remote-storage-listing-to-show)]
   [rnp-portal
    [open-db-dialog]
    [message-dialog]]])