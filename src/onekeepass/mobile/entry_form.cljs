(ns
 onekeepass.mobile.entry-form
  (:require [reagent.core :as r]
            [onekeepass.mobile.rn-components
             :as rnc
             :refer [lstr
                     rn-keyboard
                     dots-icon-name
                     icon-color
                     on-primary-color
                     page-title-text-variant
                     rn-view
                     rn-scroll-view
                     rn-keyboard-avoiding-view
                     rnp-chip
                     rnp-checkbox
                     rnp-menu
                     rnp-menu-item
                     rnp-text-input
                     rnp-text
                     rnp-touchable-ripple
                     rnp-helper-text
                     rnp-text-input-icon
                     rnp-icon-button
                     rnp-button
                     rnp-portal
                     cust-dialog
                     rnp-dialog-title
                     rnp-dialog-content
                     rnp-dialog-actions
                     rnp-list-icon]]
            [onekeepass.mobile.common-components :as cc :refer [confirm-dialog
                                                                select-field
                                                                select-tags-dialog]]
            [onekeepass.mobile.icons-list :as icons-list]
            [onekeepass.mobile.utils :as u]
            [onekeepass.mobile.date-utils :refer [utc-str-to-local-datetime-str]]
            [clojure.string :as str]
            [onekeepass.mobile.events.entry-form :as form-events]
            [onekeepass.mobile.events.password-generator :as pg-events]
            [onekeepass.mobile.background :refer [is-iOS is-Android]]
            [onekeepass.mobile.events.common :as cmn-events]))

;;(set! *warn-on-infer* true)

(defn appbar-title
  "Entry form specific title to display"
  []
  (let [edit @(form-events/form-edit-mode)
        is-history-entry @(form-events/history-entry-form?)]
    (if edit
      [rn-view {:flexDirection "row"
                :style {:alignItems "center"
                        ;; :justify-content "space-between"  <- Using this hides the Edit or Save button below Action icon
                        ;; We need to use "center" whenever we have 'rnp-appbar-action' 
                        :justify-content "center"}}
       [rnp-button {:style {}
                    :textColor "white"
                    :mode "text"
                    :onPress form-events/cancel-entry-form}
        (lstr "button.labels.cancel")]
       [rnp-text {:style {:color "white"
                          :max-width 100
                          :margin-right 20 :margin-left 20}
                  :ellipsizeMode "tail"
                  :numberOfLines 1
                  :variant page-title-text-variant}
        (lstr "page.titles.entry")]
       [rnp-button {:style {}
                    :textColor "white"
                    :disabled (not @(form-events/form-modified))
                    :mode "text"
                    :onPress (fn []
                               (.dismiss rn-keyboard)
                               (form-events/entry-save))}
        (lstr "button.labels.save")]]

      [rn-view {:flexDirection "row"
                :style {:alignItems "center"
                        ;; See the above comment for using "center"
                        :justify-content "center"}}
       [rnp-button {:style {}
                    :textColor "white"
                    :mode "text"
                    :onPress (if is-history-entry
                               form-events/cancel-history-entry-form
                               form-events/cancel-entry-form)}
        (lstr "button.labels.close")]
       [rnp-text {:style {:color "white"
                          :max-width "75%"
                          :margin-right 20 :margin-left 20}
                  :ellipsizeMode "tail"
                  :numberOfLines 1
                  :variant page-title-text-variant}
        (if is-history-entry (lstr "page.titles.historyEntry")
            (lstr "page.titles.entry"))]
       [rnp-button {:style {}
                    :textColor "white"
                    :disabled (or @(form-events/deleted-category-showing)
                                  is-history-entry)
                    :mode "text" :onPress form-events/edit-mode-on-press}
        (lstr "button.labels.edit")]])))

;;;;;;; History ;;;;;;;;

