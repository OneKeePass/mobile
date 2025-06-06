(ns onekeepass.mobile.rs-files-folders
  "Shows the list of files and folders found for a remote connection"
  (:require
   [onekeepass.mobile.common-components :refer [list-section-header
                                                message-dialog]]
   [onekeepass.mobile.constants :as const ]
   [onekeepass.mobile.events.remote-storage :as rs-events]
   [onekeepass.mobile.rn-components :as rnc :refer [page-background-color
                                                    rn-safe-area-view
                                                    rn-section-list rn-view
                                                    rnp-divider rnp-fab
                                                    rnp-list-icon
                                                    rnp-list-item rnp-portal
                                                    rnp-text]]
   [onekeepass.mobile.start-page :refer [open-db-dialog]]
   [onekeepass.mobile.translation :refer [lstr-bl]]
   [reagent.core :as r]))

(defn row-item []
  (fn [connection-id parent-dir {:keys [entry-name is-dir]} browse-type]
    (let [color @rnc/secondary-color
          file-selection-disbled (and (not is-dir) (= browse-type const/BROWSE-TYPE-DB-NEW))
          disabled-color (if file-selection-disbled "grey" color)]
      [rnp-list-item {:style {}
                      :disabled file-selection-disbled
                      :onPress (fn []
                                 (if is-dir
                                   (rs-events/remote-storage-sub-dir-listing-start connection-id parent-dir entry-name)
                                   (rs-events/remote-storage-file-picked connection-id parent-dir entry-name)))
                      :title (r/as-element
                              [rnp-text {:style {:color disabled-color}
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
        {:keys [parent-dir sub-dirs files]} :dir-entries} browse-type]
    (let [all-entries-m (combine-entries sub-dirs files)
          sections  [{:title "allItems"
                      :key "AllItems"
                      :data all-entries-m}]]

      ;; Need to use "100%" if we use rn-section-list inside a rn-view to cover whole height with scroll
      ;; Need to use height "90%" if we use rnp-button instead of rnp-fab
      [rn-view {:height "100%"}
       [rn-section-list
        {:style {}
         :sections (clj->js sections)
         :renderItem  (fn [props] ;; keys are (:item :index :section :separators)
                        (let [props (js->clj props :keywordize-keys true)]
                          (r/as-element [row-item connection-id parent-dir (-> props :item) browse-type])))
         :ItemSeparatorComponent (fn [_p]
                                   (r/as-element [rnp-divider]))
         :renderSectionHeader (fn [props] ;; key is :section
                                (let [props (js->clj props :keywordize-keys true)
                                      {:keys [title]} (-> props :section)]
                                  (r/as-element [list-section-header title])))}]
       #_(when (= browse-type const/BROWSE-TYPE-DB-NEW)
           [rn-view {:bottom 10}
            [rnp-button  "Select this folder"]])])))

(defn dir-entries-content []
  [rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}
   (let [browse-type @(rs-events/remote-storage-browse-rs-type)
         {:keys [:connection-id dir-entries] :as listings} @(rs-events/remote-storage-listing-to-show)]
     [rn-view
      [list-content listings browse-type]
      (when (= browse-type const/BROWSE-TYPE-DB-NEW)
        [rnp-fab {:style {:position "absolute"
                          :margin 16
                          :right 0
                          :align-self "center"
                          :width 200
                          :bottom (rnc/get-inset-bottom)}
                  :on-press (fn [] (rs-events/remote-storage-folder-picked-for-new-db-file connection-id dir-entries))
                  :mode "flat"
                  :label (lstr-bl 'selectFolder)}]
        ;; We can use rnp-button inside a rn-view and then 'rn-view' used in  list-content
        ;; should have height "90%"
        #_[rn-view {:bottom 0 :background-color "red"}
           [rnp-button  "Select this folder..3"]])])

   [rnp-portal
    [open-db-dialog]
    [message-dialog]]])