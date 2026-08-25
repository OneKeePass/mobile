(ns onekeepass.mobile.rs-files-folders
  "Shows the list of files and folders found for a remote connection"
  (:require
   [onekeepass.mobile.common-components :refer [confirm-dialog
                                                list-section-header
                                                message-dialog]]
   [onekeepass.mobile.constants :as const ]
   [onekeepass.mobile.events.dialogs :as dlg-events]
   [onekeepass.mobile.events.remote-storage :as rs-events]
   [onekeepass.mobile.rn-components :as rnc :refer [cust-dialog
                                                    no-autocorrect-text-props
                                                    page-background-color
                                                    rn-safe-area-view
                                                    rn-section-list rn-view
                                                    rnp-button
                                                    rnp-dialog-actions
                                                    rnp-dialog-content
                                                    rnp-dialog-title
                                                    rnp-divider rnp-fab
                                                    rnp-helper-text
                                                    rnp-list-icon
                                                    rnp-list-item rnp-portal
                                                    rnp-text rnp-text-input]]
   [onekeepass.mobile.start-page :refer [open-db-dialog]]
   [onekeepass.mobile.translation :refer [lstr-bl lstr-dlg-text lstr-dlg-title lstr-l]]
   [reagent.core :as r]))

(defn row-item []
  (fn [connection-id parent-dir {:keys [entry-name is-dir]} browse-type]
    (let [color @rnc/secondary-color
          file-selection-disbled (and (not is-dir) (const/folder-selection-browse-type? browse-type))
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

(defn save-as-file-name-dialog
  "Asks the user to confirm the name of the file to write when a folder is selected
   for the 'Save As' action. The name is prefilled with a name that does not clash
   with the files already found in the selected folder"
  ([{:keys [dialog-show file-name error-fields]}]
   (when dialog-show
     (let [error-text (:file-name error-fields)]
       [cust-dialog {:style {} :dismissable false :visible dialog-show :onDismiss #()}
        [rnp-dialog-title (lstr-dlg-title 'saveAs)]
        [rnp-dialog-content
         [rn-view {:flexDirection "column"}
          [rnp-text-input (merge no-autocorrect-text-props
                                 {:label (lstr-l 'databaseFile)
                                  :defaultValue file-name
                                  :onChangeText #(dlg-events/save-as-file-name-dialog-update [:file-name %])})]
          (when error-text
            [rnp-helper-text {:type "error" :visible true} error-text])]]

        [rnp-dialog-actions
         [rnp-button {:mode "text"
                      :onPress dlg-events/save-as-file-name-dialog-close} (lstr-bl 'cancel)]
         [rnp-button {:mode "text"
                      :onPress (fn []
                                 (rs-events/remote-storage-save-as-name-confirmed file-name))} (lstr-bl 'ok)]]])))
  ([]
   (save-as-file-name-dialog @(dlg-events/save-as-file-name-dialog-data))))

(defn save-as-file-exists-dialog
  "Writing to the remote storage replaces any file that is already there and so the
   user is asked to confirm first. The device's document picker asks this on its own"
  []
  (let [{:keys [dialog-show file-name]} @(dlg-events/save-as-file-exists-dialog-data)]
    [confirm-dialog {:dialog-show dialog-show
                     :title (lstr-dlg-title 'saveAsFileExists)
                     :confirm-text (lstr-dlg-text 'saveAsFileExists)
                     :actions [{:label (lstr-bl "cancel")
                                :on-press (fn []
                                            (rs-events/remote-storage-save-as-existing-file-name-reentered
                                             file-name))}

                               {:label (lstr-bl "overwrite")
                                :on-press (fn []
                                            (rs-events/remote-storage-save-as-overwrite-confirmed
                                             file-name))}]}]))

(defn dir-entries-content []
  [rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}
   (let [browse-type @(rs-events/remote-storage-browse-rs-type)
         {:keys [:connection-id dir-entries] :as listings} @(rs-events/remote-storage-listing-to-show)]
     [rn-view
      [list-content listings browse-type]
      (when (const/folder-selection-browse-type? browse-type)
        [rnp-fab {:style {:position "absolute"
                          :margin 16
                          :right 0
                          :align-self "center"
                          :width 200
                          :bottom (rnc/get-inset-bottom)}
                  :on-press (fn []
                              (if (= browse-type const/BROWSE-TYPE-DB-SAVE-AS)
                                (rs-events/remote-storage-folder-picked-for-save-as connection-id dir-entries)
                                (rs-events/remote-storage-folder-picked-for-new-db-file connection-id dir-entries)))
                  :mode "flat"
                  :label (lstr-bl 'selectFolder)}]
        ;; We can use rnp-button inside a rn-view and then 'rn-view' used in  list-content
        ;; should have height "90%"
        #_[rn-view {:bottom 0 :background-color "red"}
           [rnp-button  "Select this folder..3"]])])

   [rnp-portal
    [open-db-dialog]
    [save-as-file-name-dialog]
    [save-as-file-exists-dialog]
    [message-dialog]]])