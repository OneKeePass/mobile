(ns onekeepass.mobile.merging
  (:require
   [onekeepass.mobile.common-components :as cc :refer [list-header]]
   [onekeepass.mobile.constants :as const]
   [onekeepass.mobile.rn-components
    :as rnc
    :refer [cust-dialog
            primary-color
            page-background-color
            rn-safe-area-view
            rn-scroll-view
            rn-section-list
            rn-view
            rnp-button rnp-dialog-actions rnp-dialog-content
            rnp-dialog-title rnp-divider
            rnp-surface
            rnp-list-icon rnp-list-item
            rnp-portal  rnp-text]]

   [onekeepass.mobile.start-page :as start-page]

   [onekeepass.mobile.translation :refer [lstr-bl lstr-l lstr-dlg-title]]
   [onekeepass.mobile.events.dialogs :as dlg-events]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.merging :as merging-events]
   [reagent.core :as r]))


;;;;;;;;

(defn- merge-result-row-item [{:keys [display-name items-count]}]
  [rnp-list-item {;; We do set onPress 
                  ;; :onPress nil ;; This also works

                  :title (r/as-element
                          [rnp-text {:style {:color  @primary-color}
                                     :variant "titleMedium"} display-name])
                  :left nil

                  :right (fn [_props] (r/as-element
                                       [rnp-text {:variant "titleMedium"} items-count]))}])

(defn merge-result-content [merge-result]
  (let [{:keys [added-entries
                updated-entries
                added-groups
                updated-groups
                parent-changed-entries
                parent-changed-groups
                meta-data-changed
                permanently-deleted-entries
                permanently-deleted-groups]}  merge-result
        sections  [{:title nil
                    :key "MergeResult"
                    ;; dbs-opened is a list of map 
                    ;; forms the data for this list
                    :data [{:display-name "Added entries"
                            :items-count (count added-entries)}

                           {:display-name "Added groups"
                            :items-count (count added-groups)}

                           {:display-name "Entries updated"
                            :items-count (count updated-entries)}

                           {:display-name "groups updated"
                            :items-count (count updated-groups)}

                           {:display-name "Entries moved"
                            :items-count (count parent-changed-entries)}

                           {:display-name "Groups moved"
                            :items-count (count parent-changed-groups)}

                           {:display-name "Entries deleted"
                            :items-count (count permanently-deleted-entries)}

                           {:display-name "Groups deleted"
                            :items-count (count permanently-deleted-groups)}

                           {:display-name "Meta data changed"
                            :items-count  (if meta-data-changed "Yes" "No")}]}]]

    [rn-scroll-view {:style {} :contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}

     [rn-section-list
      {:style {}
       ;; Needs this when we use rn-section-list inside a rn-scroll-view
       :scrollEnabled false
       :sections (clj->js sections)
       :renderItem  (fn [props] ;; keys are (:item :index :section :separators)
                      (let [props (js->clj props :keywordize-keys true)]
                        ;; (-> props :item) returns a map
                        (r/as-element [merge-result-row-item (-> props :item)])))
       :ItemSeparatorComponent (fn [_p]
                                 (r/as-element [rnp-divider]))
       :renderSectionHeader nil #_(fn [props] ;; key is :section
                                    (let [props (js->clj props :keywordize-keys true)
                                          {:keys [title]} (-> props :section)]
                                      (r/as-element [list-header title])))}]

     #_[rn-view {:style {:flexDirection "column" :margin-top 10 :margin-bottom 10 :align-content "center"}}
        [rn-section-list
         {:style {}
          ;; Needs this when we use rn-section-list inside a rn-scroll-view
          :scrollEnabled false
          :sections (clj->js sections)
          :renderItem  (fn [props] ;; keys are (:item :index :section :separators)
                         (let [props (js->clj props :keywordize-keys true)]
                           ;; (-> props :item) returns a map
                           (r/as-element [merge-result-row-item (-> props :item)])))
          :ItemSeparatorComponent (fn [_p]
                                    (r/as-element [rnp-divider]))
          :renderSectionHeader nil}]]]))

