(ns onekeepass.mobile.entry-list
  (:require [onekeepass.mobile.background :refer [is-Android]]
            [onekeepass.mobile.common-components :as cc :refer [confirm-dialog
                                                                menu-action-factory
                                                                select-field]]
            [onekeepass.mobile.constants :as const :refer [ICON-CHECKBOX-BLANK-OUTLINE
                                                           ICON-CHECKBOX-OUTLINE]]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.events.entry-category :as ecat-events]
            [onekeepass.mobile.events.entry-list :as elist-events :refer [find-entry-by-id]]
            [onekeepass.mobile.events.move-delete :as md-events]
            [onekeepass.mobile.events.dialogs :as dlg-events]
            [onekeepass.mobile.icons-list :refer [icon-id->name]]
            [onekeepass.mobile.rn-components :as rnc :refer [cust-dialog
                                                             icon-color
                                                             page-background-color
                                                             primary-container-color
                                                             rn-safe-area-view
                                                             rn-section-list
                                                             rn-view
                                                             ;;rnp-bottom-navigation-bar
                                                             rnp-button
                                                             rnp-dialog-actions
                                                             rnp-dialog-content
                                                             rnp-dialog-title
                                                             rnp-divider
                                                             rnp-helper-text
                                                             rnp-icon-button
                                                             rnp-list-icon
                                                             rnp-list-item
                                                             rnp-menu
                                                             rnp-menu-item
                                                             rnp-text]]
            [onekeepass.mobile.utils :as u]
            [onekeepass.mobile.translation :refer [lstr-bl lstr-cv
                                                   lstr-dlg-text
                                                   lstr-dlg-title lstr-l
                                                   lstr-ml]]
            [reagent.core :as r]))

;;;;;;;;;;; Menus ;;;;;;;;;;;;;;
(def ^:private fab-action-menu-data (r/atom {:show false :x 0 :y 0 :selected-category-key nil}))

(defn hide-fab-action-menu []
  (swap! fab-action-menu-data assoc :show false))

