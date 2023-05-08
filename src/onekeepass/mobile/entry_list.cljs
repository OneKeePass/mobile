(ns onekeepass.mobile.entry-list
  (:require
   [reagent.core :as r]
   [onekeepass.mobile.rn-components :as rnc :refer [lstr
                                                    icon-color
                                                    rnp-button
                                                    rnp-fab
                                                    rnp-menu
                                                    rnp-menu-item
                                                    rn-view 
                                                    rn-safe-area-view
                                                    rn-section-list 
                                                    rnp-list-item
                                                    rnp-divider
                                                    rnp-list-icon 
                                                    rnp-text
                                                    rnp-helper-text
                                                    cust-dialog
                                                    rnp-dialog-title
                                                    rnp-dialog-content
                                                    rnp-dialog-actions]]
   [onekeepass.mobile.utils :as u :refer [contains-val?]]
   [onekeepass.mobile.constants :as const]
   [onekeepass.mobile.common-components :as cc :refer [select-field 
                                                       confirm-dialog 
                                                       menu-action-factory]]
   [onekeepass.mobile.icons-list :refer [icon-id->name]]
   [onekeepass.mobile.events.move-delete :as md-events]
   [onekeepass.mobile.events.entry-category :as ecat-events]
   [onekeepass.mobile.events.entry-list :as elist-events :refer [find-entry-by-id]]))

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
   [rnp-menu-item {:title (lstr "menu.labels.addEntry")
                   :onPress (fab-menu-action elist-events/add-entry)}]
   (when (= const/GROUP_SECTION_TITLE selected-category-key)
     [rnp-menu-item {:title (lstr "menu.labels.addGroup")
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

(defn entry-long-press-menu [{:keys [show x y entry-summary]}]
  (let [deleted-cat @(elist-events/deleted-category-showing)]
    (if-not deleted-cat
      [rnp-menu {:visible show :onDismiss hide-entry-long-press-menu :anchor (clj->js {:x x :y y})}
       ;; TODO: Need to add a rust api to toggle an entry as Favorites or not and then enable this 
       #_[rnp-menu-item {:title "Favorites" :onPress #()  :trailingIcon "check"}]
       [rnp-menu-item {:title (lstr "menu.labels.delete")
                       :onPress (entry-long-press-menu-action cc/show-entry-delete-confirm-dialog (:uuid entry-summary))}]
       ;; TDOO: 
       ;; Need to add backend api support to get history count as part of summary
       ;; and use that to enable/disable History menu item
       #_[rnp-divider]
       #_[rnp-menu-item {:title "History"  :onPress #()}]]

      [rnp-menu {:visible show :onDismiss hide-entry-long-press-menu :anchor (clj->js {:x x :y y})}
       [rnp-menu-item {:title (lstr "menu.labels.putback")
                       :onPress (entry-long-press-menu-action 
                                 md-events/open-putback-dialog (:uuid entry-summary))}]
       [rnp-menu-item {:title (lstr "menu.labels.deletePermanently")
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

(defn group-long-press-menu [{:keys [show x y category-detail]}]
  (let [group-uuid (:uuid category-detail)]
    [rnp-menu {:visible show :onDismiss hide-group-long-press-menu :anchor (clj->js {:x x :y y})}
     [rnp-menu-item {:title (lstr "menu.labels.edit")  
                     :onPress (group-long-press-menu-action elist-events/find-group-by-id group-uuid)}]
     [rnp-divider]
     [rnp-menu-item {:title (lstr "menu.labels.addEntry") 
                     :onPress (group-long-press-menu-action 
                               elist-events/add-entry-in-selected-group category-detail)}]
     [rnp-menu-item {:title (lstr "menu.labels.addGroup") 
                     :onPress (group-long-press-menu-action 
                               elist-events/add-group group-uuid)}]
     [rnp-divider]
     [rnp-menu-item {:title (lstr "menu.labels.delete")  
                     :onPress (group-long-press-menu-action 
                               cc/show-group-delete-confirm-dialog group-uuid)}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn put-back-dialog [{:keys [dialog-show title parent-group-name error-fields]}]
  (let [groups-listing (md-events/groups-listing)
        names (mapv (fn [m] {:key (:uuid m) :label (:name m)}) @groups-listing)
        on-change (fn [option]
                    (let [g (first (filter (fn [m] (= (:name m) (.-label option))) @groups-listing))]
                      (md-events/update-putback-dialog-parent-group g)))]
    [cust-dialog {:style {} :dismissable true :visible dialog-show :onDismiss #()}
     [rnp-dialog-title title]
     [rnp-dialog-content
      [rn-view {:flexDirection "column"}
       [rnp-text {:style {:margin-bottom 10} :variant "titleMedium"} "Please select a parent group"]
       (if-not (empty? error-fields)
         [:<>
          [select-field {:text-label (str "Group/Category" "*")
                         :options names
                         :value parent-group-name
                         :on-change on-change}]
          [rnp-helper-text {:type "error" :visible true} (:parent-group-info error-fields)]]
         [select-field {:text-label (str "Group/Category" "*")
                        :options names
                        :value parent-group-name
                        :on-change on-change}])]]
     [rnp-dialog-actions
      [rnp-button {:mode "text" :onPress #(md-events/hide-putback-dialog)} "Cancel"]
      [rnp-button {:mode "text" :onPress #(md-events/on-put-back-dialog-ok)} "Ok"]]]))

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
                                                          :color icon-color
                                                          :style {:align-self "center"}}]))}])))

(defn subgroup-row-item
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
                                          [rnp-list-icon {:style {:height 20} :icon icon-name :color icon-color}]))
                      :right (fn [_props] (r/as-element 
                                           [rnp-text {:variant "titleMedium"} items-count]))}])))

(defn section-header [title]
  [rn-view  {:style {:flexDirection "row"
                     :backgroundColor rnc/primary-container-color
                     :margin-top 5
                     :min-height 38}}
   [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleLarge"} title]])

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

    [rn-section-list {:sections (clj->js sections)
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

(defn permanent-delete-dialog []
  [confirm-dialog (merge @(md-events/delete-permanent-dialog-data)
                         {:title "Delete permanently"
                          :confirm-text "Are you sure you want to delete this entry permanently?"
                          :actions [{:label (lstr "button.labels.yes") 
                                     :on-press md-events/on-delete-permanent-dialog-ok}
                                    {:label (lstr "button.labels.no") 
                                     :on-press md-events/hide-delete-permanent-dialog}]})])

(def delete-all-entries-permanent-confirm (r/atom false))

(defn show-delete-all-entries-permanent-confirm-dialog []
  (reset! delete-all-entries-permanent-confirm true))

(defn delete-all-entries-permanent-confirm-dialog []
  [confirm-dialog {:dialog-show @delete-all-entries-permanent-confirm
                   :title  (lstr "dialog.titles.deleteAllEntriesPermanet")
                   :confirm-text (lstr "dialog.texts.deleteAllEntriesPermanet")
                   :actions [{:label (lstr "button.labels.yes")
                              :on-press (fn []
                                          (reset! delete-all-entries-permanent-confirm false)
                                          (elist-events/delete-all-entries-permanently))}
                             {:label (lstr "button.labels.no")
                              :on-press #(reset! delete-all-entries-permanent-confirm false)}]}])

(defn entry-list-content []
  [rn-safe-area-view {:style {:flex 1}}
   [main-content]
   [fab-action-menu @fab-action-menu-data]
   [entry-long-press-menu @entry-long-press-menu-data]
   [group-long-press-menu @group-long-press-menu-data]
   [put-back-dialog @(md-events/putback-dialog-data)]
   [permanent-delete-dialog]
   [delete-all-entries-permanent-confirm-dialog]
   [cc/entry-delete-confirm-dialog elist-events/delete-entry]
   [cc/group-delete-confirm-dialog elist-events/delete-group] 
   (let [selected-category-key @(elist-events/selected-category-key)
         selected-category-detail @(elist-events/selected-category-detail)]
     (when (contains-val? [const/TYPE_SECTION_TITLE 
                           const/GROUP_SECTION_TITLE 
                           const/CAT_SECTION_TITLE] selected-category-key)
       [rnp-fab {:style {:position "absolute" :margin 16 :right 0 :bottom 0} :icon "plus"
                 :onPress (fn [e]
                            (show-fab-action-menu e selected-category-key selected-category-detail))}]))])