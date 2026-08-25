(ns onekeepass.mobile.entry-list
  (:require [onekeepass.mobile.background :refer [is-Android]]
            [onekeepass.mobile.bottom-navigator :as bn]
            [onekeepass.mobile.common-components :as cc :refer [confirm-dialog
                                                                menu-action-factory
                                                                select-field]]
            [onekeepass.mobile.constants :as const :refer [ICON-CHECKBOX-BLANK-OUTLINE
                                                           ICON-CHECKBOX-OUTLINE]]
            [onekeepass.mobile.events.clone-entry :as ce-events]
            [onekeepass.mobile.events.common :as cmn-events]
            [onekeepass.mobile.events.custom-icons :as ci-events]
            [onekeepass.mobile.events.dialogs :as dlg-events]
            [onekeepass.mobile.events.entry-category :as ecat-events]
            [onekeepass.mobile.events.entry-list :as elist-events :refer [find-entry-by-id]]
            [onekeepass.mobile.events.entry-list-otp :as otp-events]
            [onekeepass.mobile.events.move-delete :as md-events]
            [onekeepass.mobile.events.remote-storage :as rs-events]
            [onekeepass.mobile.events.search :as search-events]
            [onekeepass.mobile.grouped-list :as gl]
            [onekeepass.mobile.icons-list :refer [ENTRY-GROUP-LIST-ICON-SIZE
                                                  icon-id->name]]
            [onekeepass.mobile.otp-badge :as otp-badge]
            [onekeepass.mobile.rn-components :as rnc :refer [cust-dialog
                                                             icon-color
                                                             no-autocorrect-text-props
                                                             on-surface-variant
                                                             rn-image
                                                             rn-safe-area-view
                                                             rn-section-list
                                                             rn-view
                                                             rnp-button
                                                             rnp-dialog-actions
                                                             rnp-dialog-content
                                                             rnp-dialog-title
                                                             rnp-divider
                                                             rnp-helper-text
                                                             rnp-list-icon
                                                             rnp-list-item
                                                             rnp-menu
                                                             rnp-menu-item
                                                             rnp-text
                                                             rnp-text-input
                                                             row-highlight-style]]
            [onekeepass.mobile.translation :refer [lstr-bl lstr-cv
                                                   lstr-dlg-text
                                                   lstr-dlg-title lstr-l
                                                   lstr-ml]]
            [onekeepass.mobile.utils :as u]
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
  [rnp-menu {:visible show :key (str show) :onDismiss hide-fab-action-menu :anchor (clj->js {:x x :y y})}
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

(declare clone-entry-dialog-show-with-state)

(defn entry-long-press-menu [{:keys [show x y entry-summary]}]
  (let [deleted-cat @(elist-events/deleted-category-showing)
        {:keys [uuid title parent-group-uuid entry-type-uuid]} entry-summary]
    (if-not deleted-cat
      [rnp-menu {:visible show :key (str show) :onDismiss hide-entry-long-press-menu :anchor (clj->js {:x x :y y})}
       ;; TODO: Need to add a rust api to toggle an entry as Favorites or not and then enable this
       #_[rnp-menu-item {:title "Favorites" :onPress #()  :trailingIcon "check"}]
       [rnp-menu-item {:title (lstr-ml "clone")
                       :disabled @(cmn-events/current-db-disable-edit)
                       :onPress (entry-long-press-menu-action
                                 clone-entry-dialog-show-with-state uuid title parent-group-uuid)}]

       [rnp-menu-item {:title (lstr-ml "move")
                       :disabled @(cmn-events/current-db-disable-edit)
                       :onPress (entry-long-press-menu-action move-entry-dialog-show-with-state uuid parent-group-uuid)}]

       [rnp-menu-item {:title (lstr-ml "delete")
                       :disabled @(cmn-events/current-db-disable-edit)
                       :onPress (entry-long-press-menu-action cc/show-entry-delete-confirm-dialog uuid)}]

       ;; Launch the remote Storage Browser using this connection entry
       (when (cmn-events/remote-connection-entry-type? entry-type-uuid)
         [rnp-menu-item {:title (lstr-ml "openRemote")
                         :onPress (entry-long-press-menu-action
                                   rs-events/open-entry-remote entry-type-uuid uuid)}])



       ;; TDOO: 
       ;; Need to add backend api support to get history count as part of summary
       ;; and use that to enable/disable History menu item
       #_[rnp-divider]
       #_[rnp-menu-item {:title "History"  :onPress #()}]]

      [rnp-menu {:visible show :key (str show) :onDismiss hide-entry-long-press-menu :anchor (clj->js {:x x :y y})}
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
    [rnp-menu {:visible show :key (str show) :onDismiss hide-group-long-press-menu :anchor (clj->js {:x x :y y})}
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

(defn show-sort-menu
  ([event sort-criteria]
   (show-sort-menu event sort-criteria false))
  ([^js/PEvent event sort-criteria disable-time-sort?]
  (swap! sort-menu-data assoc
         :show true
         :sort-criteria sort-criteria
         :disable-time-sort? disable-time-sort?
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY))))

(defn- sort-menus [{:keys [show x y disable-time-sort?]
                    {:keys [key-name direction]} :sort-criteria}]
  [rnp-menu {:visible show :key (str show) :onDismiss hide-sort-menu :anchor (clj->js {:x x :y y})}
   [rnp-menu-item {:title (lstr-ml 'title)
                   :leadingIcon (if (= key-name const/TITLE) ICON-CHECKBOX-OUTLINE ICON-CHECKBOX-BLANK-OUTLINE)
                   :onPress (sort-menu-action elist-events/entry-list-sort-key-changed const/TITLE)}]

   [rnp-menu-item {:title (lstr-ml 'modifiedTime)
                   :disabled disable-time-sort?
                   :leadingIcon (if (= key-name const/MODIFIED_TIME) ICON-CHECKBOX-OUTLINE ICON-CHECKBOX-BLANK-OUTLINE)
                   :onPress (sort-menu-action elist-events/entry-list-sort-key-changed const/MODIFIED_TIME)}]

   [rnp-menu-item {:title (lstr-ml 'createdTime)
                   :disabled disable-time-sort?
                   :leadingIcon (if (= key-name const/CREATED_TIME) ICON-CHECKBOX-OUTLINE ICON-CHECKBOX-BLANK-OUTLINE)
                   :onPress (sort-menu-action elist-events/entry-list-sort-key-changed const/CREATED_TIME)}]

   [rnp-divider]
   [rnp-menu-item {:title (lstr-ml 'ascending)
                   :leadingIcon (if (= direction const/ASCENDING) ICON-CHECKBOX-OUTLINE ICON-CHECKBOX-BLANK-OUTLINE)
                   :onPress (sort-menu-action elist-events/entry-list-sort-direction-changed const/ASCENDING)}]

   [rnp-menu-item {:title (lstr-ml 'descending)
                   :leadingIcon (if (= direction const/DESCENDING) ICON-CHECKBOX-OUTLINE ICON-CHECKBOX-BLANK-OUTLINE)
                   :onPress (sort-menu-action elist-events/entry-list-sort-direction-changed const/DESCENDING)}]])

(defn sort-menu []
  [sort-menus @sort-menu-data])

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Clone entry ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn clone-entry-dialog-show-with-state
  "Called when the 'Clone' menu item of an entry is pressed. The cloned entry is added to
   the same group as the source entry and only a new title is asked from the user"
  [entry-uuid title parent-group-uuid]
  (dlg-events/clone-entry-dialog-show-with-state {:entry-uuid entry-uuid
                                                  :parent-group-uuid parent-group-uuid
                                                  :new-title (str title " Copy")}))

(defn clone-entry-dialog
  ([{:keys [dialog-show new-title entry-uuid parent-group-uuid error-fields]}]
   (when dialog-show
     (let [error-text (:new-title error-fields)]
       [cust-dialog {:style {} :dismissable true :visible dialog-show :onDismiss #()}
        [rnp-dialog-title (lstr-dlg-title 'cloneEntry)]
        [rnp-dialog-content
         [rn-view {:flexDirection "column"}
          [rnp-text-input (merge no-autocorrect-text-props
                                 {:label (lstr-l 'newTitle)
                                  :defaultValue new-title
                                  :onChangeText #(dlg-events/clone-entry-dialog-update [:new-title %])})]
          (when error-text
            [rnp-helper-text {:type "error" :visible true} error-text])]]

        [rnp-dialog-actions
         [rnp-button {:mode "text"
                      :onPress dlg-events/clone-entry-dialog-close} (lstr-bl 'cancel)]
         [rnp-button {:mode "text"
                      :onPress (fn []
                                 (ce-events/clone-entry-start
                                  entry-uuid new-title parent-group-uuid))} (lstr-bl 'ok)]]])))
  ([]
   (clone-entry-dialog @(dlg-events/clone-entry-dialog-data))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn- icon-left-element
  "Returns a reagent element for the left-icon slot — a custom icon image
   if `custom-icon-uuid` is set and the data URL has loaded, otherwise the
   standard MaterialCommunityIcons glyph for `icon-name`."
  [icon-name custom-icon-uuid]
  (when custom-icon-uuid (ci-events/ensure-icon-data-url custom-icon-uuid))
  (let [data-url (when custom-icon-uuid @(ci-events/icon-data-url custom-icon-uuid))]
    #_(println "icon-left-element data-url:" data-url)
    (if data-url
      [rn-view {:style {:margin-left 5 :align-self "center"
                        :width ENTRY-GROUP-LIST-ICON-SIZE
                        :height ENTRY-GROUP-LIST-ICON-SIZE}}
       [rn-image {:source (clj->js {:uri data-url})
                  :style {:width ENTRY-GROUP-LIST-ICON-SIZE
                          :height ENTRY-GROUP-LIST-ICON-SIZE}}]]
      [rnp-list-icon {:icon icon-name
                      :color @icon-color
                      :style {:margin-left 5
                              :align-self "center"
                              :width ENTRY-GROUP-LIST-ICON-SIZE
                              :height ENTRY-GROUP-LIST-ICON-SIZE}}])))

;; Keys of the sections the user has collapsed - presentation only state of this page
(def ^:private collapsed-sections (r/atom #{}))

(defn otp-right-element
  "The entry's current 2FA code, followed by the chevron the row already showed.

   An entry with no code renders no badge at all, so those rows are unchanged apart from
   the chevron keeping its place."
  [uuid]
  (let [token-data @(otp-events/otp-token-data uuid)]
    (otp-events/ensure-otp-token uuid token-data)
    [rn-view {:style {:flexDirection "row" :align-items "center"}}
     [otp-badge/otp-badge token-data {:code-color @on-surface-variant
                                      :bar-color @rnc/circular-progress-color
                                      :bar-track-color @rnc/outline-variant}]
     [gl/row-chevron]]))

(defn row-item []
  (fn [{:keys [title secondary-title icon-id custom-icon-uuid uuid] :as entry-summary}]
    (let [icon-name (icon-id->name icon-id)
          {menu-show :show menu-entry-summary :entry-summary} @entry-long-press-menu-data]
      [rnp-list-item {:style (row-highlight-style (and menu-show (= uuid (:uuid menu-entry-summary))))
                      :onPress (fn [] (find-entry-by-id uuid))
                      :onLongPress (fn [e]
                                     (show-entry-long-press-menu e entry-summary))
                      :title (r/as-element
                              [rnp-text {:variant "titleMedium"} title])
                      :description secondary-title
                      :descriptionStyle {:color @on-surface-variant}
                      :left (fn [_props]
                              (r/as-element
                               [icon-left-element icon-name custom-icon-uuid]))
                      :right (fn [_props] (r/as-element [otp-right-element uuid]))}])))

(defn- subgroup-row-item
  "category-detail-m is a map representing struct 'CategoryDetail'
  TODO: Need to rename section-title something category-key as we receive the section key
  instead of section title as section title will be language dependent (in future)
  "
  [_category-detail-m category-key]
  ;; should the following need to accept section-title for react comp?
  (fn [{:keys [title display-title entries-count groups-count icon-id custom-icon-uuid]
        :as category-detail-m}]
    (let [display-name (if (nil? display-title) title display-title)
          icon-name (icon-id->name icon-id)
          {menu-show :show menu-category-detail :category-detail} @group-long-press-menu-data]
      [rnp-list-item {:style (row-highlight-style (and menu-show (= category-detail-m menu-category-detail)))
                      :onPress (fn []
                                 (ecat-events/load-selected-category-entry-items category-detail-m category-key))
                      :onLongPress (fn [e]
                                     (show-group-long-press-menu e category-detail-m))
                      :title (r/as-element
                              [rnp-text {:variant "titleMedium"} display-name])
                      :description (gl/items-count-description entries-count groups-count)
                      :descriptionStyle {:color @on-surface-variant}
                      :left (fn [_props]
                              (r/as-element
                               [icon-left-element icon-name custom-icon-uuid]))
                      :right (fn [_props] (r/as-element [gl/row-chevron]))}])))

(defn- list-section-header [title items-count]
  [gl/section-header {:label (lstr-cv title)
                      :items-count items-count
                      :collapsed? (gl/collapsed? collapsed-sections title)
                      :on-toggle #(gl/toggle-collapsed collapsed-sections title)}])

(defn search-results-content
  "The entries the search term has matched anywhere in the database. Shown in place of its
   own content by every page that carries the search bar, so that a search reads the same
   whichever page it was started from.

   'scroll-enabled?' is false when the caller has already put this inside a scroll view"
  [scroll-enabled?]
  (let [items @(search-events/search-result-entry-items)
        not-matched @(search-events/search-not-matched)]
    (if (empty? items)
      ;; Saying 'no result' before the search of what has just been typed has even been made
      ;; would flash on the way to the matches, so nothing is shown until it is known
      (if not-matched [gl/no-match-view] [rn-view {:style {:flex 1}}])

      [rn-section-list {:style {:flex 1}
                        :contentContainerStyle (when scroll-enabled?
                                                 {:padding-bottom gl/FAB-LIST-BOTTOM-CLEARANCE})
                        :scrollEnabled scroll-enabled?
                        :sections (clj->js [{:key const/ENTRIES_SECTION_TITLE :data items}])
                        :stickySectionHeadersEnabled false
                        :renderItem (fn [props]
                                      (let [{:keys [item index section]} (js->clj props :keywordize-keys true)]
                                        (r/as-element
                                         [gl/card-row (gl/row-position index (count (:data section)))
                                          [row-item item]])))
                        :ItemSeparatorComponent (fn [_p] (r/as-element [gl/card-row-separator]))
                        ;; No header over the results, only the gap one would have left
                        :renderSectionHeader (fn [_props] (r/as-element [gl/section-spacer]))}])))

(defn main-content []
  (let [entry-items @(elist-events/selected-entry-items)
        group-uuid (:uuid @(elist-events/selected-category-detail))
        group-items @(elist-events/subgroups-summary group-uuid)
        show-subgroups @(elist-events/show-subgroups)

        ;; Subgroups are listed only when the group tree is what is being browsed
        group-items (if show-subgroups group-items [])

        ;; An empty section is left out altogether. A collapsed section still contributes
        ;; its header, only its rows are held back
        sections (cond-> []
                   (seq group-items)
                   (conj {:title const/GROUP_SECTION_TITLE
                          :key const/GROUP_SECTION_TITLE
                          :items-count (count group-items)
                          :data (gl/section-data collapsed-sections const/GROUP_SECTION_TITLE group-items)})

                   (seq entry-items)
                   (conj {:title const/ENTRIES_SECTION_TITLE
                          :key const/ENTRIES_SECTION_TITLE
                          :items-count (count entry-items)
                          :data (gl/section-data collapsed-sections const/ENTRIES_SECTION_TITLE entry-items)}))]

    (if (empty? sections)
      [gl/no-match-view]
      [rn-section-list {:scrollEnabled false
                        :sections (clj->js sections)
                        :renderItem (fn [props]
                                      ;; keys in props are (:item :index :section :separators)
                                      (let [{:keys [item index section]} (js->clj props :keywordize-keys true)]
                                        (r/as-element
                                         [gl/card-row (gl/row-position index (count (:data section)))
                                          (if (= const/GROUP_SECTION_TITLE (:key section))
                                            [subgroup-row-item item const/GROUP_SECTION_TITLE]
                                            [row-item item])])))
                        :ItemSeparatorComponent (fn [_p] (r/as-element [gl/card-row-separator]))
                        :stickySectionHeadersEnabled false
                        :renderSectionHeader (fn [props] ;; key is :section
                                               (let [props (js->clj props :keywordize-keys true)
                                                     {:keys [title items-count]} (-> props :section)]
                                                 (r/as-element [list-section-header title items-count])))}])))

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
(defn entry-row-menus-and-dialogs
  "The long press menu of an entry row together with the dialogs its actions open.

   Every page that lists entry rows has to mount this. Besides this page that is the entry
   category page, which lists the root group's own entries when the grouping is 'Groups'"
  []
  [:<>
   [entry-long-press-menu @entry-long-press-menu-data]
   [move-group-or-entry-dialog]
   [clone-entry-dialog]
   [permanent-delete-dialog]
   [cc/entry-delete-confirm-dialog elist-events/delete-entry]])

(defn- bottom-nav-bar []
  (fn []
    (let [sort-criteria @(elist-events/entry-list-sort-criteria)
          items [(bn/home-icon-action-item)
                 (bn/close-db-icon-action-item)
                 {:icon const/ICON-SORT :label (lstr-l 'sort) :action (fn [e]
                                                                        (show-sort-menu e sort-criteria))}
                 (bn/settings-icon-action-item)]]

      [bn/bottom-nav-bar-gen items])))


(defn- show-fab []
  (let [selected-category-key @(elist-events/selected-category-key)
        selected-category-detail @(elist-events/selected-category-detail)]
    [rnc/rnp-fab {:style {:position "absolute" :margin 16 :right 0 :bottom (+ (rnc/get-inset-bottom) 100)}
                  :disabled @(cmn-events/current-db-disable-edit)
                  :icon const/ICON-PLUS :onPress (fn [e] (show-fab-action-menu e selected-category-key selected-category-detail))}]))

(defn entry-list-content []
  (let [term @(search-events/search-term)]
    [rn-safe-area-view (cond-> {:style (gl/page-style)}
                         (is-Android) (assoc :edges #js ["right" "left"]))

     [gl/inline-search-bar {:term term
                            :on-change search-events/search-term-update}]

     ;; When we use 'rn-scroll-view' and if main-content uses 'rn-section-list' we need to use ':scrollEnabled false' in rn-section-list
     ;; Otherwise we may see error like
     ;; 'VirtualizedLists should never be nested inside plain ScrollViews with the same
     ;;  orientation because it can break windowing and other functionality - use another VirtualizedList-backed container instead'

     ;; The scroll view has to take the space the search bar above and the bottom bar below
     ;; leave over, so that both of them stay put whatever the list holds
     [rnc/rn-scroll-view {:style {:flex 1}
                          :contentContainerStyle {:flexGrow 1
                                                  :padding-bottom gl/FAB-LIST-BOTTOM-CLEARANCE
                                                  :background-color @rnc/grouped-list-ground-color}}
      (if (gl/searching? term)
        [search-results-content false]
        [main-content])]
     [:f> bottom-nav-bar]

     [show-fab]

     #_[bottom-nav-bar1]

     [sort-menus @sort-menu-data]
     [fab-action-menu @fab-action-menu-data]
     [entry-row-menus-and-dialogs]
     [group-long-press-menu @group-long-press-menu-data]
     [put-back-dialog @(md-events/putback-dialog-data)]
     [delete-all-entries-permanent-confirm-dialog]
     [cc/group-delete-confirm-dialog elist-events/delete-group]]))