(defn history-entry-delete-dialog []
  (let [entry-uuid @(form-events/entry-form-uuid)
        index @(form-events/history-entry-selected-index)]
    [confirm-dialog {:dialog-show @(form-events/history-entry-delete-flag)
                     :title "Delete history entry"
                     :confirm-text "Are you sure you want to delete this history entry permanently?"
                     :actions [{:label (lstr "button.labels.yes")
                                :on-press #(form-events/delete-history-entry-by-index
                                            entry-uuid index)}
                               {:label (lstr "button.labels.no")
                                :on-press form-events/close-history-entry-delete-confirm-dialog}]}]))

(defn history-entry-restore-dialog []
  [confirm-dialog {:dialog-show @(form-events/history-entry-restore-flag)
                   :title "Restore from history entry"
                   :confirm-text "Are you sure you want to restore the entry from this history entry?"
                   :actions [{:label (lstr "button.labels.yes")
                              :on-press form-events/restore-entry-from-history}
                             {:label (lstr "button.labels.no")
                              :on-press form-events/close-history-entry-restore-confirm-dialog}]}])

;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-modify-section-name-dialog [{:keys [dialog-show
                                              mode
                                              section-name
                                              error-fields] :as dialog-data}]
  (let [error (boolean (seq error-fields))]
    [cust-dialog {:style {} :dismissable true :visible dialog-show :onDismiss #()}
     [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1}
      (if (= mode :add) "New section name" "Modify section name")]
     [rnp-dialog-content
      [rn-view {:flexDirection "column"}
       [rnp-text-input {:label "Section name"
                        :defaultValue section-name
                        :onChangeText #(form-events/section-name-dialog-update :section-name %)}]
       (when error
         [rnp-helper-text {:type "error" :visible error}
          (get error-fields :section-name)])]]
     [rnp-dialog-actions]
     [rnp-dialog-actions
      [rnp-button {:mode "text"
                   :onPress #(form-events/section-name-dialog-update :dialog-show false)} (lstr "button.labels.cancel")]
      [rnp-button {:mode "text"
                   :onPress #(form-events/section-name-add-modify dialog-data)} (lstr "button.labels.ok")]]]))

(defn delete-field-confirm-dialog [{:keys [dialog-show]} actions]
  [cust-dialog {:style {} :dismissable true :visible dialog-show}
   [rnp-dialog-title {:ellipsizeMode "tail" :numberOfLines 1} "Delete field"]
   [rnp-dialog-content
    [rnp-text "Are you sure you want to delete this field permanently?"]]
   [rnp-dialog-actions
    (for [{:keys [label on-press]}  actions]
      ^{:key label} [rnp-button {:mode "text"
                                 :on-press on-press} label])]])

(defn add-modify-section-field-dialog [{:keys [dialog-show
                                               section-name
                                               field-name
                                               protected
                                               required
                                               _data-type
                                               mode
                                               error-fields]
                                        :as m}]

  (let [error (boolean (seq error-fields))
        ok-fn (fn [_e]
                (if (= mode :add)
                  (form-events/section-field-add
                   (select-keys m [:field-name :protected :required :section-name :data-type]))
                  (form-events/section-field-modify
                   (select-keys m [:field-name :current-field-name :data-type :protected :required :section-name]))))]

    [cust-dialog {:style {} :dismissable true :visible dialog-show
                  :onDismiss #(form-events/close-section-field-dialog)}
     [rnp-dialog-title {:ellipsizeMode "tail"
                        :numberOfLines 1} (if (= mode :add)
                                            (str "Add field in " section-name)
                                            (str "Modify field in " section-name))]
     [rnp-dialog-content
      [rn-view {:flexDirection "column"}

       [rnp-text-input {:label "Field name" :defaultValue field-name
                        :onChangeText #(form-events/section-field-dialog-update :field-name %)}]
       (when error
         [rnp-helper-text {:type "error" :visible error}
          (get error-fields field-name)])

       [rn-view {:flexDirection "row"}
        [rnp-touchable-ripple {:style {:width "45%"}
                               :onPress #(form-events/section-field-dialog-update :protected (not protected))}
         [rn-view {:flexDirection "row" :style {:alignItems "center" :justifyContent "space-between"}}
          [rnp-text "Protected"]
          [rn-view {:pointerEvents "none"}
           [rnp-checkbox {:status (if protected "checked" "unchecked")}]]]]
        [rn-view {:style {:width "10%"}}]
        [rnp-touchable-ripple {:style {:width "45%"}
                               :onPress #(form-events/section-field-dialog-update :required (not required))}
         [rn-view {:flexDirection "row" :style {:alignItems "center" :justifyContent "space-between"}}
          [rnp-text "Required"]
          [rn-view {:pointerEvents "none"}
           [rnp-checkbox {:status (if required "checked" "unchecked")}]]]]]]]

     [rnp-dialog-actions
      [rnp-button {:mode "text" :onPress #(form-events/close-section-field-dialog)} "Cancel"]
      [rnp-button {:mode "text" :onPress ok-fn} "Ok"]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Menus ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private section-menu-dialog-data (r/atom {:section-name nil
                                                 :is-standard-section false
                                                 :show false
                                                 :x 0 :y 0}))

(defn section-menu [{:keys [section-name is-standard-section show x y]}]
  ;; anchor coordinates can be obtained in the icon button's onPrees. This is called with 'PressEvent Object Type'
  ;; (-> event .-nativeEvent .-pageY) and (-> event .-nativeEvent .-pageX) and use that 
  ;; in achor's coordinate 
  [rnp-menu {:visible show :onDismiss #(swap! section-menu-dialog-data assoc :show false)
             :anchor (clj->js {:x x :y y})} ;;:contentStyle {:backgroundColor  (-> rnc/custom-theme .-colors .-onPrimary)}
   [rnp-menu-item {:title (lstr "menu.labels.changename") :disabled is-standard-section
                   :onPress (fn []
                              (form-events/open-section-name-modify-dialog section-name)
                              (swap! section-menu-dialog-data assoc :show false))}]
   [rnp-menu-item {:title (lstr "menu.labels.addCustomField")
                   :onPress (fn []
                              (swap! section-menu-dialog-data assoc :show false)
                              (form-events/open-section-field-dialog section-name))}]])

(def ^:private custom-field-menu-data (r/atom {:section-name nil
                                               :field-name nil
                                               :show false
                                               :x 0 :y 0}))

(defn custom-field-menu-show [^js/PEvent event section-name key protected required]
  (swap! custom-field-menu-data assoc
         :section-name section-name
         ;; need to use field-name instead of 'key' as key 
         ;; in the custom-field-menu-data for the menu popup work properly
         :field-name key
         :protected protected
         :required required
         :show true
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

(defn custom-field-menu-action-on-dismiss []
  (swap! custom-field-menu-data assoc :show false))

(defn custom-field-menu [{:keys [show x y section-name field-name protected required]}]
  [rnp-menu {:visible show
             :onDismiss custom-field-menu-action-on-dismiss
             :anchor (clj->js {:x x :y y})}
   [rnp-menu-item {:title (lstr "menu.labels.modifyCustomField")
                   :onPress (fn [_e]
                              (form-events/open-section-field-modify-dialog
                               {:key field-name
                                :protected protected
                                :required required
                                :section-name section-name})
                              (custom-field-menu-action-on-dismiss))}]
   [rnp-menu-item {:title (lstr "menu.labels.deleteField")
                   :onPress (fn [_e]
                              (form-events/field-delete section-name field-name)
                              (custom-field-menu-action-on-dismiss))}]])

(declare clear-notes)

(defn entry-type-selection []
  (let [entry-types (clj->js
                     (mapv (fn [{:keys [name uuid]}]
                             {:key uuid :label name}) @(cmn-events/all-entry-type-headers)))
        entry-type-name-selection (form-events/entry-form-field :entry-type-name-selection)]
    [select-field {:text-label (str "Entry type" "*")
                   :options entry-types
                   :value @entry-type-name-selection
                   ;;:init-value @entry-type-name-selection  
                   :on-change (fn [^js/SelOption option]
                                ;; option is the selected member from the entry-types list passed as :options
                                (clear-notes)
                                (form-events/on-entry-type-selection (.-key option)))}]))

(defn group-selection []
  (let [groups-listing (form-events/groups-listing)
        names (mapv (fn [m] {:key (:name m) :label (:name m)}) @groups-listing)
        group-selection-info (form-events/entry-form-field :group-selection-info)
        group-selected-name (:name @group-selection-info)
        edit @(form-events/form-edit-mode)
        error-fields @(form-events/entry-form-field :error-fields)
        error-text (:group-selection error-fields)
        on-change (fn [^js/SelOption option]
                    ;; option is the selected member from the names list passed as :options
                    (let [g (first (filter (fn [m] (= (:name m) (.-label option))) @groups-listing))]
                      (form-events/on-group-selection g)))]

    (if (and edit (not (nil? error-text)))
      [:<>
       [select-field {:text-label (str "Group/Category" "*")
                      :options names
                      :value group-selected-name
                      :on-change on-change}]
       [rnp-helper-text {:type "error" :visible true} error-text]]

      [select-field {:text-label (str "Group/Category" "*")
                     :options names
                     :value group-selected-name
                     :on-change on-change}])))

(declare title-with-icon)

(defn entry-type-selection-box []
  (let [is-new-entry @(form-events/new-entry-form?)]
    (when is-new-entry
      [rn-view {:style {:flexDirection "column" :padding-right 5 :padding-left 5 :margin-bottom 5 :borderWidth .20 :borderRadius 4}}
       [entry-type-selection]])))

(defn title-group-selection-box []
  (let [edit @(form-events/form-edit-mode)
        is-new-entry @(form-events/new-entry-form?)]
    (if edit
      [rn-view {:style {:flexDirection "column" :padding-right 5 :padding-left 5 :margin-bottom 5 :borderWidth .20 :borderRadius 4}}
       [title-with-icon]
       (when is-new-entry
         [:<>
          #_[entry-type-selection]
          [group-selection]])]
      [title-with-icon])))

(defn on-entry-icon-selection
  "A callback function that is called from :common/icon-selected event handler when the user selects a new icon"
  [_icon-name icon-id]
  (form-events/edit-mode-on-press)
  (form-events/entry-form-data-update-field-value :icon-id icon-id))

(defn android-title-text-input [title icon-name]
  [rnp-text-input {:style {:width "100%"}
                   :label (str "Title" "*")
                   :autoCapitalize "none"
                   :defaultValue title
                   :ref (fn [ref]
                               ;; Keys found in ref for textinput
                               ;; are #js ["focus" "clear" "setNativeProps" "isFocused" "blur" "forceFocus"]
                               ;; Need to call clear directly as the previous value is not getting cleared 
                               ;; when there is a change in entry type selection name
                          (when (and (not (nil? ref)) (str/blank? title)) (.clear ref)))
                   :onChangeText #(form-events/entry-form-data-update-field-value :title %)
                   :right (r/as-element
                           [rnp-text-input-icon {:iconColor icon-color
                                                 :icon icon-name
                                                 :onPress #(cmn-events/show-icons-to-select on-entry-icon-selection)}])}])

(defn ios-title-text-input [title icon-name]
  [rnp-text-input {:style {:width "100%"}
                   :label (str "Title" "*")
                   :autoCapitalize "none"
                   :value title
                   :onChangeText #(form-events/entry-form-data-update-field-value :title %)
                   :right (r/as-element
                           [rnp-text-input-icon {:iconColor icon-color
                                                 :icon icon-name
                                                 :onPress #(cmn-events/show-icons-to-select on-entry-icon-selection)}])}])

(defn title-with-icon []
  (let [{:keys [title icon-id]} @(form-events/entry-form-data-fields [:title :icon-id])
        icon-name (icons-list/icon-id->name icon-id)
        edit @(form-events/form-edit-mode)
        error-fields @(form-events/entry-form-field :error-fields)]
    (if edit
      [rn-view {:style {:margin-top 2 :margin-bottom 2}}
       (if (is-iOS)
         [ios-title-text-input title icon-name]
         [android-title-text-input title icon-name])
       (when (contains? error-fields :title)
         [rnp-helper-text {:type "error" :visible (contains? error-fields :title)}
          (:title error-fields)])]
      [rn-view {:style {:flexDirection "row" :justify-content "center" :alignItems "center"}}
       [rnp-list-icon {:style {} :icon icon-name :color icon-color}]
       [rn-view {:style {:width 10}}] ;; gap
       [rnp-text {:variant "titleLarge"} title]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private field-focused (r/atom {:key nil :focused false}))

(defn field-focus-action [key flag]
  (swap! field-focused assoc :field-name key :focused flag))

;; Android text input compinent issue - inputing slow and if any character changed in the middle of text 
;; the cursor moves to the left
;; Solution:
;;   Need to use defaultValue. Also we need to use ref clear fn so that any previous default value shown is cleared
;;   when there is a entry type selection change. 
;; If we do not call explicit call to clear using ref, then following is noticed 
;; If user enters some text and when entry type is changed, we expect all fields 
;; should be blank. But because of the defaultValue prop is used, the old texts entered keeps showing 
;; though in the backend db it is blank or nil
(defn android-form-text-input [{:keys [key
                                       value
                                       protected
                                       required
                                       visible
                                       edit
                                       on-change-text]} is-password-edit? custom-field-edit-focused?]

  [rnp-text-input {:label (if required (str key "*") key)
                   :defaultValue value
                       ;;:value value
                       ;;:editable edit
                   :showSoftInputOnFocus edit
                   :ref (fn [ref]
                          (when (and (not (nil? ref)) (str/blank? value)) (.clear ref)))
                   :autoCapitalize "none" 
                   :keyboardType "email-address"
                   :autoComplete "off"
                   :autoCorrect false
                   :style {:width (if (or is-password-edit? custom-field-edit-focused?)  "90%" "100%")}
                   :onFocus #(field-focus-action key true)
                   :onBlur #(field-focus-action key false)
                   :onChangeText (if edit on-change-text nil)
                   :onPressOut (if-not edit
                                 #(cmn-events/write-string-to-clipboard {:field-name key
                                                                         :protected protected
                                                                         :value value})
                                 nil)
                   :secureTextEntry (if (or (not protected) visible) false true)
                   ;; It looks like we can have only one icon
                   :right (when protected
                            (if visible
                              (r/as-element [rnp-text-input-icon {:icon "eye"
                                                                  :onPress #(form-events/entry-form-field-visibility-toggle key)}])
                              (r/as-element [rnp-text-input-icon {:icon "eye-off"
                                                                  :onPress #(form-events/entry-form-field-visibility-toggle key)}])))}])

;; In iOS, we do not see the same issue as seen with the use of text input in android 
(defn ios-form-text-input [{:keys [key
                                   value
                                   protected
                                   required
                                   visible
                                   edit
                                   on-change-text]} is-password-edit? custom-field-edit-focused?]
  ;;(println "Key is " key " and value " value)
  [rnp-text-input {:label (if required (str key "*") key)
                   :value value
                   :showSoftInputOnFocus edit
                   :autoCapitalize "none"
                   :keyboardType "email-address"
                   ;;:autoComplete "off"
                   :autoCorrect false
                   ;;:contextMenuHidden true
                   :selectTextOnFocus false
                   :spellCheck false ;;ios
                   :textContentType "none"
                   ;; Sometime in iOS when a text input has its secureTextEntry with true value
                   ;; Strong Password prompt comes up and hides the actual input box preventing any entry
                   ;; Particularly it happened with Simulator. For now, we can disable the Password AutoFill feature
                   ;; in the Simulator Phone's Settings or setting the textContentType as shown below also works
                   ;; On device, this behaviour is not seen
                   ;; :textContentType (if (or (not protected) visible) nil "newPassword")
                   :style {:width (if (or is-password-edit? custom-field-edit-focused?)  "90%" "100%")}
                   :onFocus #(field-focus-action key true)
                   :onBlur #(field-focus-action key false)
                   :onChangeText (if edit on-change-text nil)
                   :onPressOut (if-not edit
                                 #(cmn-events/write-string-to-clipboard {:field-name key
                                                                         :protected protected
                                                                         :value value})
                                 nil)
                   :secureTextEntry (if (or (not protected) visible) false true)
                   ;; It looks like we can have only one icon
                   :right (when protected
                            (if visible
                              (r/as-element [rnp-text-input-icon
                                             {:icon "eye"
                                              :onPress #(form-events/entry-form-field-visibility-toggle key)}])
                              (r/as-element [rnp-text-input-icon
                                             {:icon "eye-off"
                                              :onPress #(form-events/entry-form-field-visibility-toggle key)}])))}])

(defn text-field
  "Called to show form fields"
  [{:keys [key
           protected
           standard-field
           required
           edit
           on-change-text
           error-text
           section-name]
    :or {edit false
         protected false
         on-change-text #(println (str "No on change text handler yet registered for " key))
         required false}
    :as kvm}]
  (let [is-password-edit? (and edit (= key "Password"))
        custom-field-edit-focused? (if (is-iOS)
                                     (and
                                      edit
                                      (= (:field-name @field-focused) key)
                                      ;; Sometimes pressing on the custom field menu icon is not working
                                      ;; It starts working when some input action is done in the custom field
                                      ;; and now if press on the menu icon, it works. By removing focused check as below
                                      ;; the pressing on custom icon works. But first time when press Soft KB hides and then
                                      ;; again we need to press for menu popup
                                      ;;(:focused @field-focused)   <- See above comments

                                      (not standard-field))
                                     ;; In Android, we cannot use :focused as onBlur sets false and dot-icon is hidden
                                     (and
                                      edit
                                      (= (:field-name @field-focused) key)
                                      (not standard-field)))]
    [rn-view {:flexDirection "column"}
     [rn-view {:flexDirection "row" :style {:flex 1}}
      (if (is-iOS)
        [ios-form-text-input kvm is-password-edit? custom-field-edit-focused?]
        [android-form-text-input kvm is-password-edit? custom-field-edit-focused?])
      (when is-password-edit?
        [rn-view {:style {:backgroundColor "white"}}
         [rnp-icon-button {:style {}
                           :icon "cached"
                           ;; on-change-text is a single argument function
                           ;; This function is called when the generated password is selected in Generator page
                           :onPress #(pg-events/generate-password on-change-text)}]])

     ;; In Android, when we press this dot-icon, the custom field first loses focus and the keyborad dimiss takes place 
     ;; and then menu pops up only when we press second time the dot-icon
      (when custom-field-edit-focused?
        [rn-view {:style {:backgroundColor "white"}}
         [rnp-icon-button {:style {}
                           :icon dots-icon-name
                           :onPress #(custom-field-menu-show % section-name key protected required)}]])]

     (when (and edit (not (nil? error-text)))
       [rnp-helper-text {:type "error" :visible true} error-text])]))

;; Notes - Multiline text input
;; Android issue - inputing slow and if any character changed in the middle of text 
;; the cursor moves to the left
;; Need to use defaultValue and clear fn for entry type selection changes 
;; See other method of handling this in case of title and other text field 
;; iOS issue (After 0.71.3 upgrade)
;; The value props does not work with multiline and need to use defaultValue 
;; Need to call explcitly clear-notes in entry-type-selection on-change event
;; Still Soft KB hides the notes field and we need to manually scroll to the field.

(def notes-ref (atom nil))

(defn clear-notes []
  (when @notes-ref (.clear  @notes-ref)))

(defn notes [edit]
  (let [value @(form-events/entry-form-data-fields :notes)]
    (when (or edit (not (str/blank? value)))
      [rn-view {:style {:padding-right 5 :padding-left 5 :borderWidth .20 :borderRadius 4}}
       [rnp-text-input {:style {:width "100%"} :multiline true :label (lstr "notes")
                        :defaultValue value
                        :ref (fn [ref]
                               (reset! notes-ref ref)
                               (when (and (is-Android) (not (nil? ref)) (str/blank? value)) (.clear ref)))
                        :showSoftInputOnFocus edit
                        :onChangeText (when edit #(form-events/entry-form-data-update-field-value :notes %))}]])))

(defn section-header [section-name]
  (let [edit @(form-events/entry-form-field :edit)
        standard-sections @(form-events/entry-form-data-fields :standard-section-names)]
    [rn-view {:style {:flexDirection "row"
                      :backgroundColor  (-> rnc/custom-theme .-colors .-primaryContainer)
                      :margin-top 5
                      :min-height 35}}
     [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleMedium"} section-name]
     (when edit
       [rnp-icon-button {:icon dots-icon-name :style {:height 35
                                                      :margin-right 0
                                                      :backgroundColor on-primary-color}
                         :onPress (fn [^js/PEvent event]
                                    (swap! section-menu-dialog-data assoc
                                           :section-name section-name
                                           :is-standard-section (u/contains-val? standard-sections section-name)
                                           :show true
                                           :x (-> event .-nativeEvent .-pageX)
                                           :y (-> event .-nativeEvent .-pageY)))}])]))

#_(defn field-nam-k [key]
    key)

(defn section-content [edit section-name section-data]
  (let [errors @(form-events/entry-form-field :error-fields)]
    ;; Show a section in edit mode irrespective of its contents; 
    ;; In non edit mode a section is shown only 
    ;; if it has some fields with non blank value. It is assumed the 'required' 
    ;; fileds will have some valid values
    (when (or edit (boolean (seq (filter (fn [kv] (not (str/blank? (:value kv)))) section-data))))
      [rn-view {:style {:flexDirection "column"}}
       [section-header section-name]
       (doall
        (for [{:keys [key
                      value
                      select-field-options
                      required
                      password-score] :as kv} section-data]
          ;; All fields of this section is shown in edit mode. In case of non edit mode, 
          ;; all required fields and other fields with values are shown
          (when (or edit (or required (not (str/blank? value))))
            (cond
              (not (nil? select-field-options))
              ^{:key key} [select-field {:text-label (if required (str key "*") key)
                                         :options  (mapv (fn [v] {:key v :label v}) select-field-options)
                                         :value value
                                         :disabled (not edit)
                                         :on-change #(form-events/update-section-value-on-change
                                                      section-name key (.-label ^js/SelOption %))}]

              :else
              ^{:key key} [text-field (assoc kv
                                             :section-name section-name
                                             :edit edit
                                             :error-text (get errors key)
                                             :on-change-text #(form-events/update-section-value-on-change
                                                               section-name key %)
                                             :password-score password-score
                                             :visible @(form-events/visible? key))]))))])))

(defn all-sections-content []
  (let [{:keys [edit]
         {:keys [section-names section-fields]} :data} @(form-events/entry-form)]
    ;; section-names is a list of section names
    ;; section-fields is a list of map - one map for each field in that section
    [rn-view {:style {:flexDirection "column"
                      :padding-top 5
                      :padding-right 5
                      :padding-left 5
                      :padding-bottom 10
                      :margin-bottom 5 :borderWidth .20 :borderRadius 4}}
     (doall
      (for [section-name section-names]
        ^{:key section-name} [section-content edit section-name (get section-fields section-name)]))]))

(defn add-section-btn []
  [rn-view {:style {:padding-top 5 :padding-bottom 5}  :justify-content "center"}
   [rnp-button {:mode "contained" :onPress #(form-events/open-section-name-dialog)} "Additional section and custom fields"]])

(defn tags [edit]
  (let [entry-tags @(form-events/entry-form-data-fields :tags)
        tags-availble (boolean (seq entry-tags))]
    ;;(println "entry-tags empty-tags " entry-tags tags-availble edit)
    (when (or edit tags-availble)
      [rn-view {:style {:flexDirection "column" :justify-content "center"  :min-height 50  :margin-top 5 :padding 5 :borderWidth .20 :borderRadius 4}}
       [rn-view {:style {:flexDirection "row" :backgroundColor  (-> rnc/custom-theme .-colors .-primaryContainer) :min-height 25}}
        [rnp-text {:style {:alignSelf "center" :width "85%" :padding-left 15} :variant "titleMedium"} "Tags"]
        (when edit
          [rnp-icon-button {:icon "plus" :style {:height 35 :margin-right 0 :backgroundColor on-primary-color}
                            :onPress (fn [] (cmn-events/tags-dialog-init-selected-tags entry-tags))}])]
       [rn-view {:style {:flexDirection "column" :padding-top 10}}
        [rn-view {:style {:flexDirection "row" :flexWrap "wrap"}}
         (doall
          (for [tag  entry-tags]
            ^{:key tag} [rnp-chip {:style {:margin 5}
                                   :onClose (when edit
                                              (fn []
                                                (form-events/entry-form-data-update-field-value :tags (filterv #(not= tag %) entry-tags))))} tag]))]]])))

(defn entry-times []
  (let [{:keys [last-modification-time creation-time]} @(form-events/entry-form-data-fields
                                                         [:last-modification-time :creation-time])]
    [rn-view {:style {:margin-top 20}}
     [rn-view {:style {:justify-content "space-between"} :flexDirection "row"}
      [rnp-text "Creation Time"]
      [rnp-text (utc-str-to-local-datetime-str creation-time)]]
     [rn-view {:style {:height 10}}]
     [rn-view {:style {:justify-content "space-between"} :flexDirection "row"}
      [rnp-text "Last Modification Time"]
      [rnp-text (utc-str-to-local-datetime-str last-modification-time)]]]))

(defn main-content []
  (let [edit @(form-events/form-edit-mode)]
    [rn-view {:style {:flexDirection "column" :justify-content "center" :padding 5}}
     [entry-type-selection-box]
     [title-group-selection-box]
     [all-sections-content]
     (when edit [add-section-btn])
     [notes edit]
     ;; Tags
     [tags edit]

     (when-not edit [entry-times])

     ;; Attachments panel will come here  

     ;; Setup the menus. This ensures these menu components are called only once to initiate
     [section-menu @section-menu-dialog-data]
     [custom-field-menu @custom-field-menu-data]

     ;; All entry form related dialogs
     [rnp-portal
      [add-modify-section-name-dialog @(form-events/section-name-dialog-data)]
      [add-modify-section-field-dialog @(form-events/section-field-dialog-data)]
      [select-tags-dialog @(cmn-events/tags-dialog-data) #(form-events/entry-form-data-update-field-value :tags %)]
      [delete-field-confirm-dialog @(form-events/field-delete-dialog-data)
       [{:label (lstr "button.labels.yes")
         :on-press #(form-events/field-delete-confirm true)}
        {:label (lstr "button.labels.no")
         :on-press #(form-events/field-delete-confirm false)}]]
      [history-entry-delete-dialog]
      [history-entry-restore-dialog]
      [cc/entry-delete-confirm-dialog form-events/delete-entry]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  

(defn content []
  [rn-keyboard-avoiding-view {:style {:flex 1}
                              :behavior (if (is-iOS) "padding" nil)}
   [rn-scroll-view {:contentContainerStyle {:flexGrow 1}}
    [main-content]]])

(comment
  (in-ns 'onekeepass.mobile.entry-form))