(defn merge-result-dialog [{:keys [dialog-show data]}]
  [cust-dialog {:style {} :dismissable false :visible dialog-show :onDismiss #()}
   [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1} (lstr-dlg-title "mergeResults")]
   [rnp-dialog-content {:style {:min-height 100}}
    [merge-result-content data]]
   [rnp-dialog-actions
    [rnp-button {:mode "text"
                 :onPress (fn []
                            (dlg-events/merge-result-dialog-close)
                            (cmn-events/to-previous-page))}
     (lstr-bl "close")]]])

;;;;;;;;

(defn- row-item [{:keys [db-key file-name] :as _db-opened}]
  [rnp-list-item {:style {}
                  :onPress #(merging-events/merging-databases-source-db-selected db-key)
                  :title (r/as-element
                          [rnp-text {:style {:color  @primary-color}
                                     :variant "titleMedium"} file-name])
                  :left (fn [_props]
                          (r/as-element
                           [rnp-list-icon {:style {:height 24}
                                           :icon const/ICON-DATABASE-EYE
                                           :color @rnc/tertiary-color}]))

                  :right nil}])

(defn- opened-dbs-list-content [dbs-opened-list]
  (let [sections  [{:title (lstr-l "openedDatabases")
                    :key "Merging"
                    ;; dbs-opened is a list of map 
                    ;; forms the data for this list
                    :data (if-not (nil? dbs-opened-list) dbs-opened-list [])}]]
    [rn-section-list
     {:style {}
      :sections (clj->js sections)
      :renderItem  (fn [props] ;; keys are (:item :index :section :separators)
                     (let [props (js->clj props :keywordize-keys true)]
                       ;; (-> props :item) returns a map
                       (r/as-element [row-item (-> props :item)])))
      :ItemSeparatorComponent (fn [_p]
                                (r/as-element [rnp-divider]))
      :renderSectionHeader (fn [props] ;; key is :section
                             (let [props (js->clj props :keywordize-keys true)
                                   {:keys [title]} (-> props :section)]
                               (r/as-element [list-header title])))}]))

(defn- content []
  (let [dbs @(merging-events/merging-databases-opened-db-list)]
    [rn-view {:style {:height "100%" :padding 5}}
     [rn-view {:style {:margin-top 20 :margin-bottom 20 :align-items "center"}}
      [rnp-text {:style {:margin-top 10   :textAlign "justify"}}
       "You are going to merge into the database"]

      [rnp-text {:style {:margin-top 10  :textAlign "justify"} :variant "bodyLarge"}
       @(merging-events/merging-target-db-file-name)]

      [rnp-surface {:style {:padding 10 :margin-top 10} :elevation 1}
       [rnp-text {:style {:color @rnc/error-color}} "Please keep a backup copy of this database before merging.The merged database will be saved on successful merging."]]]

     [rnp-divider]
     [rn-view {:style {:margin-top 20 :margin-bottom 20 :align-items "center"}}
      [rnp-button {:style {:margin-bottom 5 :margin-top 5}
                   :labelStyle {:fontWeight "bold" :fontSize 15}
                   :mode "text"
                   :on-press merging-events/merging-databases-open-new-db-source-start}
       "Open a source database"]]

     (when-not (empty? dbs)
       [:<>
        [rnp-divider]
        [rn-view {:style {:padding 5 :align-items "center"}} ;;:height "100%" 
         [rnp-text {:style {:margin-top 3  :textAlign "justify"}} "Or"]
         [rnp-text {:style {:margin-top 3 :margin-bottom 3 :textAlign "justify"}} "Select one of the following opened databases as a source database"]]

        [opened-dbs-list-content dbs]])]))

(defn main-content []
  [rn-safe-area-view {:style {:flex 1 :background-color @rnc/page-background-color}}
   [content]
   [rnp-portal
    [merge-result-dialog @(dlg-events/merge-result-dialog-data)]
    [start-page/start-page-storage-selection-dialog]
    [start-page/open-db-dialog]
    [cc/message-dialog]]])