(defn show-fab-action-menu [^js/PEvent event selected-category-key selected-category-detail]
  (swap! fab-action-menu-data assoc
         :selected-category-detail selected-category-detail
         :selected-category-key selected-category-key
         :show true
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

;; If we do not hide the menu explicitly, 
;; when entry form is canceled or closed, the appbar backaction onPress fails
;; This explicit hide fixes that issue
;; This pattern is followed in all menu press handling
(def fab-menu-action (menu-action-factory hide-fab-action-menu))

(defn fab-action-menu [{:keys [show x y selected-category-key selected-category-detail]}]
  [rnp-menu {:visible show :onDismiss hide-fab-action-menu :anchor (clj->js {:x x :y y})}
   [rnp-menu-item {:title (lstr-ml "addEntry")
                   :onPress (fab-menu-action elist-events/add-entry)}]
   (when (= const/GROUP_SECTION_TITLE selected-category-key)
     [rnp-menu-item {:title (lstr-ml "addGroup")
                     :onPress (fab-menu-action elist-events/add-group (:uuid selected-category-detail))}])])

(def ^:private entry-long-press-menu-data (r/atom
                                           {:show false :entry-summary nil :x 0 :y 0}))

(defn hide-entry-long-press-menu []
  (swap! entry-long-press-menu-data assoc :show false))

(defn show-entry-long-press-menu [^js/PEvent event entry-summary]
  (swap! entry-long-press-menu-data assoc
         :show true
         :entry-summary entry-summary
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

(def entry-long-press-menu-action (menu-action-factory hide-entry-long-press-menu))

(declare move-entry-dialog-show-with-state)

(defn entry-long-press-menu [{:keys [show x y entry-summary]}]
  (let [deleted-cat @(elist-events/deleted-category-showing)
        {:keys [uuid parent-group-uuid]} entry-summary]
    (if-not deleted-cat
      [rnp-menu {:visible show :onDismiss hide-entry-long-press-menu :anchor (clj->js {:x x :y y})}
       ;; TODO: Need to add a rust api to toggle an entry as Favorites or not and then enable this 
       #_[rnp-menu-item {:title "Favorites" :onPress #()  :trailingIcon "check"}]
       [rnp-menu-item {:title (lstr-ml "move")
                       :disabled @(cmn-events/current-db-disable-edit)
                       :onPress (entry-long-press-menu-action move-entry-dialog-show-with-state uuid parent-group-uuid)}]

       [rnp-menu-item {:title (lstr-ml "delete")
                       :disabled @(cmn-events/current-db-disable-edit)
                       :onPress (entry-long-press-menu-action cc/show-entry-delete-confirm-dialog uuid)}]



       ;; TDOO: 
       ;; Need to add backend api support to get history count as part of summary
       ;; and use that to enable/disable History menu item
       #_[rnp-divider]
       #_[rnp-menu-item {:title "History"  :onPress #()}]]

      [rnp-menu {:visible show :onDismiss hide-entry-long-press-menu :anchor (clj->js {:x x :y y})}
       [rnp-menu-item {:title (lstr-ml "putback")
                       :disabled @(cmn-events/current-db-disable-edit)
                       :onPress (entry-long-press-menu-action
                                 md-events/open-putback-dialog (:uuid entry-summary))}]
       [rnp-menu-item {:title (lstr-ml "deletePermanently")
                       :disabled @(cmn-events/current-db-disable-edit)
                       :onPress (entry-long-press-menu-action
                                 md-events/openn-delete-permanent-dialog (:uuid entry-summary))}]])))

;;; 

(def ^:private group-long-press-menu-data (r/atom {:show false :x 0 :y 0}))

(defn hide-group-long-press-menu []
  (swap! group-long-press-menu-data assoc :show false))

(defn show-group-long-press-menu [^js/PEvent event category-detail]
  (swap! group-long-press-menu-data assoc
         :show true
         :category-detail category-detail
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

(def group-long-press-menu-action (menu-action-factory hide-group-long-press-menu))

(declare move-group-dialog-show-with-state)

(defn group-long-press-menu [{:keys [show x y category-detail]}]
  (let [group-uuid (:uuid category-detail)
        parent-group-uuid (:parent-group-uuid category-detail)]
    [rnp-menu {:visible show :onDismiss hide-group-long-press-menu :anchor (clj->js {:x x :y y})}
     [rnp-menu-item {:title (lstr-ml "edit")
                     :disabled @(cmn-events/current-db-disable-edit)
                     :onPress (group-long-press-menu-action elist-events/find-group-by-id group-uuid)}]
     [rnp-divider]
     [rnp-menu-item {:title (lstr-ml "addEntry")
                     :disabled @(cmn-events/current-db-disable-edit)
                     :onPress (group-long-press-menu-action
                               elist-events/add-entry-in-selected-group category-detail)}]
     [rnp-menu-item {:title (lstr-ml "addGroup")
                     :disabled @(cmn-events/current-db-disable-edit)
                     :onPress (group-long-press-menu-action
                               elist-events/add-group group-uuid)}]
     [rnp-divider]
     [rnp-menu-item {:title (lstr-ml "move")
                     :disabled @(cmn-events/current-db-disable-edit)
                     :onPress (group-long-press-menu-action move-group-dialog-show-with-state  group-uuid parent-group-uuid)}]

     [rnp-menu-item {:title (lstr-ml "delete")
                     :disabled @(cmn-events/current-db-disable-edit)
                     :onPress (group-long-press-menu-action
                               cc/show-group-delete-confirm-dialog group-uuid)}]]))

;;;; Sort menus
(def ^:private sort-menu-data (r/atom {:show false :sort-criteria {:key-name const/TITLE :direction const/ASCENDING} :x 0 :y 0}))

(defn- hide-sort-menu []
  (swap! sort-menu-data assoc :show false))

(def sort-menu-action (menu-action-factory hide-sort-menu))

(defn- show-sort-menu [^js/PEvent event sort-criteria]
  (swap! sort-menu-data assoc
         :show true
         :sort-criteria sort-criteria
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

(defn- sort-menus [{:keys [show x y]
                    {:keys [key-name direction]} :sort-criteria}]
  [rnp-menu {:visible show :onDismiss hide-sort-menu :anchor (clj->js {:x x :y y})}
   [rnp-menu-item {:title (lstr-ml 'title)
                   :leadingIcon (if (= key-name const/TITLE) ICON-CHECKBOX-OUTLINE ICON-CHECKBOX-BLANK-OUTLINE)
                   :onPress (sort-menu-action elist-events/entry-list-sort-key-changed const/TITLE)}]

   [rnp-menu-item {:title (lstr-ml 'modifiedTime)
                   :leadingIcon (if (= key-name const/MODIFIED_TIME) ICON-CHECKBOX-OUTLINE ICON-CHECKBOX-BLANK-OUTLINE)
                   :onPress (sort-menu-action elist-events/entry-list-sort-key-changed const/MODIFIED_TIME)}]

   [rnp-menu-item {:title (lstr-ml 'createdTime)
                   :leadingIcon (if (= key-name const/CREATED_TIME) ICON-CHECKBOX-OUTLINE ICON-CHECKBOX-BLANK-OUTLINE)
                   :onPress (sort-menu-action elist-events/entry-list-sort-key-changed const/CREATED_TIME)}]

   [rnp-divider]
   [rnp-menu-item {:title (lstr-ml 'ascending)
                   :leadingIcon (if (= direction const/ASCENDING) ICON-CHECKBOX-OUTLINE ICON-CHECKBOX-BLANK-OUTLINE)
                   :onPress (sort-menu-action elist-events/entry-list-sort-direction-changed const/ASCENDING)}]

   [rnp-menu-item {:title (lstr-ml 'descending)
                   :leadingIcon (if (= direction const/DESCENDING) ICON-CHECKBOX-OUTLINE ICON-CHECKBOX-BLANK-OUTLINE)
                   :onPress (sort-menu-action elist-events/entry-list-sort-direction-changed const/DESCENDING)}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn move-group-or-entry-dialog
  ([{:keys [dialog-show
            group-selection-info
            kind-kw
            error-fields
            uuid-selected-to-move
            current-parent-group-uuid]}]
   (when dialog-show
     (let [groups-listing @(md-events/groups-listing)
           ;; Need to exclude certain uuids from group listing
           groups-listing (filter (fn [group-info]
                                    ;; Note: In case of entry 'uuid-selected-to-move' is the entry-uuid and stricly not 
                                    ;; required to be excluded vec
                                    (not (u/contains-val? [uuid-selected-to-move current-parent-group-uuid] (:uuid group-info))))
                                  groups-listing)

           ;; Form the selection options 
           names (mapv (fn [m] {:key (:uuid m) :label (:name m)}) groups-listing)

           ;; Called when user selects a new group
           on-change (fn [option]
                       (let [group-info (first (filter (fn [m] (= (:name m) (.-label option))) groups-listing))]
                         ;; Note we need to use {:parent-group-selection-error nil} instead of setting {:error-fields {}}
                         ;; If we do not this, the deep-merge will not update :error-fields and the error remains
                         (dlg-events/move-group-or-entry-dialog-update-with-map
                          {:error-fields {:parent-group-selection-error nil}
                           :group-selection-info group-info})))
           ;; Current selection
           parent-group-name (:name group-selection-info)
           ;; Error if any
           parent-group-selection-error (:parent-group-selection-error error-fields)]

       [cust-dialog {:style {} :dismissable true :visible dialog-show :onDismiss #()}
        [rnp-dialog-title "Move"]
        [rnp-dialog-content
         [rn-view {:flexDirection "column"}
          [rnp-text {:style {:margin-bottom 10} :variant "titleMedium"} (lstr-dlg-text 'putBack)]
          (if-not (nil? parent-group-selection-error)
            [:<>
             [select-field {:text-label (str "Group" "*")
                            :options names
                            :value parent-group-name
                            :on-change on-change}]
             [rnp-helper-text {:type "error" :visible true} parent-group-selection-error]]
            [select-field {:text-label (str "Group" "*")
                           :options names
                           :value parent-group-name
                           :on-change on-change}])]]

        [rnp-dialog-actions
         [rnp-button {:mode "text" :onPress dlg-events/move-group-or-entry-dialog-close} (lstr-bl 'cancel)]
         [rnp-button {:mode "text" :onPress (fn []
                                              (md-events/move-entry-or-group kind-kw uuid-selected-to-move group-selection-info))} (lstr-bl 'ok)]]])))

  ([]
   (move-group-or-entry-dialog @(dlg-events/move-group-or-entry-dialog-data))))

#_(defn move-group-or-entry-dialog-show-with-state
    "Called to show the move dialog when menu item in group or entry panel is clicked"
    [kind-kw uuid-selected-to-move current-parent-group-uuid]
    (dlg-events/move-group-or-entry-dialog-show-with-state {:kind-kw kind-kw
                                                            :uuid-selected-to-move uuid-selected-to-move
                                                            :current-parent-group-uuid current-parent-group-uuid}))

(defn move-entry-dialog-show-with-state [uuid-selected-to-move current-parent-group-uuid]
  (dlg-events/move-group-or-entry-dialog-show-with-state {:kind-kw :entry
                                                          :uuid-selected-to-move uuid-selected-to-move
                                                          :current-parent-group-uuid current-parent-group-uuid}))

(defn- move-group-dialog-show-with-state [uuid-selected-to-move current-parent-group-uuid]
  (dlg-events/move-group-or-entry-dialog-show-with-state {:kind-kw :group
                                                          :uuid-selected-to-move uuid-selected-to-move
                                                          :current-parent-group-uuid current-parent-group-uuid}))

(defn- put-back-dialog [{:keys [dialog-show parent-group-name error-fields]}]
  (let [groups-listing (md-events/groups-listing)
        names (mapv (fn [m] {:key (:uuid m) :label (:name m)}) @groups-listing)
        on-change (fn [option]
                    (let [g (first (filter (fn [m] (= (:name m) (.-label option))) @groups-listing))]
                      (md-events/update-putback-dialog-parent-group g)))]
    [cust-dialog {:style {} :dismissable true :visible dialog-show :onDismiss #()}
     [rnp-dialog-title (lstr-dlg-title 'putBack)]
     [rnp-dialog-content
      [rn-view {:flexDirection "column"}
       [rnp-text {:style {:margin-bottom 10} :variant "titleMedium"} (lstr-dlg-text 'putBack)]
       (if-not (empty? error-fields)
         [:<>
          [select-field {:text-label (str (lstr-l 'groupOrCategory) "*")
                         :options names
                         :value parent-group-name
                         :on-change on-change}]
          [rnp-helper-text {:type "error" :visible true} (:parent-group-info error-fields)]]
         [select-field {:text-label (str (lstr-l 'groupOrCategory) "*")
                        :options names
                        :value parent-group-name
                        :on-change on-change}])]]
     [rnp-dialog-actions
      [rnp-button {:mode "text" :onPress #(md-events/hide-putback-dialog)} (lstr-bl 'cancel)]
      [rnp-button {:mode "text" :onPress #(md-events/on-put-back-dialog-ok)} (lstr-bl 'ok)]]]))

(defn row-item []
  (fn [{:keys [title secondary-title icon-id uuid] :as entry-summary}]
    (let [icon-name (icon-id->name icon-id)]
      [rnp-list-item {:onPress #(find-entry-by-id uuid)
                      :onLongPress (fn [e]
                                     (show-entry-long-press-menu e entry-summary))
                      :title (r/as-element
                              [rnp-text {:variant "titleMedium"} title])
                      :description secondary-title
                      :left (fn [_props] (r/as-element
                                          [rnp-list-icon {:icon icon-name
                                                          :color @icon-color
                                                          :style {:margin-left 5 :align-self "center"}}]))}])))

(defn- subgroup-row-item
  "category-detail-m is a map representing struct 'CategoryDetail'
  TODO: Need to rename section-title something category-key as we receive the section key 
  instead of section title as section title will be language dependent (in future)
  "
  [_category-detail-m category-key]
  ;; should the following need to accept section-title for react comp?
  (fn [{:keys [title display-title entries-count groups-count icon-id] :as category-detail-m}]
    (let [display-name (if (nil? display-title) title display-title)
          icon-name (icon-id->name icon-id)
          items-count (+ entries-count groups-count)]
      [rnp-list-item {:onPress #(ecat-events/load-selected-category-entry-items category-detail-m category-key)
                      :onLongPress (fn [e]
                                     (show-group-long-press-menu e category-detail-m))
                      :title (r/as-element
                              [rnp-text {:variant "titleMedium"} display-name])
                      :left (fn [_props] (r/as-element
                                          [rnp-list-icon {:style {:height 20} :icon icon-name :color @icon-color}]))
                      :right (fn [_props] (r/as-element
                                           [rnp-text {:variant "titleMedium"} items-count]))}])))

(defn- section-header [title]
  [rn-view  {:style {:flexDirection "row"
                     :backgroundColor @primary-container-color
                     :margin-top 5
                     :min-height 38}}
   [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleLarge"} (lstr-cv title)]])

(defn main-content []
  (let [entry-items @(elist-events/selected-entry-items)
        group-uuid (:uuid @(elist-events/selected-category-detail))
        group-items @(elist-events/subgroups-summary group-uuid)
        show-subgroups @(elist-events/show-subgroups)
        sections  (if-not  show-subgroups
                    [{:title "Entries"
                      :key "Entries"
                      :data entry-items}]
                    ;; Sections when "Groups" is selected to show the group tree
                    [{:title const/GROUP_SECTION_TITLE
                      :key const/GROUP_SECTION_TITLE
                      :data group-items}
                     {:title "Entries"
                      :key "Entries"
                      :data entry-items}])]

    [rn-section-list {:scrollEnabled false
                      :sections (clj->js sections)
                      :renderItem (fn [props]
                                    ;; keys in props are (:item :index :section :separators)
                                    (let [props (js->clj props :keywordize-keys true)]
                                      (r/as-element (if (= "Groups" (-> props :section :key))
                                                      [subgroup-row-item (-> props :item) const/GROUP_SECTION_TITLE]
                                                      [row-item (-> props :item)]))))
                      :ItemSeparatorComponent (fn [_p] (r/as-element [rnp-divider]))
                      :stickySectionHeadersEnabled false
                      :renderSectionHeader (fn [props] ;; key is :section
                                             (let [props (js->clj props :keywordize-keys true)
                                                   {:keys [title data]} (-> props :section)]
                                               (when (and show-subgroups (> (count data) 0))
                                                 (r/as-element [section-header title]))))}]))

(defn- permanent-delete-dialog []
  [confirm-dialog (merge @(md-events/delete-permanent-dialog-data)
                         {:title (lstr-dlg-title 'deleteEntryPermanent)
                          :confirm-text (lstr-dlg-text 'deleteEntryPermanent)
                          :actions [{:label (lstr-bl "yes")
                                     :on-press md-events/on-delete-permanent-dialog-ok}
                                    {:label (lstr-bl "no")
                                     :on-press md-events/hide-delete-permanent-dialog}]})])

(def delete-all-entries-permanent-confirm (r/atom false))

(defn show-delete-all-entries-permanent-confirm-dialog []
  (reset! delete-all-entries-permanent-confirm true))

(defn- delete-all-entries-permanent-confirm-dialog []
  [confirm-dialog {:dialog-show @delete-all-entries-permanent-confirm
                   :title  (lstr-dlg-title "deleteAllEntriesPermanet")
                   :confirm-text (lstr-dlg-text "deleteAllEntriesPermanet")
                   :actions [{:label (lstr-bl "yes")
                              :on-press (fn []
                                          (reset! delete-all-entries-permanent-confirm false)
                                          (elist-events/delete-all-entries-permanently))}
                             {:label (lstr-bl "no")
                              :on-press #(reset! delete-all-entries-permanent-confirm false)}]}])
;; Relevant for Android only
(defn adjust-inset-view []
  (fn []
    (when (is-Android)
      (let [bottom (rnc/get-inset-bottom)]
        ;; (println "bottom is..." bottom)
        [rn-view {:style {:height bottom}}]))))

(defn- bottom-nav-bar
  "A functional reagent componnent that returns the custom bottom bar"
  []
  (fn []
    (let [selected-category-key @(elist-events/selected-category-key)
          selected-category-detail @(elist-events/selected-category-detail)
          sort-criteria @(elist-events/entry-list-sort-criteria)]

      [rn-view {:style {:width "100%"
                        ;; Need to use the same background-color as the entry list content to make it opaque
                        :background-color @page-background-color
                        :padding-left 25
                        :padding-right 25
                        :borderTopWidth 1
                        :borderTopColor  @rnc/outline-variant
                        :min-height 50

                        ;;:position "absolute"

                        ;; In adndroid when we use absolute position, this bottom bar hides
                        ;; the entries list content - particularly when the list has more entries
                        ;; and even using the scroll does not work and it scrolls behind this component 

                        ;; Not using absolute position works for both android in iOS

                        ;; After Android 'compileSdkVersion = 35 introduction, adding insets hides the entries list content
                        ;; Also see comments in js/components/KeyboardAvoidingDialog.js

                        ;; Instead of setting bottom value from inset, we are using a dummy view 'adjust-inset-view'

                        :bottom 0}}

       [rn-view {:flexDirection "row" :justifyContent "space-between"}
        [rn-view {:align-items "center"}
         [rnp-icon-button {:size 24
                           :icon const/ICON-SORT
                           :iconColor @rnc/on-error-container-color
                           :onPress (fn [e]
                                      (show-sort-menu e sort-criteria))}]
         [rnp-text {:style {:margin-top -5}
                    :text-align "center"}
          (lstr-l 'sort)]]

        [rn-view {:align-items "center"}
         [rnp-icon-button {:size 24
                           :icon const/ICON-PLUS
                           :iconColor @rnc/on-error-container-color
                           :disabled @(cmn-events/current-db-disable-edit)
                           :onPress (fn [e]
                                      (show-fab-action-menu e selected-category-key selected-category-detail))}]
         [rnp-text {:style {:margin-top -5}
                    :text-align "center"}
          (lstr-l 'add)]]]])))

(defn entry-list-content []
  [rn-safe-area-view {:style {:flex 1 :background-color @page-background-color}}

   ;; When we use 'rn-scroll-view' and if main-content uses 'rn-section-list' we need to use ':scrollEnabled false' in rn-section-list
   ;; Otherwise we may see error like 
   ;; 'VirtualizedLists should never be nested inside plain ScrollViews with the same 
   ;;  orientation because it can break windowing and other functionality - use another VirtualizedList-backed container instead'

   [rnc/rn-scroll-view {:style {} :contentContainerStyle {:flexGrow 1 :background-color @page-background-color}}
    [main-content]]
   [:f> bottom-nav-bar]
   ;; Provides a dummy height component mainly to push the rn-scroll-view + bottom-nav-bar 
   ;; above the android built-in bottom bar when Android API level is below 32. It is nil comp for iOS 
   ;; and view with height of 0 in other Android API level 
   [adjust-inset-view]

   #_[bottom-nav-bar1]

   [sort-menus @sort-menu-data]
   [fab-action-menu @fab-action-menu-data]
   [entry-long-press-menu @entry-long-press-menu-data]
   [group-long-press-menu @group-long-press-menu-data]
   [put-back-dialog @(md-events/putback-dialog-data)]
   [move-group-or-entry-dialog]
   [permanent-delete-dialog]
   [delete-all-entries-permanent-confirm-dialog]
   [cc/entry-delete-confirm-dialog elist-events/delete-entry]
   [cc/group-delete-confirm-dialog elist-events/delete-group]])

;;;;;;;;;;; Few other ideas of using bottom bar ;;;;;;

#_(def idx (r/atom -1))

#_(defn- bottom-nav-bar1 []
    (let [routes [{:key "sort" :title "Sort" :focusedIcon "heart" :unfocusedIcon "heart-outline"}
                  {:key "settings" :title "Settings" :focusedIcon "bell" :unfocusedIcon "bell-outline"}]

          states {:index @idx
                  :routes routes}]

      [rnp-bottom-navigation-bar {:safeAreaInsets {:bottom 0}
                                  :navigationState (clj->js states :keywordize-keys true)
                                  :onTabPress (fn [props]
                                                (let [{:keys [route] :as p} (js->clj props :keywordize-keys true)
                                                      _ (println "route is " route)
                                                      {:keys [key preventDefault]} route]
                                                  (println "key is " key)
                                                  (println "p is " p)

                                                  (if (= key "sort")
                                                    (reset! idx 0)
                                                    (reset! idx 1)))
                                                #_(println props))}]))

#_(defn- bottom-nav-bar2 []
    (let [routes [{:key "sort" :title "Sort" :focusedIcon "heart" :unfocusedIcon "heart-outline"}
                  {:key "settings" :title "Settings" :focusedIcon "bell" :unfocusedIcon "bell-outline"}]

          states {:index @idx
                  :routes routes}]

      [rnp-bottom-navigation-bar {:safeAreaInsets {:bottom 0}
                                  :navigationState (clj->js states :keywordize-keys true)
                                  :onTabPress (fn [props]
                                                (let [{:keys [route] :as p} (js->clj props :keywordize-keys true)
                                                      _ (println "route is " route)
                                                      {:keys [key preventDefault]} route]
                                                  (println "key is " key)
                                                  (println "p is " p)

                                                  (if (= key "sort")
                                                    (reset! idx 0)
                                                    (reset! idx 1)))
                                                #_(println props))}]))
